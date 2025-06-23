package com.massagepro.ui.appointments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.dao.ServiceDao
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import kotlinx.coroutines.launch
import java.util.Date
import kotlinx.coroutines.flow.Flow // Добавлен импорт для Flow
import kotlinx.coroutines.flow.combine // Добавлен импорт для combine

class AppointmentViewModel(private val appointmentDao: AppointmentDao, private val clientDao: ClientDao, private val serviceDao: ServiceDao) : ViewModel() {

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

    suspend fun getConflictingAppointments(startTime: Date, endTime: Date, excludeAppointmentId: Int): List<Appointment> {
        return appointmentDao.getConflictingAppointments(startTime, endTime, excludeAppointmentId)
    }

    suspend fun getClientById(clientId: Int): Client? {
        return clientDao.getClientById(clientId)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceDao.getServiceById(serviceId)
    }

    // ЭТИ ФУНКЦИИ ПЕРЕМЕЩЕНЫ ВНУТРЬ КЛАССА
    fun getFilteredAppointments(startDate: Date, endDate: Date, clientId: Int?, status: String): Flow<List<Appointment>> {
        return appointmentDao.getAppointmentsForDateRange(startDate, endDate).combine(clientDao.getAllClients()) { appointments, clients ->
            appointments.filter { appointment ->
                val matchesClient = clientId == null || appointment.clientId == clientId
                val matchesStatus = status == "Все" || appointment.status == status
                matchesClient && matchesStatus
            }
        }
    }

    fun getAppointmentsForDateRange(startDate: Date, endDate: Date): Flow<List<Appointment>> {
        return appointmentDao.getAppointmentsForDateRange(startDate, endDate)
    }
}
