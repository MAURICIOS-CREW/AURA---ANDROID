package com.mexadev.aura.core.session

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class CryptoManager {

    private lateinit var aead: Aead

    fun init(context: Context) {
        AeadConfig.register()

        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "aura_keyset", "secure_session_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://aura_master_key")
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(data: String): String {
        val encrypted = aead.encrypt(data.toByteArray(), null)
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    fun decrypt(encryptedData: String): String? {
        return try {
            val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
            val decrypted = aead.decrypt(decoded, null)
            String(decrypted)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
