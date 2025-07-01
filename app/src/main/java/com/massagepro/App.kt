package com.massagepro // или ваш актуальный пакет

import android.app.Application
import com.massagepro.data.AppDatabase // Убедитесь, что это правильный путь к вашему AppDatabase
// import androidx.room.Room // Больше не нужен прямой импорт Room здесь

class App : Application() {

    // ИЗМЕНЕНО: Теперь используем метод getDatabase() из AppDatabase
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this) // Передаем this (Application context)
    }

    override fun onCreate() {
        super.onCreate()
        // Здесь можно добавить любую другую инициализацию на уровне приложения
    }
}
