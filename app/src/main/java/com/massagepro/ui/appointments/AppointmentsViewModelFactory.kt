package com.massagepro.ui.appointments

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository

class AppointmentsViewModelFactory(
    private val application: Application,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppointmentsViewModel::class.java)) {
            "Unknown ViewModel class"
        }
        @Suppress("UNCHECKED_CAST")
        return AppointmentsViewModel(
            application,
            appointmentRepository,
            clientRepository,
            serviceRepository
        ) as T
    }
}