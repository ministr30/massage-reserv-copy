package com.massagepro.data.repository

import com.massagepro.data.dao.ServiceDao
import com.massagepro.data.model.Service
import kotlinx.coroutines.flow.Flow

class ServiceRepository(private val serviceDao: ServiceDao) {

    fun getAllServices(): Flow<List<Service>> {
        return serviceDao.getAllServices()
    }

    suspend fun insertService(service: Service) {
        serviceDao.insertService(service)
    }

    suspend fun updateService(service: Service) {
        serviceDao.updateService(service)
    }

    suspend fun deleteService(service: Service) {
        serviceDao.deleteService(service)
    }

    suspend fun getServiceById(serviceId: Int): Service? {
        return serviceDao.getServiceById(serviceId)
    }

    // ДОБАВЛЕНО: Методы для работы с категориями (ВОССТАНОВЛЕНЫ)
    fun getServicesByCategory(category: String): Flow<List<Service>> {
        return serviceDao.getServicesByCategory(category)
    }

    fun getAllCategories(): Flow<List<String>> {
        return serviceDao.getAllCategories()
    }
}