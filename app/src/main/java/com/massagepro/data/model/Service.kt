
package com.massagepro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val basePrice: Double,
    val duration: Int, // in minutes
    val category: String?,
    val isActive: Boolean = true
)


