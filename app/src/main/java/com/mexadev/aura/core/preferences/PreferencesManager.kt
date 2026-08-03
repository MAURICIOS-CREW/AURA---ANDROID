package com.mexadev.aura.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_ui_prefs", Context.MODE_PRIVATE)

    var biometricSessionRenewal: Boolean
        get() = prefs.getBoolean("biometric_session_renewal", true)
        set(value) = prefs.edit { putBoolean("biometric_session_renewal", value) }

    var lockOnExit: Boolean
        get() = prefs.getBoolean("lock_on_exit", false)
        set(value) = prefs.edit { putBoolean("lock_on_exit", value) }

    var vehiclesCount: Int
        get() = prefs.getInt("vehicles_count", 0)
        set(value) = prefs.edit { putInt("vehicles_count", value) }

    /** Número de incidentes del usuario para mostrar skeletons en la próxima apertura */
    var incidentsCount: Int
        get() = prefs.getInt("incidents_count", 0)
        set(value) = prefs.edit { putInt("incidents_count", value) }

    /**
     * ID del usuario autenticado, guardado al hacer login.
     * Se usa para distinguir burbujas propias en el chat de comentarios.
     */
    var userId: Long
        get() = prefs.getLong("user_id", -1L)
        set(value) = prefs.edit { putLong("user_id", value) }

    var notificationImportance: String
        get() = prefs.getString("notification_importance", "high") ?: "high"
        set(value) = prefs.edit { putString("notification_importance", value) }
}

