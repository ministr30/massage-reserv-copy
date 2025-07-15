package com.massagepro.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode

@Serializable
data class Client(
    @EncodeDefault(Mode.NEVER)
    val id: Long? = null,
    val name: String,
    val phone: String? = null,
    val notes: String? = null
)