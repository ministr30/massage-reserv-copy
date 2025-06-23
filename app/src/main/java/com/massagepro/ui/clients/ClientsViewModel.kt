package com.massagepro.ui.clients

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.model.Client
import kotlinx.coroutines.launch

class ClientsViewModel(private val clientDao: ClientDao) : ViewModel() {

    val allClients: LiveData<List<Client>> = clientDao.getAllClients().asLiveData()

    fun insertClient(client: Client) = viewModelScope.launch {
        clientDao.insertClient(client)
    }

    fun updateClient(client: Client) = viewModelScope.launch {
        clientDao.updateClient(client)
    }

    fun deleteClient(client: Client) = viewModelScope.launch {
        clientDao.deleteClient(client)
    }

    // ИСПРАВЛЕНО: Теперь это suspend функция, которая возвращает Client?
    suspend fun getClientById(clientId: Int): Client? {
        return clientDao.getClientById(clientId)
    }

    fun searchClients(query: String): LiveData<List<Client>> {
        return clientDao.searchClients(query).asLiveData()
    }
}
