package com.massagepro.ui.appointments

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
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
    suspend fun generateTimeSlots( // Сделал public для удобства тестирования, если нужно
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
            var appointmentEndTime: Calendar? = null // Добавлено для хранения времени завершения записи

            // Ищем запись, которая начинается ВНУТРИ текущего 30-минутного слота
            // Если найдено несколько, берем первую (или можно выбрать по какой-то логике, например, самую короткую)
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

                // Вычисляем и сохраняем время завершения для отображения
                appointmentEndTime = Calendar.getInstance().apply {
                    timeInMillis = bookedAppointment.dateTime + (bookedAppointment.serviceDuration * 60 * 1000)
                }
            }

            // Проверяем, перекрывается ли текущий слот какой-либо записью (даже если она началась раньше)
            // Это нужно для пометки слотов как занятых, но без дублирования деталей
            if (!isBooked) { // Проверяем только если слот еще не помечен как занятый по началу записи
                for (appWithDetails in appointmentsWithDetails) {
                    val appStart = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime }
                    val appEnd = Calendar.getInstance().apply { timeInMillis = appWithDetails.appointment.dateTime + (appWithDetails.appointment.serviceDuration * 60 * 1000) }

                    // Условие перекрытия:
                    // (начало записи < конец слота) И (конец записи > начало слота)
                    if (appStart.timeInMillis < slotEndTime.timeInMillis && appEnd.timeInMillis > slotStartTime.timeInMillis) {
                        isBooked = true
                        // shouldDisplay остается false, так как детали этой записи будут отображаться в слоте, где она начинается
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
                    appointmentEndTime // Передаем время завершения
                )
            )
        }
        // Возвращаем все слоты. Фильтрация по shouldDisplay должна происходить в HomeFragment
        // или там, где используется этот список, чтобы показать только слоты с деталями.
        // Или, если ты хочешь, чтобы generateTimeSlots возвращал ТОЛЬКО слоты с деталями,
        // то оставь .filter { it.shouldDisplay }
        return allSlots
    }
}
