package com.massagepro.data.model

enum class AppointmentStatus(val statusValue: String) {
    PLANNED("Заплановано"),
    COMPLETED("Завершено"),
    CANCELED("Скасовано"),
    MISSED("Неявка")
}