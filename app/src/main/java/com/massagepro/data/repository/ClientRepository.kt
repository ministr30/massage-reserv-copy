package com.massagepro.data.repository

import com.massagepro.data.dao.ClientDao
import com.massagepro.data.model.Client
import kotlinx.coroutines.flow.Flow

class ClientRepository(private val clientDao: ClientDao) {

    fun getAllClients(): Flow<List<Client>> {
        return clientDao.getAllClients()
    }

    suspend fun insertClient(client: Client) {
        clientDao.insertClient(client)
    }

    suspend fun updateClient(client: Client) {
        clientDao.updateClient(client)
    }

    suspend fun deleteClient(client: Client) {
        clientDao.deleteClient(client)
    }

    suspend fun getClientById(clientId: Int): Client? {
        return clientDao.getClientById(clientId)
    }

    fun searchClients(query: String): Flow<List<Client>> {
        return clientDao.searchClients(query)
    }
}
