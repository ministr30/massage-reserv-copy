package com.massagepro.ui.home

import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import java.util.Calendar

data class TimeSlot(
    val startTime: Calendar,
    val endTime: Calendar,
    val isBooked: Boolean,
    val bookedAppointment: Appointment? = null,
    val client: Client? = null,
    val service: Service? = null,
    val shouldDisplay: Boolean = true // По умолчанию true, будем менять в ViewModel
)
