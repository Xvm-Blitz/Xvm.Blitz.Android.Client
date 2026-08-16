package ru.xvmblitz.android.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.data.ApiDefaults
import ru.xvmblitz.android.data.api.AccessType
import ru.xvmblitz.android.data.api.GetSubscriptionPublicPricingResponseDto
import ru.xvmblitz.android.data.api.GetSubscriptionUserPricingResponseDto
import ru.xvmblitz.android.data.api.GetUsageResponseDto
import ru.xvmblitz.android.ui.components.AdaptiveButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    isAuthorized: Boolean,
    usage: GetUsageResponseDto?,
    subscriptionPricing: GetSubscriptionUserPricingResponseDto?,
    publicSubscriptionPricing: GetSubscriptionPublicPricingResponseDto?,
    usageError: String?,
    paymentStatusMessage: String?,
    usageUpdatedAtEpochMs: Long?,
    isUsageLoading: Boolean,
    isPaymentCreating: Boolean,
    isPaymentPending: Boolean,
    onBack: () -> Unit,
    onPrepareDebugBaseUrl: (apiBaseUrl: String?, onResult: (Result<Unit>) -> Unit) -> Unit,
    onAuthorized: () -> Unit,
    onLogout: () -> Unit,
    onRefreshUsage: () -> Unit,
    onCreateSubscriptionPayment: (receiptEmail: String, onOpenPaymentUrl: (String) -> Unit) -> Unit,
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
                subscriptionPricing = subscriptionPricing,
                publicSubscriptionPricing = publicSubscriptionPricing,
                usageError = usageError,
                paymentStatusMessage = paymentStatusMessage,
                usageUpdatedAtEpochMs = usageUpdatedAtEpochMs,
                isUsageLoading = isUsageLoading,
                isPaymentCreating = isPaymentCreating,
                isPaymentPending = isPaymentPending,
                onLogout = onLogout,
                onCreateSubscriptionPayment = onCreateSubscriptionPayment,
            )
        } else {
            LoginContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                isSubmitting = isUsageLoading,
                onPrepareDebugBaseUrl = onPrepareDebugBaseUrl,
                onAuthorized = onAuthorized,
            )
        }
    }
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    isSubmitting: Boolean,
    onPrepareDebugBaseUrl: (apiBaseUrl: String?, onResult: (Result<Unit>) -> Unit) -> Unit,
    onAuthorized: () -> Unit,
) {
    val context = LocalContext.current
    val container = XvmBlitzApp.instance.container
    var apiBaseUrl by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) container.apiBaseUrl else ApiDefaults.BASE_URL,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val busy = loading || isSubmitting

    fun openOpenIdLogin() {
        loading = true
        error = null
        onPrepareDebugBaseUrl(
            if (BuildConfig.DEBUG) apiBaseUrl else null,
        ) { result ->
            result
                .onSuccess {
                    val loginUri = Uri.parse(container.openIdLoginUrl())
                    context.startActivity(Intent(Intent.ACTION_VIEW, loginUri))
                    loading = false
                    onAuthorized()
                }
                .onFailure { exception ->
                    loading = false
                    error = exception.message ?: "Не удалось открыть авторизацию"
                }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Вход через Lesta OpenID",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Авторизация нужна для доступа к статистике, сессиям и учёта квоты",
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

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { openOpenIdLogin() },
            enabled = !busy,
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
                    Text("Открытие…")
                }
            } else {
                Text("Войти через Lesta OpenID")
            }
        }
    }
}

@Composable
private fun AuthorizedQuotaContent(
    modifier: Modifier = Modifier,
    usage: GetUsageResponseDto?,
    subscriptionPricing: GetSubscriptionUserPricingResponseDto?,
    publicSubscriptionPricing: GetSubscriptionPublicPricingResponseDto?,
    usageError: String?,
    paymentStatusMessage: String?,
    usageUpdatedAtEpochMs: Long?,
    isUsageLoading: Boolean,
    isPaymentCreating: Boolean,
    isPaymentPending: Boolean,
    onLogout: () -> Unit,
    onCreateSubscriptionPayment: (receiptEmail: String, onOpenPaymentUrl: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var receiptEmail by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Аккаунт и подписка",
            style = MaterialTheme.typography.titleLarge,
        )

        if (isUsageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (usage != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Подписка", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatAccountType(usage),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(text = formatTariffPrice(usage, subscriptionPricing, publicSubscriptionPricing))
                    Text(text = formatSubscriptionStatus(subscriptionPricing, usage))
                }
            }
        }

        if (usage != null && (subscriptionPricing != null || publicSubscriptionPricing != null)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Оплата", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatPaymentPrice(subscriptionPricing, publicSubscriptionPricing),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = formatPaymentPeriod(subscriptionPricing, usage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    subscriptionPricing?.let { pricing ->
                        legacyPriceHint(pricing, publicSubscriptionPricing)?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = receiptEmail,
                        onValueChange = { receiptEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email для чека") },
                        singleLine = true,
                        enabled = !isPaymentCreating && !isPaymentPending,
                    )
                    Button(
                        onClick = {
                            onCreateSubscriptionPayment(receiptEmail) { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        enabled = !isPaymentCreating && !isPaymentPending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isPaymentCreating || isPaymentPending) {
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
                                Text(if (isPaymentCreating) "Создание…" else "Ожидание оплаты…")
                            }
                        } else {
                            Text("Оплатить 1 месяц")
                        }
                    }
                    paymentStatusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        if (usage != null && isFreeTariff(usage)) {
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
                        text = formatPeriodRange(usage.periodStart, usage.periodEnd),
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
        } else if (usage == null && !isUsageLoading) {
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
            text = "Выйти",
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Подтверждение выхода") },
            text = {
                Text("Вы уверены, что хотите выйти из аккаунта Lesta OpenID?")
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = { showLogoutConfirm = false }) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            showLogoutConfirm = false
                            onLogout()
                        },
                    ) {
                        Text("Выйти")
                    }
                }
            },
        )
    }
}

private fun formatAccountType(usage: GetUsageResponseDto?): String {
    return when (usage?.type) {
        AccessType.Free -> "Тариф: Бесплатный"
        AccessType.Trial -> "Тариф: Пробный период"
        AccessType.FullAccess -> "Тариф: Премиум"
        null -> "Тариф: -"
    }
}

private fun formatTariffPrice(
    usage: GetUsageResponseDto?,
    pricing: GetSubscriptionUserPricingResponseDto?,
    publicPricing: GetSubscriptionPublicPricingResponseDto?,
): String {
    if (isFreeTariff(usage) || usage?.type == AccessType.Trial) {
        return "0 ${formatSubscriptionCurrency(pricing?.currency ?: publicPricing?.currency ?: "RUB")} / мес"
    }

    if (pricing == null) {
        return ""
    }

    return "${pricing.amount.toInt()} ${formatSubscriptionCurrency(pricing.currency)} / мес"
}

private fun formatPaymentPrice(
    pricing: GetSubscriptionUserPricingResponseDto?,
    publicPricing: GetSubscriptionPublicPricingResponseDto?,
): String {
    pricing?.let {
        return "${it.amount.toInt()} ${formatSubscriptionCurrency(it.currency)} / мес"
    }

    publicPricing?.let {
        return "${it.amount.toInt()} ${formatSubscriptionCurrency(it.currency)} / мес"
    }

    return ""
}

private fun legacyPriceHint(
    pricing: GetSubscriptionUserPricingResponseDto,
    publicPricing: GetSubscriptionPublicPricingResponseDto?,
): String? {
    if (!pricing.isGrandfathered || pricing.legacyPriceUntil.isNullOrBlank() || publicPricing == null) {
        return null
    }
    if (publicPricing.amount == pricing.amount) {
        return null
    }
    return "Льготная цена действует до ${formatUsageDate(pricing.legacyPriceUntil)}. Затем - ${publicPricing.amount.toInt()} ${formatSubscriptionCurrency(publicPricing.currency)} / мес"
}

private fun isFreeTariff(usage: GetUsageResponseDto?): Boolean =
    usage?.type == AccessType.Free

private fun resolvePremiumUntil(usage: GetUsageResponseDto?, pricing: GetSubscriptionUserPricingResponseDto?): String? =
    usage?.premiumUntil?.takeIf { it.isNotBlank() } ?: pricing?.premiumUntil?.takeIf { it.isNotBlank() }

private fun isPremiumActive(
    pricing: GetSubscriptionUserPricingResponseDto?,
    usage: GetUsageResponseDto?,
): Boolean {
    val untilRaw = resolvePremiumUntil(usage, pricing)
    return when (usage?.type) {
        AccessType.Trial -> untilRaw != null && runCatching {
            OffsetDateTime.parse(untilRaw).isAfter(OffsetDateTime.now())
        }.getOrDefault(false)
        AccessType.FullAccess -> untilRaw.isNullOrBlank() || runCatching {
            OffsetDateTime.parse(untilRaw).isAfter(OffsetDateTime.now())
        }.getOrDefault(false)
        else -> false
    }
}

private fun formatSubscriptionStatus(
    pricing: GetSubscriptionUserPricingResponseDto?,
    usage: GetUsageResponseDto?,
): String {
    if (usage?.type == AccessType.Trial) {
        val until = resolvePremiumUntil(usage, pricing)
        return if (until.isNullOrBlank()) {
            "Пробный период"
        } else {
            "Пробный период до: ${formatUsageDate(until)}"
        }
    }

    if (isPremiumActive(pricing, usage)) {
        return formatPremiumUntil(resolvePremiumUntil(usage, pricing))
    }

    if (isFreeTariff(usage) && usage != null) {
        return "Квота будет обновлена после ${formatUsageDate(usage.periodEnd)}"
    }

    return formatPremiumUntil(resolvePremiumUntil(usage, pricing))
}

private fun formatPaymentPeriod(
    pricing: GetSubscriptionUserPricingResponseDto?,
    usage: GetUsageResponseDto?,
): String {
    if (isFreeTariff(usage) || usage?.type == AccessType.Trial) {
        val periodStart = LocalDate.now()
        val periodEnd = periodStart.plusMonths(1)
        return "Оплачиваемый период: ${formatPeriodRange(periodStart, periodEnd)}"
    }

    if (pricing == null) {
        return ""
    }

    return "Следующий оплачиваемый период: ${formatPeriodRange(pricing.nextPaymentPeriod.start, pricing.nextPaymentPeriod.end)}"
}

private fun formatPeriodRange(start: LocalDate, end: LocalDate): String =
    "${start.format(usageDateFormatter)}\u00A0-\u00A0${end.format(usageDateFormatter)}"

private fun formatPeriodRange(startRaw: String, endRaw: String): String =
    "${formatUsageDate(startRaw)}\u00A0-\u00A0${formatUsageDate(endRaw)}"

private fun formatPremiumUntil(premiumUntilRaw: String?): String {
    if (premiumUntilRaw.isNullOrBlank()) {
        return "Не оплачена"
    }
    return "Оплачена до: ${formatUsageDate(premiumUntilRaw)}"
}

private fun formatSubscriptionCurrency(currency: String): String {
    return if (currency.equals("RUB", ignoreCase = true)) "₽" else currency
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
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(usageDateFormatter)
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
