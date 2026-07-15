package com.mexadev.aura

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.session.BiometricHelper
import kotlinx.coroutines.launch
import java.io.IOException

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        // Reusamos el layout del splash para mantener la estética limpia
        setContentView(R.layout.activity_splash)
        
        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
        
        findViewById<View>(R.id.btnErrorRetry)?.setOnClickListener {
            findViewById<View>(R.id.layoutErrorOverlay)?.visibility = View.GONE
            requestBiometricAndVerify()
        }
        
        requestBiometricAndVerify()
    }
    
    private fun requestBiometricAndVerify() {
        if (BiometricHelper.isBiometricAvailable(this)) {
            BiometricHelper.showBiometricPrompt(
                activity = this,
                title = "Aplicación Bloqueada",
                subtitle = "Usa tu huella para volver a acceder",
                onSuccess = {
                    verifySession()
                },
                onError = {
                    finishAffinity() // Cerrar toda la app si falla
                },
                onCancel = {
                    finishAffinity() // Cerrar si el usuario cancela
                }
            )
        } else {
            // Si el dispositivo no tiene biometría configurada, simplemente recarga la sesión.
            verifySession()
        }
    }
    
    private fun verifySession() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getProfile()
                if (response.isSuccessful) {
                    finish() // Desbloquear la app y volver a la pantalla anterior
                } else {
                    val code = response.code()
                    if (code == 401 || code == 403) {
                        startActivity(Intent(this@LockActivity, LoginActivity::class.java))
                        finishAffinity()
                    } else if (code >= 500) {
                        showErrorOverlay("Mantenimiento", "El servidor se encuentra en mantenimiento o presentó un problema.\nIntenta más tarde.")
                    } else {
                        showErrorOverlay("Error", "Ocurrió un error inesperado al conectar con el servidor.")
                    }
                }
            } catch (e: IOException) {
                showErrorOverlay("Sin Conexión", "No hay conexión al servidor.\nVerifica tu red y vuelve a intentarlo.")
            } catch (e: Exception) {
                showErrorOverlay("Error", "Ocurrió un error inesperado al conectar con el servidor.")
            }
        }
    }
    
    private fun showErrorOverlay(title: String, message: String) {
        findViewById<TextView>(R.id.tvErrorTitle)?.text = title
        findViewById<TextView>(R.id.tvErrorMessage)?.text = message
        findViewById<View>(R.id.layoutErrorOverlay)?.visibility = View.VISIBLE
    }
}
