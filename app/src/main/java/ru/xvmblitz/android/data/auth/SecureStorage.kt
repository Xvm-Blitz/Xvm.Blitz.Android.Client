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

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun loadAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun loadRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveLestaExpiresAt(value: String) {
        prefs.edit().putString(KEY_LESTA_EXPIRES_AT, value).apply()
    }

    fun loadLestaExpiresAt(): String? = prefs.getString(KEY_LESTA_EXPIRES_AT, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "xvm_blitz_secure"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_LESTA_EXPIRES_AT = "lesta_expires_at"
    }
}
