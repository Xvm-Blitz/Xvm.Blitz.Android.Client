package ru.xvmblitz.android.util

import retrofit2.HttpException
import ru.xvmblitz.android.data.AppContainer
import java.time.OffsetDateTime

sealed interface CaptureAccessResult {
    data object Allowed : CaptureAccessResult
    data class Denied(val message: String) : CaptureAccessResult
}

object CaptureAccessGuard {
    suspend fun check(container: AppContainer): CaptureAccessResult {
        val apiKey = container.authRepository.getApiKeyOrNull()
        if (apiKey.isNullOrBlank()) {
            return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)
        }
        return try {
            val usage = container.usageApi.getUsage(apiKey)
            if (usage.currentUsage >= usage.totalLimit) {
                CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
            } else if (isPeriodExpired(usage.periodEnd)) {
                CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
            } else {
                CaptureAccessResult.Allowed
            }
        } catch (exception: Exception) {
            classifyError(exception) ?: CaptureAccessResult.Allowed
        }
    }

    fun classifyError(exception: Throwable): CaptureAccessResult.Denied? {
        val httpException = exception as? HttpException
        if (httpException != null) {
            when (httpException.code()) {
                401, 403 -> return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)
                402, 429 -> return CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
            }
            val body = runCatching { httpException.response()?.errorBody()?.string().orEmpty() }
                .getOrDefault("")
                .lowercase()
            if (body.contains("quota") ||
                body.contains("limit") ||
                body.contains("квот") ||
                body.contains("usage")
            ) {
                return CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
            }
            if (body.contains("api key") ||
                body.contains("api_key") ||
                body.contains("unauthorized") ||
                body.contains("ключ")
            ) {
                return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)
            }
        }
        val message = exception.message.orEmpty().lowercase()
        if (message.contains("quota") || message.contains("квот") || message.contains("limit exceeded")) {
            return CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
        }
        if (message.contains("api key") || message.contains("unauthorized") || message.contains("ключ")) {
            return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)
        }
        return null
    }

    private fun isPeriodExpired(periodEndRaw: String): Boolean {
        val periodEnd = runCatching { OffsetDateTime.parse(periodEndRaw) }.getOrNull() ?: return false
        return periodEnd.isBefore(OffsetDateTime.now())
    }
}
