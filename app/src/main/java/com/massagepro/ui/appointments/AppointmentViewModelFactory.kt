
package com.massagepro.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.dao.ServiceDao

class AppointmentViewModelFactory(private val appointmentDao: AppointmentDao, private val clientDao: ClientDao, private val serviceDao: ServiceDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppointmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppointmentViewModel(appointmentDao, clientDao, serviceDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


