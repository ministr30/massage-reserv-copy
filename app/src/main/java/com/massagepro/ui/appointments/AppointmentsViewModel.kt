package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.App // Импорт App для доступа к строковым ресурсам
import com.massagepro.R // Импорт R для доступа к строковым ресурсам
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.ui.home.TimeSlot // Убедитесь, что импорт TimeSlot корректен
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

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
        // Запускаем проверку статусов при инициализации ViewModel
        checkAndAutoUpdateAppointmentStatuses()
    }

    fun setHideFreeSlots(hide: Boolean) {
        _hideFreeSlots.value = hide
        sharedPreferences.edit().putBoolean(KEY_HIDE_FREE_SLOTS, hide).apply()
    }

    val allAppointments: LiveData<List<AppointmentWithClientAndService>> =
        appointmentRepository.getAppointmentsWithClientAndService().asLiveData()

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

    fun getAppointmentsForDayLiveData(startOfDayMillis: Long, endOfDayMillis: Long): LiveData<List<AppointmentWithClientAndService>> {
        return appointmentRepository.getAppointmentsForDay(startOfDayMillis, endOfDayMillis).asLiveData()
    }

    fun getAppointmentsForDayFlow(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> {
        return appointmentRepository.getAppointmentsForDay(startOfDayMillis, endOfDayMillis)
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
            val scheduledStatus = application.getString(R.string.appointment_status_scheduled)
            val completedStatus = application.getString(R.string.appointment_status_completed)

            val scheduledAppointments = appointmentRepository.getAppointmentsByStatus(scheduledStatus)

            val currentTimeMillis = System.currentTimeMillis()

            scheduledAppointments.forEach { appointment ->
                val appointmentEndTimeMillis = appointment.dateTime + (appointment.serviceDuration * 60 * 1000) // Время окончания записи

                if (currentTimeMillis >= appointmentEndTimeMillis) {
                    val updatedAppointment = appointment.copy(status = completedStatus)
                    appointmentRepository.updateAppointment(updatedAppointment)
                }
            }
        }
    }

    // Метод generateTimeSlots теперь suspend
    private suspend fun generateTimeSlots(selectedDate: Calendar, appointmentsWithDetails: List<AppointmentWithClientAndService>): List<TimeSlot> {
        val allSlots = mutableListOf<TimeSlot>()
        val calendar = Calendar.getInstance().apply {
            time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 9); set(
            Calendar.MINUTE,
            0
        ); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val endOfDay = Calendar.getInstance().apply {
            time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 21); set(
            Calendar.MINUTE,
            0
        ); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val processedAppointmentIds = mutableSetOf<Int>()

        while (calendar.before(endOfDay)) {
            val slotStartTime = calendar.clone() as Calendar
            calendar.add(Calendar.MINUTE, 30) // 30-minute slots
            val slotEndTime = calendar.clone() as Calendar

            var isBooked = false
            var bookedAppointment: Appointment? = null
            var client: Client? = null
            var service: Service? = null
            var shouldDisplay = true

            var appointmentStartingHere: AppointmentWithClientAndService? = null
            for (appWithDetails in appointmentsWithDetails) {
                val appStart = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                if (appStart.timeInMillis == slotStartTime.timeInMillis) {
                    appointmentStartingHere = appWithDetails
                    break
                }
            }

            if (appointmentStartingHere != null) {
                isBooked = true
                bookedAppointment = appointmentStartingHere.appointment
                client = getClientById(appointmentStartingHere.appointment.clientId)
                service = getServiceById(appointmentStartingHere.appointment.serviceId)
                shouldDisplay = true
                processedAppointmentIds.add(bookedAppointment.id)
            } else {
                for (appWithDetails in appointmentsWithDetails) {
                    val appStart = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                    val appEnd = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                    appEnd.add(Calendar.MINUTE, appWithDetails.appointment.serviceDuration)

                    if (appWithDetails.overlaps(slotStartTime, slotEndTime) && !processedAppointmentIds.contains(appWithDetails.appointment.id)) {
                        isBooked = true
                        bookedAppointment = appWithDetails.appointment
                        client = getClientById(appWithDetails.appointment.clientId)
                        service = getServiceById(appWithDetails.appointment.serviceId)
                        shouldDisplay = false
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
                    shouldDisplay
                )
            )
        }
        return allSlots.filter { it.shouldDisplay }
    }


    fun getDisplayableTimeSlots(selectedDate: Calendar): Flow<List<TimeSlot>> {
        val startOfDayMillis = selectedDate.getStartOfDayMillis()
        val endOfDayMillis = selectedDate.getEndOfDayMillis()

        val appointmentsFlow = getAppointmentsForDayFlow(startOfDayMillis, endOfDayMillis)

        val generatedTimeSlotsFlow: Flow<List<TimeSlot>> = appointmentsFlow.map { appointments ->
            // Вызываем suspend функцию generateTimeSlots внутри map, который сам по себе suspend
            generateTimeSlots(selectedDate, appointments)
        }

        return generatedTimeSlotsFlow.combine(hideFreeSlots) { slots, hide ->
            if (hide) slots.filter { it.bookedAppointment != null } else slots
        }
    }

    private fun Calendar.getStartOfDayMillis(): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun Calendar.getEndOfDayMillis(): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun AppointmentWithClientAndService.overlaps(
        slotStartTime: Calendar,
        slotEndTime: Calendar
    ): Boolean {
        val appStart = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
        val appEnd = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
        val durationMinutes = if (appointment.serviceDuration > 0) appointment.serviceDuration else 0
        appEnd.add(Calendar.MINUTE, durationMinutes)
        return appStart.time.before(slotEndTime.time) && appEnd.time.after(slotStartTime.time)
    }
}
