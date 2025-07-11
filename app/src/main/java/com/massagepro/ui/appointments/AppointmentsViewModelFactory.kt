package com.massagepro.ui.appointments

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository

// Фабрика для создания AppointmentsViewModel
class AppointmentsViewModelFactory(
    private val application: Application, // Application context, если нужен в ViewModel
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModelProvider.Factory { // <-- ЭТО ВАЖНО: Реализация ViewModelProvider.Factory

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppointmentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppointmentsViewModel(
                application,
                appointmentRepository,
                clientRepository,
                serviceRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
