package com.massagepro.data.repository

import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(private val appointmentDao: AppointmentDao) {

    fun getAppointmentsWithClientAndService(status: String): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsWithClientAndService(status)
    }

    // 👇 ФУНКЦИЯ getAppointmentsWithClientAndServiceIncludingAllStatuses() УДАЛЕНА ОТСЮДА 👇

    fun getAllAppointments(status: String): Flow<List<Appointment>> {
        return appointmentDao.getAllAppointments(status)
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

    suspend fun getAppointmentById(id: Int): Appointment? {
        return appointmentDao.getAppointmentById(id)
    }

    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment> {
        return appointmentDao.getConflictingAppointments(newStart, newEnd, excludeAppointmentId)
    }

    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long, status: String): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsForDay(startOfDayMillis, endOfDayMillis, status)
    }

    fun getAppointmentsForDayIncludingAllStatuses(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> {
        return appointmentDao.getAppointmentsForDayIncludingAllStatuses(startOfDayMillis, endOfDayMillis)
    }

    suspend fun getAppointmentsByStatus(status: String): List<Appointment> {
        return appointmentDao.getAppointmentsByStatus(status)
    }

    suspend fun getAllAppointmentsList(): List<Appointment> {
        return appointmentDao.getAllAppointmentsList()
    }
}