package com.massagepro.ui.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.dao.ServiceDao
import com.massagepro.data.model.Service
import kotlinx.coroutines.launch

class ServicesViewModel(private val serviceDao: ServiceDao) : ViewModel() {

    val allServices: LiveData<List<Service>> = serviceDao.getAllServices().asLiveData()

    fun insertService(service: Service) = viewModelScope.launch {
        serviceDao.insertService(service)
    }

    fun updateService(service: Service) = viewModelScope.launch {
        serviceDao.updateService(service)
    }

    fun deleteService(service: Service) = viewModelScope.launch {
        serviceDao.deleteService(service)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceDao.getServiceById(serviceId)
    }
}


