package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY dateTime ASC")
    fun getAllAppointments(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Int): Appointment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT
            a.id AS appt_id,
            a.clientId AS appt_clientId,
            a.serviceId AS appt_serviceId,
            a.serviceName AS appt_serviceName,
            a.serviceDuration AS appt_serviceDuration,
            a.servicePrice AS appt_servicePrice,
            a.dateTime AS appt_dateTime,
            a.notes AS appt_notes,
            a.status AS appt_status,
            c.name AS clientName,
            s.name AS serviceName
        FROM
            appointments a
        INNER JOIN clients c ON a.clientId = c.id
        INNER JOIN services s ON a.serviceId = s.id
        ORDER BY a.dateTime ASC
    """)
    fun getAppointmentsWithClientAndService(): Flow<List<AppointmentWithClientAndService>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT
            a.id AS appt_id,
            a.clientId AS appt_clientId,
            a.serviceId AS appt_serviceId,
            a.serviceName AS appt_serviceName,
            a.serviceDuration AS appt_serviceDuration,
            a.servicePrice AS appt_servicePrice,
            a.dateTime AS appt_dateTime,
            a.notes AS appt_notes,
            a.status AS appt_status,
            c.name AS clientName,
            s.name AS serviceName
        FROM
            appointments a
        INNER JOIN clients c ON a.clientId = c.id
        INNER JOIN services s ON a.serviceId = s.id
        WHERE a.dateTime BETWEEN :startOfDayMillis AND :endOfDayMillis
        ORDER BY a.dateTime ASC
    """)
    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>>

    // Добавь аннотацию @Query с реальным SQL, если нужно, иначе оставь suspend fun без тела
    // Пример заглушки, замени под свою логику
    @Query("""
        SELECT * FROM appointments
        WHERE (:newStart < dateTime + serviceDuration) AND (:newEnd > dateTime)
        AND id != :excludeAppointmentId
    """)
    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment>
}
