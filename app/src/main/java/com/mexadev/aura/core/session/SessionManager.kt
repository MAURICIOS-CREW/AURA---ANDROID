package com.mexadev.aura.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_session_datastore")

class SessionManager(val context: Context) {

    private val cryptoManager = CryptoManager().apply { init(context) }
    private val dataStore = context.dataStore

    companion object {
        val ACCESS_TOKEN_KEY  = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val FCM_TOKEN_KEY     = stringPreferencesKey("fcm_token")
        val FCM_SYNCED_KEY    = booleanPreferencesKey("is_fcm_synced")

        // Flow compartido entre todas las instancias para requerir biometría desde background threads
        val biometricRequestFlow = MutableSharedFlow<(Boolean) -> Unit>(extraBufferCapacity = 1)
    }

    fun requestBiometricAuth(callback: (Boolean) -> Unit) {
        biometricRequestFlow.tryEmit(callback)
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        val encAccess = cryptoManager.encrypt(accessToken)
        val encRefresh = cryptoManager.encrypt(refreshToken)
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = encAccess
            prefs[REFRESH_TOKEN_KEY] = encRefresh
        }
    }

    suspend fun getAccessToken(): String? {
        val encrypted = dataStore.data.map { it[ACCESS_TOKEN_KEY] }.firstOrNull() ?: return null
        return cryptoManager.decrypt(encrypted)
    }

    suspend fun getRefreshToken(): String? {
        val encrypted = dataStore.data.map { it[REFRESH_TOKEN_KEY] }.firstOrNull() ?: return null
        return cryptoManager.decrypt(encrypted)
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    suspend fun saveFcmToken(token: String) {
        dataStore.edit { prefs -> prefs[FCM_TOKEN_KEY] = token }
    }

    suspend fun getFcmToken(): String? {
        return dataStore.data.map { it[FCM_TOKEN_KEY] }.firstOrNull()
    }

    suspend fun setFcmTokenSynced(synced: Boolean) {
        dataStore.edit { prefs -> prefs[FCM_SYNCED_KEY] = synced }
    }

    suspend fun isFcmTokenSynced(): Boolean {
        return dataStore.data.map { it[FCM_SYNCED_KEY] ?: false }.firstOrNull() ?: false
    }
    
    // For synchronous access (like interceptors that already run in a background thread)
    fun getRefreshTokenSync(): String? = runBlocking { getRefreshToken() }
    fun getAccessTokenSync(): String? = runBlocking { getAccessToken() }
    fun clearSessionSync() = runBlocking { clearSession() }
    fun requestBiometricAuthSync(callback: (Boolean) -> Unit) = requestBiometricAuth(callback)
}
