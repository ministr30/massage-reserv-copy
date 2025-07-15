package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.*
import com.massagepro.data.model.*
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.ui.home.TimeSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "com.massagepro.slot_prefs"
private const val KEY_HIDE_FREE_SLOTS = "hide_free_slots"

class AppointmentsViewModel(
    application: Application,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hideFreeSlots = MutableStateFlow(sharedPreferences.getBoolean(KEY_HIDE_FREE_SLOTS, false))
    val hideFreeSlots: StateFlow<Boolean> = _hideFreeSlots.asStateFlow()

    // Исправлено — корректное объявление allServices
    val allClients: LiveData<List<Client>> = clientRepository.getAllClients().asLiveData()
    val allServices: LiveData<List<Service>> = liveData {
        val services = serviceRepository.getAllServicesOnce()
        emit(services)
    }


    init {
        checkAndAutoUpdateAppointmentStatuses()
    }

    fun setHideFreeSlots(hide: Boolean) {
        _hideFreeSlots.value = hide
        sharedPreferences.edit { putBoolean(KEY_HIDE_FREE_SLOTS, hide) }
    }

    fun insertAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.insertAppointment(appointment)
    }

    fun updateAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.updateAppointment(appointment)
    }

    fun deleteAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentRepository.deleteAppointment(appointment)
    }

    suspend fun getAppointmentById(id: Long): Appointment? =
        appointmentRepository.getAppointmentById(id)

    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Long): List<Appointment> =
        appointmentRepository.getConflictingAppointments(newStart, newEnd, excludeAppointmentId)

    suspend fun getClientById(clientId: Long): Client? =
        clientRepository.getClientById(clientId)

    suspend fun getServiceById(serviceId: Long): Service? =
        serviceRepository.getServiceById(serviceId)

    fun getAppointmentsForDayFlow(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> =
        appointmentRepository.getAppointmentsForDayIncludingAllStatuses(startOfDayMillis, endOfDayMillis)

    fun updateAppointmentStatus(appointmentId: Long, newStatus: String) {
        viewModelScope.launch {
            getAppointmentById(appointmentId)?.let {
                updateAppointment(it.copy(status = newStatus))
            }
        }
    }

    private fun checkAndAutoUpdateAppointmentStatuses() {
        viewModelScope.launch {
            val planned = AppointmentStatus.PLANNED.statusValue
            val completed = AppointmentStatus.COMPLETED.statusValue
            appointmentRepository.getAppointmentsByStatus(planned).forEach { app ->
                val end = app.dateTime + TimeUnit.MINUTES.toMillis(app.serviceDuration.toLong())
                if (System.currentTimeMillis() >= end) {
                    updateAppointment(app.copy(status = completed))
                }
            }
        }
    }

    suspend fun generateTimeSlots(
        selectedDate: Calendar,
        appointmentsWithDetails: List<AppointmentWithClientAndService>
    ): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        val cal = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = cal.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 21)

        while (cal.before(endOfDay)) {
            val start = cal.clone() as Calendar
            cal.add(Calendar.MINUTE, 30)
            val end = cal.clone() as Calendar

            var booked: Appointment? = null
            var client: Client? = null
            var service: Service? = null
            var display = false
            var endTime: Calendar? = null

            val starting = appointmentsWithDetails.firstOrNull {
                val appStart = Calendar.getInstance().apply { timeInMillis = it.appointment.dateTime }
                appStart.timeInMillis >= start.timeInMillis && appStart.timeInMillis < end.timeInMillis
            }

            if (starting != null) {
                booked = starting.appointment
                client = getClientById(booked.clientId)
                service = getServiceById(booked.serviceId)
                display = true
                endTime = Calendar.getInstance().apply {
                    timeInMillis = booked.dateTime + TimeUnit.MINUTES.toMillis(booked.serviceDuration.toLong())
                }
            }

            val isBooked = booked != null || appointmentsWithDetails.any {
                val appStart = Calendar.getInstance().apply { timeInMillis = it.appointment.dateTime }
                val appEnd = Calendar.getInstance().apply { timeInMillis = it.appointment.dateTime + TimeUnit.MINUTES.toMillis(it.appointment.serviceDuration.toLong()) }
                appStart.timeInMillis < end.timeInMillis && appEnd.timeInMillis > start.timeInMillis
            }

            slots.add(
                TimeSlot(
                    start, end, isBooked, booked, client, service, display, endTime
                )
            )
        }
        return slots
    }

    suspend fun findNextAvailableSlot(serviceDuration: Int, searchFrom: Calendar): Calendar? {
        val all = appointmentRepository.getAllAppointmentsList().sortedBy { it.dateTime }
        val search = searchFrom.clone() as Calendar
        if (search.get(Calendar.HOUR_OF_DAY) < 9) {
            search.set(Calendar.HOUR_OF_DAY, 9)
            search.set(Calendar.MINUTE, 0)
        }

        for (i in 0..30) {
            val day = (search.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val start = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }
            val end = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0) }

            val dayApps = all.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.dateTime }
                cal.get(Calendar.YEAR) == day.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
            }

            var lastEnd = start.timeInMillis
            for (app in dayApps) {
                if (app.dateTime - lastEnd >= TimeUnit.MINUTES.toMillis(serviceDuration.toLong())) {
                    val slot = Calendar.getInstance().apply { timeInMillis = lastEnd }
                    if (!slot.before(search)) return slot
                }
                lastEnd = app.dateTime + TimeUnit.MINUTES.toMillis(app.serviceDuration.toLong())
            }
            if (end.timeInMillis - lastEnd >= TimeUnit.MINUTES.toMillis(serviceDuration.toLong())) {
                val slot = Calendar.getInstance().apply { timeInMillis = lastEnd }
                if (!slot.before(search)) return slot
            }
        }
        return null
    }

    suspend fun getPrecedingAppointment(startTime: Long): Appointment? {
        val all = appointmentRepository.getAllAppointmentsList()
        return all.filter { (it.dateTime + TimeUnit.MINUTES.toMillis(it.serviceDuration.toLong())) <= startTime }
            .maxByOrNull { it.dateTime }
    }
}
