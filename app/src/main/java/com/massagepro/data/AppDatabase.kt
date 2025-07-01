package com.massagepro.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.dao.ServiceDao
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import androidx.room.Room
import android.content.Context

@Database(entities = [Client::class, Service::class, Appointment::class], version =9, exportSchema = false) // Версия увеличена до 6
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun serviceDao(): ServiceDao
    abstract fun appointmentDao(): AppointmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ИЗМЕНЕНО: Удален неиспользуемый параметр 'scope'
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "massagepro_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
