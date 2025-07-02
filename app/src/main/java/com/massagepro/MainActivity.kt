package com.massagepro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.massagepro.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var isLoading = true // Флаг для контроля видимости заставки

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Устанавливаем заставку
        val splashScreen = installSplashScreen()

        // 2. Устанавливаем условие, при котором заставка будет на экране.
        // Она будет видна, пока isLoading равно true.
        splashScreen.setKeepOnScreenCondition { isLoading }

        // 3. Запускаем корутину с задержкой в 3 секунды
        lifecycleScope.launch {
            delay(3000) // 3000 миллисекунд = 3 секунды
            // 4. После задержки меняем флаг, что позволяет заставке исчезнуть
            isLoading = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
    }
}
