
package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.massagepro.data.model.Appointment
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY startTime ASC")
    fun getAllAppointments(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :appointmentId")
    suspend fun getAppointmentById(appointmentId: Int): Appointment?

    @Insert
    suspend fun insertAppointment(appointment: Appointment)

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Query("SELECT * FROM appointments WHERE startTime BETWEEN :startDate AND :endDate ORDER BY startTime ASC")
    fun getAppointmentsForDateRange(startDate: Date, endDate: Date): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE clientId = :clientId ORDER BY startTime ASC")
    fun getAppointmentsByClient(clientId: Int): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE status = :status ORDER BY startTime ASC")
    fun getAppointmentsByStatus(status: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE (startTime < :endTime AND endTime > :startTime) AND id != :excludeAppointmentId")
    suspend fun getConflictingAppointments(startTime: Date, endTime: Date, excludeAppointmentId: Int): List<Appointment>

    @Query("SELECT COUNT(*) FROM appointments WHERE DATE(startTime / 1000, 'unixepoch') = DATE(:date / 1000, 'unixepoch')")
    suspend fun getAppointmentCountForDate(date: Date): Int

    @Query("SELECT SUM(totalCost) FROM appointments WHERE DATE(startTime / 1000, 'unixepoch') = DATE(:date / 1000, 'unixepoch') AND status = 'Запланировано'")
    suspend fun getTotalRevenueForDate(date: Date): Double?
}


