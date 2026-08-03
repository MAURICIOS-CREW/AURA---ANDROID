package com.mexadev.aura.fcm.resolver

import android.app.NotificationManager
import androidx.core.app.NotificationCompat

enum class NotificationImportance(
    val key: String,
    val channelId: String,
    val channelName: String,
    val osImportance: Int,
    val priority: Int,
    val vibrationPattern: LongArray?,
    val shouldWakeScreen: Boolean,
    val playsSound: Boolean
) {
    HIGH(
        key = "high",
        channelId = "aura_alerts_high_v2",
        channelName = "Alertas de Seguridad (Críticas)",
        osImportance = NotificationManager.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500),
        shouldWakeScreen = true,
        playsSound = true
    ),
    MEDIUM(
        key = "medium",
        channelId = "aura_alerts_normal",
        channelName = "Alertas Normales",
        osImportance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        vibrationPattern = null,
        shouldWakeScreen = false,
        playsSound = true
    ),
    LOW(
        key = "low",
        channelId = "aura_alerts_low",
        channelName = "Alertas Silenciosas",
        osImportance = NotificationManager.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_LOW,
        vibrationPattern = null,
        shouldWakeScreen = false,
        playsSound = false
    ),
    OFF(
        key = "off",
        channelId = "",
        channelName = "",
        osImportance = NotificationManager.IMPORTANCE_NONE,
        priority = NotificationCompat.PRIORITY_MIN,
        vibrationPattern = null,
        shouldWakeScreen = false,
        playsSound = false
    );

    companion object {
        fun fromKey(key: String?): NotificationImportance {
            return when (key?.lowercase()) {
                "high" -> HIGH
                "medium", "normal" -> MEDIUM
                "low" -> LOW
                "off" -> OFF
                else -> HIGH
            }
        }
    }
}
