package com.mexadev.aura.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.fcm.resolver.NotificationImportance
import com.mexadev.aura.fcm.resolver.NotificationImportanceResolver
import com.mexadev.aura.ui.fcm.QuickActionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        
        CoroutineScope(Dispatchers.IO).launch {
            FcmHelper.syncToken(
                token, 
                sessionManager, 
                ApiClient.apiService
            )
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data

        // Extraemos título y cuerpo del payload
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Notificación de Aura"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Tienes un nuevo mensaje"
        
        saveNotificationToDb(title, body, data)

        // Resolver la importancia delegando la lógica a NotificationImportanceResolver (SOLID)
        val prefs = PreferencesManager(applicationContext)
        val resolver = NotificationImportanceResolver(prefs)
        val importance = resolver.resolveImportance(data)

        if (importance != NotificationImportance.OFF) {
            sendNotification(title, body, data, importance)
        }
    }
    
    private fun saveNotificationToDb(title: String, body: String, data: Map<String, String>) {
        val type = data["type"]
        val status = data["status"]
        val timestamp = data["timestamp"] ?: java.time.Instant.now().toString()
        val notificationId = data["id"] ?: java.util.UUID.randomUUID().toString()
        
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

    private fun sendNotification(
        title: String, 
        messageBody: String, 
        data: Map<String, String>, 
        importance: NotificationImportance
    ) {
        val intent = Intent(this, QuickActionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            for ((key, value) in data) {
                putExtra(key, value)
            }
            putExtra("notification_title", title)
            putExtra("notification_body", messageBody)
            putExtra("notification_importance", importance.key)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, importance.channelId)
            .setSmallIcon(R.drawable.ic_aura_logo)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(importance.priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (importance.playsSound) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(defaultSoundUri)
        }

        importance.vibrationPattern?.let { pattern ->
            builder.setVibrate(pattern)
        }

        if (importance.shouldWakeScreen) {
            builder.setFullScreenIntent(pendingIntent, true)
            wakeUpScreen()
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(importance.channelId, importance.channelName, importance.osImportance).apply {
            description = "Notificaciones de nivel: ${importance.key}"
            if (importance.vibrationPattern != null) {
                enableVibration(true)
                vibrationPattern = importance.vibrationPattern
            } else if (!importance.playsSound) {
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
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.SCREEN_DIM_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Aura::SecurityAlertWakeLock"
            )
            wakeLock.acquire(4000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
