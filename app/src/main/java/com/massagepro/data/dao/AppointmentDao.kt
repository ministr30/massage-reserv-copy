package com.massagepro.data.dao

import androidx.room.*
import com.massagepro.data.model.Appointment
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    // --- Ваши существующие методы (insert, update, delete, и т.д.) остаются без изменений ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: Appointment)

    @Update
    suspend fun update(appointment: Appointment)

    @Delete
    suspend fun delete(appointment: Appointment)

    @Query("SELECT * FROM appointments WHERE id = :id")
    fun getAppointmentById(id: Int): Flow<Appointment?>
    // --- Конец существующих методов ---


    /**
     * ИСПРАВЛЕНО: Сигнатура метода теперь принимает Long (миллисекунды).
     * Запрос WHERE startTime BETWEEN ... теперь будет работать корректно.
     */
    @Query("SELECT * FROM appointments WHERE startTime BETWEEN :startMillis AND :endMillis ORDER BY startTime ASC")
    fun getAppointmentsForDateRange(startMillis: Long, endMillis: Long): Flow<List<Appointment>>

}
