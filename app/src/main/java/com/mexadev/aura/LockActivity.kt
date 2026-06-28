package com.mexadev.aura

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.mexadev.aura.core.session.BiometricHelper

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reusamos el layout del splash para mantener la estética limpia
        setContentView(R.layout.activity_splash)
        
        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
        
        if (BiometricHelper.isBiometricAvailable(this)) {
            BiometricHelper.showBiometricPrompt(
                activity = this,
                title = "Aplicación Bloqueada",
                subtitle = "Usa tu huella para volver a acceder",
                onSuccess = {
                    finish() // Desbloquear la app y volver a la pantalla anterior
                },
                onError = {
                    finishAffinity() // Cerrar toda la app si falla
                },
                onCancel = {
                    finishAffinity() // Cerrar si el usuario cancela
                }
            )
        } else {
            // Si el dispositivo no tiene biometría configurada, simplemente entra.
            // Opcionalmente se podría redirigir a un login manual.
            finish()
        }
    }
}
