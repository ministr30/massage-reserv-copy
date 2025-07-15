package com.massagepro.data.repository

import com.massagepro.data.model.Service
import com.massagepro.data.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ServiceRepository {

    private val postgrest = SupabaseClient.client.postgrest["Services"]

    fun getAllServices(): Flow<List<Service>> = flow {
        val services = postgrest.select().decodeList<Service>()
        emit(services)
    }

    fun searchServices(query: String): Flow<List<Service>> = flow {
        val services = postgrest.select {
            filter { ilike("category", "%$query%") }
        }.decodeList<Service>()
        emit(services)
    }

    suspend fun getAllServicesOnce(): List<Service> = withContext(Dispatchers.IO) {
        postgrest.select().decodeList()
    }

    suspend fun insertService(service: Service) = withContext(Dispatchers.IO) {
        postgrest.insert(service)
    }

    suspend fun updateService(service: Service) = withContext(Dispatchers.IO) {
        postgrest.update({
            set("category", service.category)
            set("duration", service.duration)
            set("basePrice", service.basePrice)
            set("description", service.description)
        }) {
            filter { eq("id", service.id!!) }
        }
    }

    suspend fun deleteService(service: Service) = withContext(Dispatchers.IO) {
        postgrest.delete {
            filter { eq("id", service.id!!) }
        }
    }

    suspend fun getServiceById(serviceId: Long): Service? = withContext(Dispatchers.IO) {
        postgrest.select {
            filter { eq("id", serviceId) }
        }.decodeSingleOrNull()
    }
}
