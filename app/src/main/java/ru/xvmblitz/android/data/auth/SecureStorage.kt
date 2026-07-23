package ru.xvmblitz.android.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun loadApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun saveSessionSecretKey(secretKey: String) {
        prefs.edit().putString(KEY_SESSION_SECRET, secretKey).apply()
    }

    fun loadSessionSecretKey(): String? = prefs.getString(KEY_SESSION_SECRET, null)

    companion object {
        private const val PREFS_NAME = "xvm_blitz_secure"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SESSION_SECRET = "session_secret_key"
    }
}
