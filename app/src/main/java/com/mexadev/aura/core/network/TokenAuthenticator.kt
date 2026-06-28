package com.mexadev.aura.core.network

import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.data.model.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.CountDownLatch
import com.mexadev.aura.core.preferences.PreferencesManager

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val apiServiceLazy: Lazy<ApiService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops if refresh returns 401
        if (response.request.url.encodedPath.contains("auth/refresh")) {
            sessionManager.clearSession()
            return null
        }

        synchronized(this) {
            val refreshToken = sessionManager.getRefreshToken() ?: return null

            // Check if biometric is required for session renewal
            val prefs = PreferencesManager(sessionManager.context)
            if (prefs.biometricSessionRenewal) {
                val latch = CountDownLatch(1)
                var biometricSuccess = false
                
                // Request UI to show prompt
                sessionManager.requestBiometricAuth { success ->
                    biometricSuccess = success
                    latch.countDown()
                }
                
                // Block this OkHttp network thread until the user completes the prompt
                try {
                    latch.await()
                } catch (_: InterruptedException) {
                    return null
                }
                
                if (!biometricSuccess) {
                    sessionManager.clearSession()
                    return null
                }
            }

            val newAccessToken = try {
                runBlocking {
                    val refreshResponse = apiServiceLazy.value.refresh(RefreshRequest(refreshToken))
                    
                    if (refreshResponse.isSuccessful) {
                        val body = refreshResponse.body()
                        if (body != null) {
                            sessionManager.saveTokens(body.accessToken, refreshToken)
                            body.accessToken
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }

            return if (newAccessToken != null) {
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            } else {
                sessionManager.clearSession()
                null
            }
        }
    }
}
