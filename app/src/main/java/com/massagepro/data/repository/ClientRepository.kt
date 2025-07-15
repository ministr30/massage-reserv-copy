package com.massagepro.data.repository

import com.massagepro.data.model.Client
import com.massagepro.data.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class ClientRepository {

    private val postgrest = SupabaseClient.client.postgrest["Clients"]

    fun getAllClients(): Flow<List<Client>> = flow {
        emit(postgrest.select().decodeList<Client>())
    }

    suspend fun insertClient(client: Client) = withContext(Dispatchers.IO) {
        SupabaseClient.client
            .postgrest["Clients"]
            .insert(client)
    }




    suspend fun updateClient(client: Client) {
        postgrest.update({
            set("name", client.name)
            set("phone", client.phone)
            set("notes", client.notes)
        }) {
            filter { eq("id", client.id!!) }
        }
    }

    suspend fun deleteClient(client: Client) {
        postgrest.delete { filter { eq("id", client.id!!) } }
    }

    suspend fun getClientById(clientId: Long): Client? =
        postgrest.select { filter { eq("id", clientId) } }.decodeSingleOrNull<Client>()

    fun searchClients(query: String): Flow<List<Client>> = flow {
        val nameResults = postgrest.select {
            filter { ilike("name", "%$query%") }
        }.decodeList<Client>()

        val phoneResults = postgrest.select {
            filter { ilike("phone", "%$query%") }
        }.decodeList<Client>()

        emit((nameResults + phoneResults).distinctBy { it.id })
    }
}