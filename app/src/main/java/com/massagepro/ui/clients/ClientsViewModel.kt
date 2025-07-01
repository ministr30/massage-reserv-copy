package com.massagepro.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.model.Client
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Добавлены аннотации @OptIn для подавления предупреждений
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClientsViewModel(private val clientRepository: ClientRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allClients: StateFlow<List<Client>> = searchQuery
        .debounce(300) // необязательно, но удобно
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                clientRepository.getAllClients()
            } else {
                clientRepository.searchClients("%$query%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun insertClient(client: Client) = viewModelScope.launch {
        clientRepository.insertClient(client)
    }

    fun updateClient(client: Client) = viewModelScope.launch {
        clientRepository.updateClient(client)
    }

    fun deleteClient(client: Client) = viewModelScope.launch {
        clientRepository.deleteClient(client)
    }

    suspend fun getClientById(clientId: Int): Client? {
        return clientRepository.getClientById(clientId)
    }
}

class ClientsViewModelFactory(private val clientRepository: ClientRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClientsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClientsViewModel(clientRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
