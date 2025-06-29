package com.massagepro.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository

class AppointmentsViewModelFactory(
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppointmentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppointmentsViewModel(appointmentRepository, clientRepository, serviceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
