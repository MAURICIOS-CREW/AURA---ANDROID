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

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit { putBoolean("notifications_enabled", value) }

    var importanceAccessQr: String
        get() = prefs.getString("importance_access_qr", "high") ?: "high"
        set(value) = prefs.edit { putString("importance_access_qr", value) }

    var importanceAccessPlaca: String
        get() = prefs.getString("importance_access_placa", "high") ?: "high"
        set(value) = prefs.edit { putString("importance_access_placa", value) }

    var importanceIncident: String
        get() = prefs.getString("importance_incident", "high") ?: "high"
        set(value) = prefs.edit { putString("importance_incident", value) }

    /** Number of available services for skeleton loading */
    var availableServicesCount: Int
        get() = prefs.getInt("available_services_count", 4)
        set(value) = prefs.edit { putInt("available_services_count", value) }

    /** Number of contracted services for skeleton loading */
    var contractedServicesCount: Int
        get() = prefs.getInt("contracted_services_count", 3)
        set(value) = prefs.edit { putInt("contracted_services_count", value) }

    /** Number of documents for skeleton loading */
    var documentsCount: Int
        get() = prefs.getInt("documents_count", 3)
        set(value) = prefs.edit { putInt("documents_count", value) }

    /** Number of polls for skeleton loading */
    var pollsCount: Int
        get() = prefs.getInt("polls_count", 2)
        set(value) = prefs.edit { putInt("polls_count", value) }

    /** Number of community posts for skeleton loading */
    var communityCount: Int
        get() = prefs.getInt("community_count", 3)
        set(value) = prefs.edit { putInt("community_count", value) }
}

