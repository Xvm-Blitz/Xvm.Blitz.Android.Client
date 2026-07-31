package ru.xvmblitz.android.data.auth

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.OffsetDateTime

class AuthRepository(private val secureStorage: SecureStorage) {
    private val _accessToken = MutableStateFlow(secureStorage.loadAccessToken())
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow(secureStorage.loadRefreshToken())
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    val isAuthorized: Boolean
        get() = !_accessToken.value.isNullOrBlank()

    fun getAccessToken(): String? = _accessToken.value?.takeIf { it.isNotBlank() }

    fun getRefreshToken(): String? = _refreshToken.value?.takeIf { it.isNotBlank() }

    fun getLestaExpiresAt(): String? = secureStorage.loadLestaExpiresAt()?.takeIf { it.isNotBlank() }

    fun getExpiresAtEpochMs(): Long? =
        secureStorage.loadExpiresAtEpochMs()
            ?: getAccessToken()?.let { readJwtExpiryEpochMs(it) }

    fun getLestaAccountId(): Long? {
        val token = getAccessToken() ?: return null
        val payloadJson = decodeJwtPayload(token) ?: return null
        return runCatching {
            val element = Json.parseToJsonElement(payloadJson)
            val obj = element as? JsonObject ?: return null
            val claim = obj["lesta_account_id"]?.jsonPrimitive ?: return null
            claim.longOrNull ?: claim.content.toLongOrNull()
        }.getOrNull()?.takeIf { it > 0L }
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        lestaExpiresAt: String?,
        expiresAt: String? = null,
    ): Boolean {
        val access = accessToken.trim()
        val refresh = refreshToken.trim()
        if (access.isEmpty() || refresh.isEmpty()) {
            return false
        }
        secureStorage.saveAccessToken(access)
        secureStorage.saveRefreshToken(refresh)
        if (!lestaExpiresAt.isNullOrBlank()) {
            secureStorage.saveLestaExpiresAt(lestaExpiresAt.trim())
        }
        val expiresAtEpochMs = parseExpiresAtEpochMs(expiresAt) ?: readJwtExpiryEpochMs(access)
        if (expiresAtEpochMs != null) {
            secureStorage.saveExpiresAtEpochMs(expiresAtEpochMs)
        } else {
            secureStorage.clearExpiresAtEpochMs()
        }
        _accessToken.value = access
        _refreshToken.value = refresh
        return true
    }

    fun clear() {
        secureStorage.clear()
        _accessToken.value = null
        _refreshToken.value = null
    }

    fun parseExpiresAtEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { value ->
            return if (value < 10_000_000_000L) value * 1000L else value
        }
        return runCatching {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun readJwtExpiryEpochMs(accessToken: String): Long? {
        val payloadJson = decodeJwtPayload(accessToken) ?: return null
        return runCatching {
            val element = Json.parseToJsonElement(payloadJson)
            val obj = element as? JsonObject ?: return null
            val exp = obj["exp"]?.jsonPrimitive?.longOrNull ?: return null
            exp * 1000L
        }.getOrNull()
    }

    private fun decodeJwtPayload(jwt: String): String? {
        val parts = jwt.split('.')
        if (parts.size < 2) {
            return null
        }
        var payload = parts[1]
        val padding = (4 - payload.length % 4) % 4
        if (padding > 0) {
            payload += "=".repeat(padding)
        }
        return runCatching {
            String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrNull()
    }
}
