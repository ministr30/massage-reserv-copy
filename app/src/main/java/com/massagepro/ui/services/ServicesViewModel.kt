package com.massagepro.ui.services

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.massagepro.data.model.Service
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ServiceUiState {
    object Loading : ServiceUiState()
    data class Success(val services: List<Service>) : ServiceUiState()
    data class Error(val message: String) : ServiceUiState()
}

class ServicesViewModel(private val repository: ServiceRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 0) // Новый триггер

    private val _uiState = MutableStateFlow<ServiceUiState>(ServiceUiState.Loading)
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    init {
        observeServices()
    }

    private fun observeServices() {
        viewModelScope.launch {
            combine(_searchQuery.debounce(300), _refreshTrigger.onStart { emit(Unit) }) { query, _ -> query }
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        repository.getAllServices()
                    } else {
                        repository.searchServices(query)
                    }
                }
                .catch { e ->
                    _uiState.value = ServiceUiState.Error("Помилка при завантаженні послуг")
                }
                .collect { services ->
                    _uiState.value = ServiceUiState.Success(services)
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshServices() {
        viewModelScope.launch {
            _refreshTrigger.emit(Unit)  // триггерим повторный запрос
        }
    }

    suspend fun insertService(service: Service): Boolean {
        return try {
            repository.insertService(service)
            refreshServices()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateService(service: Service): Boolean {
        return try {
            repository.updateService(service)
            refreshServices()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteService(service: Service): Boolean {
        return try {
            repository.deleteService(service)
            refreshServices()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getServiceById(id: Long): Service? {
        return try {
            repository.getServiceById(id)
        } catch (e: Exception) {
            null
        }
    }
}
