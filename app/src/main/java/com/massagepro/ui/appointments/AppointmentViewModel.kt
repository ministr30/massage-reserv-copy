package com.massagepro.ui.appointments

import androidx.lifecycle.*
import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.dao.ServiceDao
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*

class AppointmentViewModel(
    private val appointmentDao: AppointmentDao,
    private val clientDao: ClientDao,
    private val serviceDao: ServiceDao
) : ViewModel() {

    // Эти свойства остаются без изменений
    val allAppointments: LiveData<List<Appointment>> = appointmentDao.getAllAppointments().asLiveData()
    val allClients: LiveData<List<Client>> = clientDao.getAllClients().asLiveData()
    val allServices: LiveData<List<Service>> = serviceDao.getAllServices().asLiveData()

    fun insertAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.insertAppointment(appointment)
    }

    fun updateAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.updateAppointment(appointment)
    }

    fun deleteAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.deleteAppointment(appointment)
    }

    suspend fun getAppointmentById(appointmentId: Int): Appointment? {
        return appointmentDao.getAppointmentById(appointmentId)
    }

    /**
     * ИСПРАВЛЕНО: Метод теперь корректно преобразует Date в Long перед запросом к DAO.
     * Это предотвратит ошибку несоответствия типов.
     */
    suspend fun getConflictingAppointments(startTime: Date, endTime: Date, excludeAppointmentId: Int): List<Appointment> {
        return appointmentDao.getConflictingAppointments(startTime.time, endTime.time, excludeAppointmentId)
    }

    suspend fun getClientById(clientId: Int): Client? {
        return clientDao.getClientById(clientId)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceDao.getServiceById(serviceId)
    }

    /**
     * Этот метод уже был корректен в вашем коде. Он остается без изменений.
     * Он использует вспомогательные функции для нормализации дат и правильно передает Long в DAO.
     */
    fun getFilteredAppointments(startDate: Date, endDate: Date, clientId: Int?, status: String): Flow<List<Appointment>> {
        val safeStart = normalizeStartOfDay(startDate).time
        val safeEnd = normalizeEndOfDay(endDate).time

        println("🔍 Фильтр по дате: с $safeStart по $safeEnd")

        return appointmentDao.getAppointmentsForDateRange(safeStart, safeEnd).map { appointments ->
            println("📋 Получено записей из БД: ${appointments.size}")
            appointments.filter { appointment ->
                val matchesClient = clientId == null || appointment.clientId == clientId
                val matchesStatus = status == "Все" || appointment.status == status
                matchesClient && matchesStatus
            }.also {
                println("✅ После фильтров (клиент + статус): ${it.size}")
            }
        }
    }

    private fun normalizeStartOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun normalizeEndOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }
}

/**
 * Фабрика необходима для создания экземпляра ViewModel с передачей DAO в конструктор.
 */
class AppointmentViewModelFactory(
    private val appointmentDao: AppointmentDao,
    private val clientDao: ClientDao,
    private val serviceDao: ServiceDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppointmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppointmentViewModel(appointmentDao, clientDao, serviceDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
