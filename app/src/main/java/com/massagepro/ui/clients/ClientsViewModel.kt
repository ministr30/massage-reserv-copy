package com.massagepro.ui.clients

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massagepro.data.model.Client
import com.massagepro.data.repository.ClientRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ClientUiState {
    object Loading : ClientUiState()
    data class Success(val clients: List<Client>) : ClientUiState()
    data class Error(val message: String) : ClientUiState()
}

class ClientsViewModel(private val repository: ClientRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ClientUiState> =
        combine(_searchQuery, _refreshTrigger) { query, _ -> query }
            .flatMapLatest { query ->
                val flow = if (query.isBlank()) repository.getAllClients()
                else repository.searchClients(query)

                flow
                    .map<List<Client>, ClientUiState> { ClientUiState.Success(it) }
                    .catch { e ->
                        Log.e("ClientsViewModel", "Flow error: ${e.message}", e)
                        emit(ClientUiState.Error(e.message ?: "Unknown error"))
                    }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ClientUiState.Loading
            )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun reload() {
        viewModelScope.launch {
            _refreshTrigger.emit(_refreshTrigger.value + 1)
        }
    }

    suspend fun insertClient(client: Client): Boolean {
        return try {
            repository.insertClient(client)
            reload()
            true
        } catch (e: Exception) {
            Log.e("ClientsViewModel", "Insert error: ${e.message}", e)
            false
        }
    }

    suspend fun updateClient(client: Client): Boolean {
        return try {
            repository.updateClient(client)
            reload()
            true
        } catch (e: Exception) {
            Log.e("ClientsViewModel", "Update error: ${e.message}", e)
            false
        }
    }

    suspend fun deleteClient(client: Client): Boolean {
        return try {
            repository.deleteClient(client)
            reload()
            true
        } catch (e: Exception) {
            Log.e("ClientsViewModel", "Delete error: ${e.message}", e)
            false
        }
    }

    suspend fun getClientById(id: Long): Client? {
        return try {
            repository.getClientById(id)
        } catch (e: Exception) {
            Log.e("ClientsViewModel", "GetById error: ${e.message}", e)
            null
        }
    }
}
