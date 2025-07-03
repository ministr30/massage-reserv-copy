package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.massagepro.ui.home.TimeSlot
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

    // This returns LiveData for existing usages if any (e.g. original HomeFragment use)
    fun getAppointmentsForDayLiveData(startOfDayMillis: Long, endOfDayMillis: Long): LiveData<List<AppointmentWithClientAndService>> {
        return appointmentRepository.getAppointmentsForDay(startOfDayMillis, endOfDayMillis).asLiveData()
    }

    // This returns a Flow for the new combined logic
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

    // Moved from HomeFragment
    private suspend fun generateTimeSlots(selectedDate: Calendar, appointmentsWithDetails: List<AppointmentWithClientAndService>): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
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

        while (calendar.before(endOfDay)) {
            val slotStartTime = calendar.clone() as Calendar
            calendar.add(Calendar.MINUTE, 30) // 30-minute slots
            val slotEndTime = calendar.clone() as Calendar

            var isBooked = false
            var bookedAppointment: Appointment? = null
            var client: Client? = null
            var service: Service? = null

            for (appWithDetails in appointmentsWithDetails) {
                if (appWithDetails.overlaps(slotStartTime, slotEndTime)) {
                    isBooked = true
                    bookedAppointment = appWithDetails.appointment
                    client = getClientById(appWithDetails.appointment.clientId) // Use ViewModel's method
                    service = getServiceById(appWithDetails.appointment.serviceId) // Use ViewModel's method
                    break
                }
            }
            slots.add(
                TimeSlot(
                    slotStartTime,
                    slotEndTime,
                    isBooked,
                    bookedAppointment,
                    client,
                    service
                )
            )
        }
        return slots
    }


    fun getDisplayableTimeSlots(selectedDate: Calendar): Flow<List<TimeSlot>> {
        val startOfDayMillis = selectedDate.getStartOfDayMillis()
        val endOfDayMillis = selectedDate.getEndOfDayMillis()

        val appointmentsFlow = getAppointmentsForDayFlow(startOfDayMillis, endOfDayMillis)

        val generatedTimeSlotsFlow: Flow<List<TimeSlot>> = appointmentsFlow.map { appointments ->
            // Pass selectedDate to generateTimeSlots
            generateTimeSlots(selectedDate, appointments)
        }

        return generatedTimeSlotsFlow.combine(hideFreeSlots) { slots, hide ->
            if (hide) slots.filter { it.bookedAppointment != null } else slots
        }
    }

    // Extension functions moved from HomeFragment
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
        // Ensure serviceDuration is positive, otherwise add 0 minutes.
        val durationMinutes = if (appointment.serviceDuration > 0) appointment.serviceDuration else 0
        appEnd.add(Calendar.MINUTE, durationMinutes)
        return appStart.time.before(slotEndTime.time) && appEnd.time.after(slotStartTime.time)
    }
}
