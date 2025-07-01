package com.massagepro.data.repository

import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import kotlinx.coroutines.flow.Flow
// import java.util.Date // Больше не нужен, если dateTime в Long

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val serviceRepository: ServiceRepository
) {
    // ИЗМЕНЕНО: Сигнатура метода изменена для возврата Flow<List<AppointmentWithClientAndService>>
    fun getAllAppointments(): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsWithClientAndService()
    }
    suspend fun getAppointmentById(id: Int): Appointment? {
        return appointmentDao.getAppointmentById(id)
    }

    suspend fun insertAppointment(appointment: Appointment) {
        appointmentDao.insertAppointment(appointment)
    }

    suspend fun updateAppointment(appointment: Appointment) {
        appointmentDao.updateAppointment(appointment)
    }

    suspend fun deleteAppointment(appointment: Appointment) {
        appointmentDao.deleteAppointment(appointment)
    }

    fun getAppointmentsWithClientAndService(): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsWithClientAndService()
    }

    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsForDay(startOfDayMillis, endOfDayMillis)
    }

    suspend fun getServiceById(serviceId: Int) = serviceRepository.getServiceById(serviceId)

    // ИСПРАВЛЕНО: Обновлена сигнатура для getConflictingAppointments
    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment> {
        return appointmentDao.getConflictingAppointments(newStart, newEnd, excludeAppointmentId)
    }
}
