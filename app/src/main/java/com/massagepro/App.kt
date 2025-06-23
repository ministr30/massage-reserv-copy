package com.massagepro

import android.app.Application
import com.massagepro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import androidx.room.Room // ВОТ ЭТА СТРОКА!

class App : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "massage_pro_database")
            .fallbackToDestructiveMigration()
            .build()
    }
}
