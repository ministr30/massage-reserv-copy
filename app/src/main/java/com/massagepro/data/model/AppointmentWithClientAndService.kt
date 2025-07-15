package com.massagepro.data.model

data class AppointmentWithClientAndService(
    val appointment: Appointment,
    val clientName: String,
    val serviceName: String,
    val serviceCategory: String,
    val serviceDuration: Int,
    val serviceBasePrice: Int
)