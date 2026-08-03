package com.mexadev.aura.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.mexadev.aura.core.network.ApiService
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.data.model.auth.FcmRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FcmHelper {

    /**
     * Obtiene el token de FCM de forma asíncrona suspendiendo la corrutina actual.
     * Encapsula la lógica del SDK de Firebase para cumplir con SOLID.
     */
    @Suppress("DEPRECATION")
    private suspend fun getFirebaseToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (continuation.isActive) {
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Guarda el token localmente e intenta sincronizarlo con el backend.
     */
    suspend fun syncToken(token: String, sessionManager: SessionManager, apiService: ApiService) {
        try {
            sessionManager.saveFcmToken(token)
            
            // Si hay sesión activa, lo mandamos al backend
            if (sessionManager.getAccessToken() != null) {
                val response = apiService.updateFcmToken(FcmRequest(token))
                sessionManager.setFcmTokenSynced(response.isSuccessful)
            } else {
                // No hay sesión, queda pendiente para cuando inicie sesión
                sessionManager.setFcmTokenSynced(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sessionManager.setFcmTokenSynced(false)
        }
    }

    /**
     * Extrae el token más reciente desde Firebase de forma asíncrona y lo sincroniza.
     */
    suspend fun fetchAndSyncToken(sessionManager: SessionManager, apiService: ApiService) {
        val token = getFirebaseToken()
        if (token != null) {
            syncToken(token, sessionManager, apiService)
        }
    }
}
