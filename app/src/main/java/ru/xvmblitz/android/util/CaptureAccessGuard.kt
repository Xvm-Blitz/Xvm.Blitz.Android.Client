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
        if (!container.authRepository.isAuthorized) {
            return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_AUTH_MESSAGE)
        }
        return try {
            val usage = container.usageApi.getUsage()
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
            val message = HttpErrorMessages.fromHttpException(httpException)
            return when (httpException.code()) {
                401, 403 -> CaptureAccessResult.Denied(
                    message ?: AppAlertNotifier.DEFAULT_AUTH_MESSAGE,
                )
                400, 402, 429 -> {
                    val resolved = message.orEmpty()
                    if (
                        resolved.contains("квот", ignoreCase = true) ||
                        resolved.contains("quota", ignoreCase = true) ||
                        resolved.contains("тестовый доступ", ignoreCase = true) ||
                        httpException.code() in setOf(402, 429)
                    ) {
                        CaptureAccessResult.Denied(
                            message ?: AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE,
                        )
                    } else if (!message.isNullOrBlank()) {
                        CaptureAccessResult.Denied(message)
                    } else {
                        null
                    }
                }
                else -> message?.let(CaptureAccessResult::Denied)
            }
        }
        val message = exception.message.orEmpty().lowercase()
        if (message.contains("quota") || message.contains("квот") || message.contains("limit exceeded")) {
            return CaptureAccessResult.Denied(AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE)
        }
        if (
            message.contains("unauthorized") ||
            message.contains("openid") ||
            message.contains("авториз") ||
            message.contains("войти")
        ) {
            return CaptureAccessResult.Denied(AppAlertNotifier.DEFAULT_AUTH_MESSAGE)
        }
        return null
    }

    private fun isPeriodExpired(periodEndRaw: String): Boolean {
        val periodEnd = runCatching { OffsetDateTime.parse(periodEndRaw) }.getOrNull() ?: return false
        return periodEnd.isBefore(OffsetDateTime.now())
    }
}
