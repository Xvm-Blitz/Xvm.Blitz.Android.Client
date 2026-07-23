package ru.xvmblitz.android.util

import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.xvmblitz.android.data.api.ProblemDetailsDto

object HttpErrorMessages {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromHttpException(
        exception: HttpException,
        includeRetryAfter: Boolean = true,
    ): String? {
        val code = exception.code()
        if (code !in 400..499) {
            return null
        }

        val response = exception.response()
        val body = runCatching { response?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val problemDetails = parseProblemDetails(body)
        val baseMessage = resolveBaseMessage(problemDetails)
            ?: if (includeRetryAfter) {
                AppAlertNotifier.fallbackMessageForStatus(code)
            } else {
                fallbackMessageForSessionStatistics(code)
            }

        if (!includeRetryAfter) {
            return baseMessage
        }

        val retryAfter = resolveRetryAfter(
            problemDetails = problemDetails,
            retryAfterHeader = response?.headers()?.get("Retry-After"),
        )
        val retryText = formatRetryAfter(retryAfter) ?: return baseMessage
        return "$baseMessage\n$retryText"
    }

    fun fallbackMessageForSessionStatistics(code: Int): String =
        when (code) {
            401 -> AppAlertNotifier.DEFAULT_API_KEY_MESSAGE
            403 -> AppAlertNotifier.REQUEST_DENIED_MESSAGE
            else -> AppAlertNotifier.REQUEST_DENIED_MESSAGE
        }

    fun fromHttpExceptionForSessionCreate(exception: HttpException): String? {
        val code = exception.code()
        if (code !in 400..499) {
            return null
        }

        retryAfterSeconds(exception)?.let { remainingSeconds ->
            return sessionCreateRateLimitMessage(remainingSeconds)
        }

        val response = exception.response()
        val body = runCatching { response?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val problemDetails = parseProblemDetails(body)
        return resolveBaseMessage(problemDetails)
            ?: AppAlertNotifier.fallbackMessageForStatus(code)
    }

    fun retryAfterSeconds(exception: HttpException): Long? {
        val response = exception.response() ?: return null
        val body = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        val problemDetails = parseProblemDetails(body)
        val retryAfter = resolveRetryAfter(
            problemDetails = problemDetails,
            retryAfterHeader = response.headers()["Retry-After"],
        ) ?: return null
        val now = OffsetDateTime.now()
        if (!retryAfter.isAfter(now)) {
            return 0L
        }
        return Duration.between(now, retryAfter).toSeconds().coerceAtLeast(1L)
    }

    fun sessionCreateRateLimitMessage(remainingSeconds: Long): String =
        "Сессия не может быть создана. ${formatRateLimitRetryText(remainingSeconds)}"

    fun quotaRateLimitMessage(remainingSeconds: Long): String =
        "${AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE} ${formatRateLimitRetryText(remainingSeconds)}"

    fun formatRateLimitRetryText(remainingSeconds: Long): String =
        if (remainingSeconds <= 0) {
            "Можно повторить сейчас"
        } else {
            "Повторите через $remainingSeconds секунд"
        }

    fun resolveRateLimitSeconds(exception: Throwable): Long? {
        (exception.cause as? HttpException)?.let(::retryAfterSeconds)?.let { return it }
        return parseRateLimitSecondsFromMessage(exception.message)
    }

    fun parseRateLimitSecondsFromMessage(message: String?): Long? =
        Regex("Повторите через (\\d+) секунд")
            .find(message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    fun parseProblemDetails(body: String): ProblemDetailsDto? {
        if (body.isBlank()) {
            return null
        }
        return runCatching { json.decodeFromString<ProblemDetailsDto>(body) }.getOrNull()
    }

    private fun resolveBaseMessage(problemDetails: ProblemDetailsDto?): String? {
        if (problemDetails == null) {
            return null
        }
        return listOfNotNull(
            problemDetails.detail?.takeIf(String::isNotBlank),
            problemDetails.error?.takeIf(String::isNotBlank),
            problemDetails.title?.takeIf(String::isNotBlank),
            problemDetails.reason?.takeIf(String::isNotBlank),
        ).firstOrNull()?.let(::sanitizeUserMessage)
    }

    private fun sanitizeUserMessage(message: String): String {
        val cleaned = message
            .replace(Regex("(?i)X-Xvm-Api-Key"), "")
            .replace(Regex("(?i)api ключ в заголовке\\s*"), "API ключ ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (cleaned.isEmpty()) {
            return message
        }
        return cleaned.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    private fun resolveRetryAfter(
        problemDetails: ProblemDetailsDto?,
        retryAfterHeader: String?,
    ): OffsetDateTime? {
        problemDetails?.retryAfter
            ?.takeIf(String::isNotBlank)
            ?.let { raw ->
                runCatching { OffsetDateTime.parse(raw) }.getOrNull()
            }
            ?.let { return it }

        val header = retryAfterHeader?.trim().orEmpty()
        if (header.isEmpty()) {
            return null
        }
        header.toLongOrNull()?.let { seconds ->
            return OffsetDateTime.now().plusSeconds(seconds.coerceAtLeast(0L))
        }
        return runCatching { OffsetDateTime.parse(header) }.getOrNull()
    }

    private fun formatRetryAfter(retryAfter: OffsetDateTime?): String? {
        if (retryAfter == null) {
            return null
        }
        val now = OffsetDateTime.now()
        if (!retryAfter.isAfter(now)) {
            return "Можно повторить сейчас"
        }
        val remainingSeconds = Duration.between(now, retryAfter).toSeconds().coerceAtLeast(1L)
        return "Повторите через $remainingSeconds секунд"
    }
}
