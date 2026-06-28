package com.mexadev.aura.core.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow

class SessionManager(val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_session_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Flow for requesting biometric authentication from background threads (like TokenAuthenticator)
    val biometricRequestFlow = MutableSharedFlow<(Boolean) -> Unit>(extraBufferCapacity = 1)

    fun requestBiometricAuth(callback: (Boolean) -> Unit) {
        biometricRequestFlow.tryEmit(callback)
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("refresh_token", null)
    }

    fun clearSession() {
        sharedPreferences.edit { clear() }
    }
}
