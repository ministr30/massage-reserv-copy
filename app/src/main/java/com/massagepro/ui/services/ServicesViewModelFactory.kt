package com.massagepro.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.repository.ServiceRepository

class ServicesViewModelFactory(
    private val repository: ServiceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServicesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServicesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
