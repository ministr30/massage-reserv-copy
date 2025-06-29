package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    // Запрос для получения всех записей с именами клиента и услуги
    // ИСПРАВЛЕНО: Добавлены явные псевдонимы 'appt_' для полей таблицы appointments,
    // чтобы избежать конфликтов имен с clients.name и services.name
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
        INNER JOIN
            clients c ON a.clientId = c.id
        INNER JOIN
            services s ON a.serviceId = s.id
        ORDER BY
            a.dateTime ASC
    """)
    fun getAppointmentsWithClientAndService(): Flow<List<AppointmentWithClientAndService>>

    // Запрос для получения записей за определенный день с именами клиента и услуги
    // ИСПРАВЛЕНО: Добавлены явные псевдонимы 'appt_' для полей таблицы appointments
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
        INNER JOIN
            clients c ON a.clientId = c.id
        INNER JOIN
            services s ON a.serviceId = s.id
        WHERE
            a.dateTime BETWEEN :startOfDayMillis AND :endOfDayMillis
        ORDER BY
            a.dateTime ASC
    """)
    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>>

    // Метод для проверки конфликтов времени, использующий dateTime и serviceDuration
    // ИСПРАВЛЕНО: Запрос для проверки конфликтов
    @Query("""
        SELECT * FROM appointments 
        WHERE 
            id != :excludeAppointmentId AND 
            (
                (:newStart < (dateTime + serviceDuration * 60 * 1000) AND :newEnd > dateTime) 
                OR 
                (:newStart = dateTime AND :newEnd = (dateTime + serviceDuration * 60 * 1000))
            )
    """)
    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Int): List<Appointment>

}