package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment)

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Query("DELETE FROM appointments WHERE id = :appointmentId")
    suspend fun deleteAppointmentById(appointmentId: Int)

    suspend fun deleteAppointment(appointment: Appointment) {
        deleteAppointmentById(appointment.id)
    }

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Int): Appointment?

    // Существующий метод для получения записей с деталями клиента и услуги
    @Query("""
        SELECT
            appointments.id AS appt_id,
            appointments.clientId AS appt_clientId,
            appointments.serviceId AS appt_serviceId,
            appointments.serviceName AS appt_serviceName,
            appointments.serviceDuration AS appt_serviceDuration,
            appointments.servicePrice AS appt_servicePrice,
            appointments.dateTime AS appt_dateTime,
            appointments.notes AS appt_notes,
            appointments.status AS appt_status,
            clients.name AS clientName,
            clients.phone AS clientPhone,
            services.category AS serviceCategory,
            services.duration AS serviceDuration,
            services.basePrice AS serviceBasePrice
        FROM appointments
        INNER JOIN clients ON appointments.clientId = clients.id
        INNER JOIN services ON appointments.serviceId = services.id
        ORDER BY appt_dateTime ASC
    """)
    fun getAppointmentsWithClientAndService(): Flow<List<AppointmentWithClientAndService>>

    // НОВЫЙ МЕТОД: Получение всех базовых записей Appointment
    @Query("SELECT * FROM appointments ORDER BY dateTime ASC")
    fun getAllAppointments(): Flow<List<Appointment>> // Возвращаем Flow<List<Appointment>>

    @Query("""
        SELECT
            appointments.id AS appt_id,
            appointments.clientId AS appt_clientId,
            appointments.serviceId AS appt_serviceId,
            appointments.serviceName AS appt_serviceName,
            appointments.serviceDuration AS appt_serviceDuration,
            appointments.servicePrice AS appt_servicePrice,
            appointments.dateTime AS appt_dateTime,
            appointments.notes AS appt_notes,
            appointments.status AS appt_status,
            clients.name AS clientName,
            clients.phone AS clientPhone,
            services.category AS serviceCategory,
            services.duration AS serviceDuration,
            services.basePrice AS serviceBasePrice
        FROM appointments
        INNER JOIN clients ON appointments.clientId = clients.id
        INNER JOIN services ON appointments.serviceId = services.id
        WHERE appt_dateTime BETWEEN :startOfDayMillis AND :endOfDayMillis
        ORDER BY appt_dateTime ASC
    """)
    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>>

    @Query("""
        SELECT * FROM appointments
        WHERE (dateTime < :newEnd AND (dateTime + serviceDuration * 60 * 1000) > :newStart)
        AND id != :excludeAppointmentId
    """)
    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment>

    @Query("SELECT * FROM appointments WHERE status = :status")
    suspend fun getAppointmentsByStatus(status: String): List<Appointment>
}
