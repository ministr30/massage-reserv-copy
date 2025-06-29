package com.massagepro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val serviceId: Int,
    val serviceName: String,
    val serviceDuration: Int,
    val servicePrice: Int, // ЗМІНЕНО: Тепер це Int (ціле число)
    val dateTime: Long,
    val notes: String? = null,
    val status: String = "Заплановано"
)