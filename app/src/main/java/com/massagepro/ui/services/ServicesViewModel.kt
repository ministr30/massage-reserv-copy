package com.massagepro.ui.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.data.model.Service
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.asFlow

class ServicesViewModel(private val serviceRepository: ServiceRepository) : ViewModel() {

    private val _selectedCategory = MutableLiveData<String?>(null)
    val selectedCategory: LiveData<String?> = _selectedCategory

    val allCategories: LiveData<List<String>> = serviceRepository.getAllCategories().asLiveData()

    val allServices: LiveData<List<Service>> = combine(
        serviceRepository.getAllServices(),
        _selectedCategory.asFlow()
    ) { services, selectedCategory ->
        if (selectedCategory.isNullOrEmpty()) {
            services
        } else {
            services.filter { it.category == selectedCategory }
        }
    }.asLiveData()

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }


    fun insertService(service: Service) = viewModelScope.launch {
        serviceRepository.insertService(service)
    }

    fun updateService(service: Service) = viewModelScope.launch {
        serviceRepository.updateService(service)
    }

    fun deleteService(service: Service) = viewModelScope.launch {
        serviceRepository.deleteService(service)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceRepository.getServiceById(serviceId)
    }
}

class ServicesViewModelFactory(private val serviceRepository: ServiceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServicesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServicesViewModel(serviceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}