package com.mexadev.aura.fcm.resolver

import com.mexadev.aura.core.preferences.PreferencesManager

class NotificationImportanceResolver(private val prefs: PreferencesManager) {

    fun resolveImportance(data: Map<String, String>): NotificationImportance {
        if (!prefs.notificationsEnabled) {
            return NotificationImportance.OFF
        }

        val rawType = (data["type"] ?: "").lowercase()
        val rawMethod = (data["method"] ?: data["access_type"] ?: "").lowercase()

        val importanceKey = when {
            isIncidentType(rawType) -> prefs.importanceIncident
            isAccessType(rawType) -> resolveAccessImportance(rawMethod, data)
            else -> fallbackImportance(rawMethod, data)
        }

        return NotificationImportance.fromKey(importanceKey)
    }

    private fun isIncidentType(type: String): Boolean =
        type.contains("incident") || type.contains("incidencia")

    private fun isAccessType(type: String): Boolean =
        type.contains("access") || type.contains("acceso")

    private fun resolveAccessImportance(method: String, data: Map<String, String>): String {
        return if (isPlateMethod(method, data)) {
            prefs.importanceAccessPlaca
        } else {
            prefs.importanceAccessQr
        }
    }

    private fun fallbackImportance(method: String, data: Map<String, String>): String {
        return when {
            isPlateMethod(method, data) -> prefs.importanceAccessPlaca
            isQrMethod(method, data) -> prefs.importanceAccessQr
            else -> prefs.notificationImportance
        }
    }

    private fun isPlateMethod(method: String, data: Map<String, String>): Boolean =
        method.contains("placa") || method.contains("plate") || method.contains("vehic")

    private fun isQrMethod(method: String, data: Map<String, String>): Boolean =
        method.contains("qr") || data.containsKey("scanned_code")
}
