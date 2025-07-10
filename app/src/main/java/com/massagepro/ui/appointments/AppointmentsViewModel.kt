package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentStatus
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
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "com.massagepro.slot_prefs"
private const val KEY_HIDE_FREE_SLOTS = "hide_free_slots"

class AppointmentsViewModel(
    application: Application, // Убрали private val
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

    // Используем KTX-расширение для SharedPreferences
    fun setHideFreeSlots(hide: Boolean) {
        _hideFreeSlots.value = hide
        sharedPreferences.edit {
            putBoolean(KEY_HIDE_FREE_SLOTS, hide)
        }
    }

    // Свойство allClients и allServices остаются, так как они используются
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

    // Эта функция используется в HomeFragment
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
            val scheduledStatus = AppointmentStatus.PLANNED.statusValue
            val completedStatus = AppointmentStatus.COMPLETED.statusValue

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

    suspend fun findNextAvailableSlot(serviceDuration: Int, searchFrom: Calendar): Calendar? {
        val allAppointments = appointmentRepository.getAllAppointmentsList().sortedBy { it.dateTime }
        val searchStartTime = searchFrom.clone() as Calendar

        if (searchStartTime.get(Calendar.HOUR_OF_DAY) < 9) {
            searchStartTime.set(Calendar.HOUR_OF_DAY, 9)
            searchStartTime.set(Calendar.MINUTE, 0)
        }

        for (i in 0..30) {
            val dayToSearch = (searchStartTime.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val workDayStart = (dayToSearch.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }
            val workDayEnd = (dayToSearch.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0) }

            val appointmentsOnThisDay = allAppointments.filter {
                val appCal = Calendar.getInstance().apply { timeInMillis = it.dateTime }
                appCal.get(Calendar.YEAR) == dayToSearch.get(Calendar.YEAR) &&
                        appCal.get(Calendar.DAY_OF_YEAR) == dayToSearch.get(Calendar.DAY_OF_YEAR)
            }

            var lastAppointmentEndTime = workDayStart.timeInMillis

            for (appointment in appointmentsOnThisDay) {
                if (appointment.dateTime - lastAppointmentEndTime >= TimeUnit.MINUTES.toMillis(serviceDuration.toLong())) {
                    val potentialSlot = Calendar.getInstance().apply { timeInMillis = lastAppointmentEndTime }
                    if (!potentialSlot.before(searchFrom)) return potentialSlot
                }
                lastAppointmentEndTime = appointment.dateTime + TimeUnit.MINUTES.toMillis(appointment.serviceDuration.toLong())
            }

            if (workDayEnd.timeInMillis - lastAppointmentEndTime >= TimeUnit.MINUTES.toMillis(serviceDuration.toLong())) {
                val potentialSlot = Calendar.getInstance().apply { timeInMillis = lastAppointmentEndTime }
                if (!potentialSlot.before(searchFrom)) return potentialSlot
            }
        }
        return null
    }

    suspend fun getPrecedingAppointment(startTime: Long): Appointment? {
        val allAppointments = appointmentRepository.getAllAppointmentsList()
        return allAppointments
            .filter { (it.dateTime + TimeUnit.MINUTES.toMillis(it.serviceDuration.toLong())) <= startTime }
            .maxByOrNull { it.dateTime }
    }
}