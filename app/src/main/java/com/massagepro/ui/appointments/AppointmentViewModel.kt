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
