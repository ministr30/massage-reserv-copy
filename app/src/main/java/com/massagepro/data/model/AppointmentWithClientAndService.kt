package com.massagepro.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class AppointmentWithClientAndService(
    @Embedded(prefix = "appt_") val appointment: Appointment, // Добавляем префикс
    val clientName: String,
    val clientPhone: String,
    val serviceCategory: String,
    val serviceDuration: Int, // Это длительность из Service, а не из Appointment
    val serviceBasePrice: Int
)
