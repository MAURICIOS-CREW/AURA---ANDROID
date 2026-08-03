package com.mexadev.aura.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.data.model.auth.FcmRequest
import com.mexadev.aura.ui.fcm.QuickActionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    @Deprecated("Deprecated by Firebase, but still required for background token updates")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        
        CoroutineScope(Dispatchers.IO).launch {
            com.mexadev.aura.fcm.FcmHelper.syncToken(
                token, 
                sessionManager, 
                ApiClient.apiService
            )
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data

        // Como usamos Opción 1 (Solo Data), extraemos de data. Si enviaran notification, hacemos fallback.
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Notificación de Aura"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Tienes un nuevo mensaje"
        
        val prefs = com.mexadev.aura.core.preferences.PreferencesManager(applicationContext)
        val importanceStr = prefs.notificationImportance.lowercase()

        saveNotificationToDb(title, body, data)
        sendNotification(title, body, data, importanceStr)
    }
    
    private fun saveNotificationToDb(title: String, body: String, data: Map<String, String>) {
        val type = data["type"]
        val status = data["status"]
        val timestamp = data["timestamp"] ?: java.time.Instant.now().toString()
        val notificationId = data["id"] ?: java.util.UUID.randomUUID().toString()
        
        // Convert map to JSON string for payload
        val payloadJson = org.json.JSONObject(data).toString()
        
        val entity = com.mexadev.aura.data.local.entity.NotificationEntity().apply {
            this.notificationId = notificationId
            this.title = title
            this.body = body
            this.type = type
            this.status = status
            this.timestamp = timestamp
            this.isRead = false
            this.payloadJson = payloadJson
            
            this.message = data["message"]
            this.guestName = data["guest_name"]
            this.scannedCode = data["scanned_code"]
            this.deviceIdentifier = data["device_identifier"]
            this.accessType = data["access_type"]
            this.method = data["method"]
            this.validFrom = data["valid_from"]
            this.validUntil = data["valid_until"]
            this.uses = data["uses"]
            this.maxUses = data["max_uses"]
            this.activeDays = data["active_days"]
            this.startTime = data["start_time"]
            this.endTime = data["end_time"]
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.mexadev.aura.data.local.AuraDatabase.getDatabase(applicationContext)
                    .notificationDao()
                    .insertNotification(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>, importanceStr: String) {
        val intent = Intent(this, QuickActionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            for ((key, value) in data) {
                putExtra(key, value)
            }
            putExtra("notification_title", title)
            putExtra("notification_body", messageBody)
            putExtra("notification_importance", importanceStr)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId: String
        val channelName: String
        val importance: Int
        val priority: Int
        val vibratePattern: LongArray?

        when (importanceStr) {
            "low" -> {
                channelId = "aura_alerts_low"
                channelName = "Alertas Silenciosas"
                importance = NotificationManager.IMPORTANCE_LOW
                priority = NotificationCompat.PRIORITY_LOW
                vibratePattern = null
            }
            "normal" -> {
                channelId = "aura_alerts_normal"
                channelName = "Alertas Normales"
                importance = NotificationManager.IMPORTANCE_DEFAULT
                priority = NotificationCompat.PRIORITY_DEFAULT
                vibratePattern = null // Vibración del sistema por defecto
            }
            "high" -> {
                channelId = "aura_alerts_high_v2" // _v2 para asegurar que el sistema recree el canal con la vibración
                channelName = "Alertas de Seguridad (Críticas)"
                importance = NotificationManager.IMPORTANCE_HIGH
                priority = NotificationCompat.PRIORITY_HIGH
                vibratePattern = longArrayOf(0, 500, 200, 500, 200, 500) // Vibración fuerte y larga
            }
            else -> { // Default a High
                channelId = "aura_alerts_high_v2"
                channelName = "Alertas de Seguridad (Críticas)"
                importance = NotificationManager.IMPORTANCE_HIGH
                priority = NotificationCompat.PRIORITY_HIGH
                vibratePattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_aura_logo) // TODO: Cambiar por icono silueta (ic_stat_name)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (importanceStr != "low") {
            builder.setSound(defaultSoundUri)
        }
        if (vibratePattern != null) {
            builder.setVibrate(vibratePattern)
        }

        // Si es alta importancia, añadimos el FullScreenIntent para que despierte fuertemente
        if (importanceStr == "high" || importanceStr !in listOf("low", "normal")) {
            builder.setFullScreenIntent(pendingIntent, true)
            wakeUpScreen()
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Notificaciones de nivel: $importanceStr"
            if (vibratePattern != null) {
                enableVibration(true)
                vibrationPattern = vibratePattern
            } else if (importanceStr == "low") {
                enableVibration(false)
                setSound(null, null)
            }
        }
        notificationManager.createNotificationChannel(channel)

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())
    }

    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.SCREEN_DIM_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Aura::SecurityAlertWakeLock"
            )
            wakeLock.acquire(4000) // Despertar por 4 segundos
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
