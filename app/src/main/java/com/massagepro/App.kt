package com.massagepro // или ваш актуальный пакет

import android.app.Application
// import com.massagepro.data.AppDatabase // Удалено, так как Room больше не используется

class App : Application() {

    // Удалено: val database: AppDatabase by lazy { ... } – Room больше не нужен

    override fun onCreate() {
        super.onCreate()
        // Здесь можно добавить любую другую инициализацию на уровне приложения
    }
}