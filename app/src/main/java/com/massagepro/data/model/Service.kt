package com.massagepro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Service(
    val id: Long? = null,
    val category: String = "",
    val duration: Int = 0,
    val basePrice: Int = 0,
    val description: String? = null
)