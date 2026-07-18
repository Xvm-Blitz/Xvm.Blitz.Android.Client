package ru.xvmblitz.android.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.data.ApiDefaults
import ru.xvmblitz.android.data.api.GetUsageResponseDto
import ru.xvmblitz.android.ui.components.AdaptiveButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    isAuthorized: Boolean,
    usage: GetUsageResponseDto?,
    usageError: String?,
    usageUpdatedAtEpochMs: Long?,
    isUsageLoading: Boolean,
    onBack: () -> Unit,
    onAuthorize: (apiKey: String, apiBaseUrl: String?, onResult: (Result<Unit>) -> Unit) -> Unit,
    onAuthorized: () -> Unit,
    onLogout: () -> Unit,
    onRefreshUsage: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Авторизация и квота") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (isAuthorized) {
            LaunchedEffect(Unit) {
                onRefreshUsage()
            }
            AuthorizedQuotaContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                usage = usage,
                usageError = usageError,
                usageUpdatedAtEpochMs = usageUpdatedAtEpochMs,
                isUsageLoading = isUsageLoading,
                onLogout = onLogout,
            )
        } else {
            LoginContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                isSubmitting = isUsageLoading,
                onAuthorize = onAuthorize,
                onAuthorized = onAuthorized,
            )
        }
    }
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    isSubmitting: Boolean,
    onAuthorize: (apiKey: String, apiBaseUrl: String?, onResult: (Result<Unit>) -> Unit) -> Unit,
    onAuthorized: () -> Unit,
) {
    val container = XvmBlitzApp.instance.container
    var apiKey by remember { mutableStateOf("") }
    var apiBaseUrl by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) container.apiBaseUrl else ApiDefaults.BASE_URL,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val busy = loading || isSubmitting

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Введите API ключ",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ключ нужен для доступа к статистике и учёта квоты",
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
                enabled = !busy,
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
            enabled = !busy,
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true
                error = null
                onAuthorize(
                    apiKey,
                    if (BuildConfig.DEBUG) apiBaseUrl else null,
                ) { result ->
                    result
                        .onSuccess {
                            loading = false
                            onAuthorized()
                        }
                        .onFailure { exception ->
                            loading = false
                            error = exception.message ?: "Неверный ключ или ошибка сети"
                        }
                }
            },
            enabled = !busy && apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (busy) {
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

@Composable
private fun AuthorizedQuotaContent(
    modifier: Modifier = Modifier,
    usage: GetUsageResponseDto?,
    usageError: String?,
    usageUpdatedAtEpochMs: Long?,
    isUsageLoading: Boolean,
    onLogout: () -> Unit,
) {
    var showChangeKeyConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Статистика использования",
            style = MaterialTheme.typography.titleLarge,
        )

        if (isUsageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (usage != null) {
            val remaining = (usage.totalLimit - usage.currentUsage).coerceAtLeast(0)
            val progress = if (usage.totalLimit == 0) {
                0f
            } else {
                usage.currentUsage.toFloat() / usage.totalLimit.toFloat()
            }
            val usagePercent = (progress * 100f).toInt()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Остаток", style = MaterialTheme.typography.titleMedium)
                    Text("Использовано: ${usage.currentUsage} из ${usage.totalLimit} ($usagePercent%)")
                    Text("Осталось: $remaining")
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Период действия", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${formatUsageDate(usage.periodStart)} — ${formatUsageDate(usage.periodEnd)}",
                    )
                    remainingPeriodText(usage.periodEnd)?.let { remainingPeriod ->
                        Text(
                            text = remainingPeriod,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }

            if (usagePercent >= 95) {
                Text(
                    text = "Использовано более чем на 95%.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (usagePercent >= 80) {
                Text(
                    text = "Использовано более чем на 80%.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!isUsageLoading) {
            Text(usageError ?: "Информация об использовании отсутствует")
        }

        formatUsageUpdatedAt(usageUpdatedAtEpochMs)?.let { updatedAtText ->
            Text(
                text = updatedAtText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }

        AdaptiveButton(
            text = "Сменить API ключ",
            onClick = { showChangeKeyConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showChangeKeyConfirm) {
        AlertDialog(
            onDismissRequest = { showChangeKeyConfirm = false },
            title = { Text("Подтверждение смены API ключа") },
            text = {
                Text(
                    "Вы уверены, что хотите сменить API ключ? Текущий ключ будет утерян, если Вы предварительно не сохранили его.",
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = { showChangeKeyConfirm = false }) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            showChangeKeyConfirm = false
                            onLogout()
                        },
                    ) {
                        Text("Продолжить")
                    }
                }
            },
        )
    }
}

private val usageDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val usageUpdatedAtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

private fun formatUsageUpdatedAt(epochMs: Long?): String? {
    if (epochMs == null) {
        return null
    }
    val formatted = Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(usageUpdatedAtFormatter)
    return "Обновлено: $formatted"
}

private fun formatUsageDate(raw: String): String {
    return runCatching {
        OffsetDateTime.parse(raw).format(usageDateFormatter)
    }.recoverCatching {
        raw.take(10).let { datePart ->
            val parts = datePart.split("-")
            if (parts.size == 3) {
                "${parts[2]}.${parts[1]}.${parts[0]}"
            } else {
                datePart
            }
        }
    }.getOrDefault(raw)
}

private fun remainingPeriodText(periodEndRaw: String): String? {
    val periodEnd = runCatching { OffsetDateTime.parse(periodEndRaw) }.getOrNull() ?: return null
    val remaining = ChronoUnit.MINUTES.between(OffsetDateTime.now(), periodEnd)
    if (remaining <= 0) {
        return "Период истёк"
    }
    val days = remaining / (60 * 24)
    val hours = remaining / 60
    return when {
        days >= 1 -> "Осталось дней: $days"
        hours >= 1 -> "Осталось часов: $hours"
        else -> "Осталось минут: $remaining"
    }
}
