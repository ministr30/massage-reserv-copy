package com.massagepro.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.massagepro.data.model.Service
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY name ASC")
    fun getAllServices(): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE id = :serviceId")
    suspend fun getServiceById(serviceId: Int): Service?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: Service)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateService(service: Service)

    @Delete
    suspend fun deleteService(service: Service)

    @Query("SELECT * FROM services WHERE category = :category ORDER BY name ASC")
    fun getServicesByCategory(category: String): Flow<List<Service>>

    @Query("SELECT DISTINCT category FROM services ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
}
