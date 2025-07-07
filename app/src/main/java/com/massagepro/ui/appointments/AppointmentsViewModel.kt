package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.ui.home.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import com.massagepro.data.model.AppointmentStatus

private const val PREFS_NAME = "com.massagepro.slot_prefs"
private const val KEY_HIDE_FREE_SLOTS = "hide_free_slots"

class AppointmentsViewModel(
    private val application: Application,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadHideFreeSlotsState(): Boolean {
        return sharedPreferences.getBoolean(KEY_HIDE_FREE_SLOTS, false)
    }

    private val _hideFreeSlots = MutableStateFlow(loadHideFreeSlotsState())
    val hideFreeSlots: StateFlow<Boolean> = _hideFreeSlots.asStateFlow()

    init {
        checkAndAutoUpdateAppointmentStatuses()
    }

    fun setHideFreeSlots(hide: Boolean) {
        _hideFreeSlots.value = hide
        sharedPreferences.edit().putBoolean(KEY_HIDE_FREE_SLOTS, hide).apply()
    }

    // ИСПРАВЛЕНО: Теперь использует метод, который НЕ фильтрует по статусу,
    // чтобы отображать ВСЕ записи в общем списке
    val allAppointments: LiveData<List<AppointmentWithClientAndService>> =
        appointmentRepository.getAppointmentsWithClientAndServiceIncludingAllStatuses().asLiveData()

    val allClients: LiveData<List<Client>> = clientRepository.getAllClients().asLiveData()
    val allServices: LiveData<List<Service>> = serviceRepository.getAllServices().asLiveData()

    fun insertAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.insertAppointment(appointment)
    }

    fun updateAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.updateAppointment(appointment)
    }

    fun deleteAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.deleteAppointment(appointment)
    }

    suspend fun getAppointmentById(id: Int): Appointment? {
        return appointmentRepository.getAppointmentById(id)
    }

    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment> {
        return appointmentRepository.getConflictingAppointments(newStart, newEnd, excludeAppointmentId)
    }

    suspend fun getClientById(clientId: Int): Client? {
        return clientRepository.getClientById(clientId)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceRepository.getServiceById(serviceId)
    }

    // ИСПРАВЛЕНО: Теперь использует метод, который НЕ фильтрует по статусу,
    // для отображения расписания на день (чтобы видеть все записи на день)
    fun getAppointmentsForDayLiveData(startOfDayMillis: Long, endOfDayMillis: Long): LiveData<List<AppointmentWithClientAndService>> {
        return appointmentRepository.getAppointmentsForDayIncludingAllStatuses(startOfDayMillis, endOfDayMillis).asLiveData()
    }

    // ИСПРАВЛЕНО: Теперь использует метод, который НЕ фильтрует по статусу
    fun getAppointmentsForDayFlow(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> {
        return appointmentRepository.getAppointmentsForDayIncludingAllStatuses(startOfDayMillis, endOfDayMillis)
    }

    fun updateAppointmentStatus(appointmentId: Int, newStatus: String) {
        viewModelScope.launch {
            val appointment = getAppointmentById(appointmentId)
            appointment?.let {
                val updatedAppointment = it.copy(status = newStatus)
                updateAppointment(updatedAppointment)
            }
        }
    }

    private fun checkAndAutoUpdateAppointmentStatuses() {
        viewModelScope.launch {
            // ИСПОЛЬЗУЕМ .statusValue для получения правильных строк статусов
            val scheduledStatus = AppointmentStatus.PLANNED.statusValue
            val completedStatus = AppointmentStatus.COMPLETED.statusValue

            // Здесь getAppointmentsByStatus(scheduledStatus) будет искать записи со статусом "Заплановано"
            val scheduledAppointments = appointmentRepository.getAppointmentsByStatus(scheduledStatus)

            val currentTimeMillis = System.currentTimeMillis()

            scheduledAppointments.forEach { appointment ->
                val appointmentEndTimeMillis = appointment.dateTime + (appointment.serviceDuration * 60 * 1000)

                if (currentTimeMillis >= appointmentEndTimeMillis) {
                    val updatedAppointment = appointment.copy(status = completedStatus)
                    appointmentRepository.updateAppointment(updatedAppointment)
                }
            }
        }
    }

    suspend fun generateTimeSlots(
        selectedDate: Calendar,
        appointmentsWithDetails: List<AppointmentWithClientAndService>
    ): List<TimeSlot> {
        val allSlots = mutableListOf<TimeSlot>()
        val calendar = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (calendar.before(endOfDay)) {
            val slotStartTime = calendar.clone() as Calendar
            calendar.add(Calendar.MINUTE, 30)
            val slotEndTime = calendar.clone() as Calendar

            var isBooked = false
            var bookedAppointment: Appointment? = null
            var client: Client? = null
            var service: Service? = null
            var shouldDisplay = false
            var appointmentEndTime: Calendar? = null

            val appointmentStartingInSlot = appointmentsWithDetails.firstOrNull { appWithDetails ->
                val appStart = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                appStart.timeInMillis >= slotStartTime.timeInMillis && appStart.timeInMillis < slotEndTime.timeInMillis
            }

            if (appointmentStartingInSlot != null) {
                isBooked = true
                bookedAppointment = appointmentStartingInSlot.appointment
                client = getClientById(appointmentStartingInSlot.appointment.clientId)
                service = getServiceById(appointmentStartingInSlot.appointment.serviceId)
                shouldDisplay = true

                appointmentEndTime = Calendar.getInstance().apply {
                    timeInMillis = bookedAppointment.dateTime + (bookedAppointment.serviceDuration * 60 * 1000)
                }
            }

            if (!isBooked) {
                for (appWithDetails in appointmentsWithDetails) {
                    val appStart = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                    val appEnd = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime + (appWithDetails.appointment.serviceDuration * 60 * 1000) }

                    if (appStart.timeInMillis < slotEndTime.timeInMillis && appEnd.timeInMillis > slotStartTime.timeInMillis) {
                        isBooked = true
                        break
                    }
                }
            }

            allSlots.add(
                TimeSlot(
                    slotStartTime,
                    slotEndTime,
                    isBooked,
                    bookedAppointment,
                    client,
                    service,
                    shouldDisplay,
                    appointmentEndTime
                )
            )
        }
        return allSlots
    }
}