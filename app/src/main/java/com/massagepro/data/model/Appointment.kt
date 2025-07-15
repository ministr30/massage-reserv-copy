package com.massagepro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    val id: Long = 0,           // ← было Int
    val clientId: Long,         // ← было Int
    val serviceId: Long,        // ← было Int
    val dateTime: Long,
    val serviceDuration: Int,
    val servicePrice: Int,
    val status: String,
    val notes: String? = null
)