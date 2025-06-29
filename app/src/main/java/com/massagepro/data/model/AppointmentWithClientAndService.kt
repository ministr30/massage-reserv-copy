package com.massagepro.data.model

import androidx.room.Embedded

data class AppointmentWithClientAndService(
    @Embedded(prefix = "appt_") // <--- ДОБАВЛЕНО ЗДЕСЬ
    val appointment: Appointment, // Теперь все поля из Appointment будут иметь префикс "appt_"
    // Например, appointment.serviceName станет appt_serviceName в базе данных

    val clientName: String,     // Это поле будет сопоставлено с колонкой clientName
    val serviceName: String      // Это поле будет сопоставлено с колонкой serviceName
)