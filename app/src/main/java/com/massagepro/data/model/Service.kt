package com.massagepro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val duration: Int, // Тривалість послуги в хвилинах
    val basePrice: Int, // ЗМІНЕНО: Тепер це Int (ціле число)
    val category: String = "", // Категория
    val isActive: Boolean = true // Статус активности услуги
)