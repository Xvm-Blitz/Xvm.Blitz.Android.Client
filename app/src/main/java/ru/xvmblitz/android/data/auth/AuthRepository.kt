package ru.xvmblitz.android.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(private val secureStorage: SecureStorage) {
    private val _apiKey = MutableStateFlow(secureStorage.loadApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    val isAuthorized: Boolean
        get() = !_apiKey.value.isNullOrBlank()

    fun getApiKeyOrNull(): String? = _apiKey.value?.takeIf { it.isNotBlank() }

    fun saveApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        secureStorage.saveApiKey(trimmed)
        _apiKey.value = trimmed
        return true
    }

    fun logout() {
        secureStorage.clear()
        _apiKey.value = null
    }
}
