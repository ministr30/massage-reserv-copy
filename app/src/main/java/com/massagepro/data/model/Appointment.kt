
package com.massagepro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val serviceId: Int,
    val startTime: Date,
    val endTime: Date,
    val totalCost: Double,
    val status: String = "Запланировано" // "Запланировано", "Отменена", "Завершена"
)


