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

    // getAllAppointments() в AppointmentDao возвращает Flow. Его нужно преобразовать в LiveData.
    val allAppointments: LiveData<List<Appointment>> = appointmentDao.getAllAppointments().asLiveData()
    val allClients: LiveData<List<Client>> = clientDao.getAllClients().asLiveData()
    val allServices: LiveData<List<Service>> = serviceDao.getAllServices().asLiveData()

    // Методы insert/update/delete в AppointmentDao являются suspend функциями,
    // поэтому их нужно вызывать из корутины, например, в viewModelScope.launch.
    fun insertAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.insertAppointment(appointment)
    }

    fun updateAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.updateAppointment(appointment)
    }

    fun deleteAppointment(appointment: Appointment) = viewModelScope.launch {
        appointmentDao.deleteAppointment(appointment)
    }

    // getAppointmentById в AppointmentDao является suspend функцией и возвращает Appointment?.
    // ViewModel может напрямую вызывать его, т.к. сам метод ViewModel также suspend.
    suspend fun getAppointmentById(appointmentId: Int): Appointment? {
        return appointmentDao.getAppointmentById(appointmentId)
    }

    // getConflictingAppointments в AppointmentDao является suspend функцией.
    // ViewModel также должен быть suspend и передавать миллисекунды.
    suspend fun getConflictingAppointments(startTime: Date, endTime: Date, excludeAppointmentId: Int): List<Appointment> {
        return appointmentDao.getConflictingAppointments(startTime.time, endTime.time, excludeAppointmentId)
    }

    // getClientById и getServiceById в DAO являются suspend функциями.
    suspend fun getClientById(clientId: Int): Client? {
        return clientDao.getClientById(clientId)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceDao.getServiceById(serviceId)
    }

    // Этот метод уже был корректен, т.к. AppointmentDao.getAppointmentsForDateRange возвращает Flow.
    fun getAppointmentsForDateRange(startMillis: Long, endMillis: Long): Flow<List<Appointment>> {
        return appointmentDao.getAppointmentsForDateRange(startMillis, endMillis)
    }
}