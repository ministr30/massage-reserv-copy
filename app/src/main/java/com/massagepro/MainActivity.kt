package com.massagepro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.massagepro.databinding.ActivityMainBinding
import androidx.core.view.WindowCompat // Убедитесь, что этот импорт есть

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Указываем системе, что контент должен рисоваться от края до края,
        // то есть под системными панелями (строкой состояния и системной навигацией).
        WindowCompat.setDecorFitsSystemWindows(window, false) // ВОЗВРАЩЕНО НА false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true
        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
    }
}
