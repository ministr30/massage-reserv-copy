package com.massagepro.data.dao

import androidx.room.*
import com.massagepro.data.model.Appointment
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    // 1. Добавлен метод для получения всех записей, который возвращает Flow
    @Query("SELECT * FROM appointments ORDER BY startTime DESC")
    fun getAllAppointments(): Flow<List<Appointment>>

    // 2. Методы insert, update, delete переименованы, чтобы совпадать с ViewModel
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment)

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    // 3. getAppointmentById изменен на suspend и возвращает Appointment?
    // Это соответствует тому, что ожидает AppointmentViewModel.
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Int): Appointment?

    /**
     * Сигнатура метода для получения записей по диапазону дат.
     * Возвращает Flow<List<Appointment>>
     */
    @Query("SELECT * FROM appointments WHERE startTime BETWEEN :startMillis AND :endMillis ORDER BY startTime ASC")
    fun getAppointmentsForDateRange(startMillis: Long, endMillis: Long): Flow<List<Appointment>>

    // 4. Добавлен метод для получения конфликтующих записей
    /**
     * Возвращает список записей, которые конфликтуют по времени с заданным интервалом,
     * исключая запись с определенным ID (полезно при обновлении существующей записи).
     * Время хранится как Long (миллисекунды).
     */
    @Query("SELECT * FROM appointments WHERE id != :excludeAppointmentId AND ((startTime < :endTime AND endTime > :startTime) OR (startTime = :startTime AND endTime = :endTime))")
    suspend fun getConflictingAppointments(startTime: Long, endTime: Long, excludeAppointmentId: Int): List<Appointment>
}