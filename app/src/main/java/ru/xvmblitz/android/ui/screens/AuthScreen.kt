package ru.xvmblitz.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.data.ApiDefaults

@Composable
fun AuthScreen(
    onAuthorized: () -> Unit,
) {
    val container = XvmBlitzApp.instance.container
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(container.authRepository.getApiKeyOrNull().orEmpty()) }
    var apiBaseUrl by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) container.apiBaseUrl else ApiDefaults.BASE_URL,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "XVM Blitz",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Введите API ключ для доступа к статистике",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (BuildConfig.DEBUG) {
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = {
                    apiBaseUrl = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL (debug)") },
                singleLine = true,
                enabled = !loading,
                supportingText = {
                    Text("Только в debug/test сборке")
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API ключ") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !loading,
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        if (BuildConfig.DEBUG) {
                            val normalized = ApiDefaults.normalizeBaseUrl(apiBaseUrl)
                            if (!normalized.startsWith("https://")) {
                                error = "Base URL должен начинаться с https://"
                                return@launch
                            }
                            container.setApiBaseUrl(normalized)
                            container.settingsRepository.setApiBaseUrl(normalized)
                            apiBaseUrl = normalized
                        }

                        if (!container.authRepository.saveApiKey(apiKey)) {
                            error = "Ключ не может быть пустым"
                            return@launch
                        }
                        container.usageApi.getUsage(apiKey.trim())
                        onAuthorized()
                    } catch (exception: Exception) {
                        container.authRepository.logout()
                        error = exception.message ?: "Неверный ключ или ошибка сети"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading && apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Вход…")
                }
            } else {
                Text("Войти")
            }
        }
    }
}
