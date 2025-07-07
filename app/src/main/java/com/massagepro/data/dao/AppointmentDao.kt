package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import kotlinx.coroutines.flow.Flow
import com.massagepro.data.model.AppointmentStatus

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

    // МЕТОД ДЛЯ СТАТИСТИКИ (ФИЛЬТРУЕТ ПО СТАТУСУ)
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
        WHERE appointments.status = :status
        ORDER BY appt_dateTime ASC
    """)
    fun getAppointmentsWithClientAndService(status: String): Flow<List<AppointmentWithClientAndService>>

    // НОВЫЙ МЕТОД: Для отображения ВСЕХ записей (без фильтрации по статусу)
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
    fun getAppointmentsWithClientAndServiceIncludingAllStatuses(): Flow<List<AppointmentWithClientAndService>>


    // МЕТОД ДЛЯ СТАТИСТИКИ (ФИЛЬТРУЕТ ПО СТАТУСУ)
    @Query("SELECT * FROM appointments WHERE status = :status ORDER BY dateTime ASC")
    fun getAllAppointments(status: String): Flow<List<Appointment>>

    // МЕТОД ДЛЯ СТАТИСТИКИ (ФИЛЬТРУЕТ ПО СТАТУСУ И ДИАПАЗОНУ)
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
        AND appt_status = :status
        ORDER BY appt_dateTime ASC
    """)
    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long, status: String): Flow<List<AppointmentWithClientAndService>>

    // НОВЫЙ МЕТОД: Для отображения расписания дня (без фильтрации по статусу)
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
    fun getAppointmentsForDayIncludingAllStatuses(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>>

    @Query("""
        SELECT * FROM appointments
        WHERE (dateTime < :newEnd AND (dateTime + serviceDuration * 60 * 1000) > :newStart)
        AND id != :excludeAppointmentId
    """)
    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment>

    @Query("SELECT * FROM appointments WHERE status = :status")
    suspend fun getAppointmentsByStatus(status: String): List<Appointment>
}