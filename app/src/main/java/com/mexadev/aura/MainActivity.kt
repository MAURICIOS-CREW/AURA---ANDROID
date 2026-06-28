package com.mexadev.aura

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mexadev.aura.databinding.ActivityMainBinding
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.core.session.BiometricHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
        
        val sessionManager = SessionManager(this)
        
        // Escucha peticiones de validación biométrica del TokenAuthenticator de Retrofit
        lifecycleScope.launch {
            sessionManager.biometricRequestFlow.collect { callback ->
                BiometricHelper.showBiometricPrompt(
                    activity = this@MainActivity,
                    title = "Renovación de Sesión",
                    subtitle = "Tu sesión expiró. Verifica tu identidad para renovarla automáticamente.",
                    onSuccess = { callback(true) },
                    onError = { callback(false) },
                    onCancel = { callback(false) }
                )
            }
        }
    }
}