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

        val parsed = readProblem(exception)
        val baseMessage = resolveUserMessage(parsed.problemDetails, code, includeRetryAfter)
            ?: return null

        if (!includeRetryAfter) {
            return baseMessage
        }

        val retryText = formatRetryAfter(parsed.retryAfter) ?: return baseMessage
        return "$baseMessage\n$retryText"
    }

    fun fallbackMessageForSessionStatistics(code: Int): String =
        when (code) {
            401 -> AppAlertNotifier.DEFAULT_AUTH_MESSAGE
            403 -> "Расширенная статистика недоступна для пробного аккаунта"
            else -> "Не удалось получить статистику сессии"
        }

    fun fromHttpExceptionForSessionCreate(exception: HttpException): String? {
        val code = exception.code()
        if (code !in 400..499) {
            return null
        }

        val parsed = readProblem(exception)
        parsed.retryAfter?.let { retryAfter ->
            val now = OffsetDateTime.now()
            if (retryAfter.isAfter(now)) {
                val remainingSeconds = Duration.between(now, retryAfter).toSeconds().coerceAtLeast(1L)
                return sessionCreateRateLimitMessage(remainingSeconds)
            }
            return sessionCreateRateLimitMessage(0)
        }

        return resolveUserMessage(parsed.problemDetails, code, includeRetryAfter = true)
            ?: AppAlertNotifier.fallbackMessageForStatus(code)
    }

    fun retryAfterSeconds(exception: HttpException): Long? {
        val parsed = readProblem(exception)
        val retryAfter = parsed.retryAfter ?: return null
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

    private fun readProblem(exception: HttpException): ParsedProblem {
        val response = exception.response()
        val body = runCatching { response?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val problemDetails = parseProblemDetails(body)
        val retryAfter = resolveRetryAfter(
            problemDetails = problemDetails,
            retryAfterHeader = response?.headers()?.get("Retry-After"),
        )
        return ParsedProblem(problemDetails, retryAfter, body)
    }

    private fun resolveUserMessage(
        problemDetails: ProblemDetailsDto?,
        statusCode: Int,
        includeRetryAfter: Boolean,
    ): String? {
        val fromType = messageForProblemType(problemDetails?.type)
        val fromFields = resolveBaseMessage(problemDetails)
        val preferred = when {
            !fromFields.isNullOrBlank() && !isGenericDeniedMessage(fromFields) -> fromFields
            !fromType.isNullOrBlank() -> fromType
            !fromFields.isNullOrBlank() -> fromFields
            else -> null
        }
        if (!preferred.isNullOrBlank()) {
            return preferred
        }
        return if (includeRetryAfter) {
            AppAlertNotifier.fallbackMessageForStatus(statusCode)
        } else {
            fallbackMessageForSessionStatistics(statusCode)
        }
    }

    private fun messageForProblemType(type: String?): String? {
        val normalized = type?.substringAfterLast('/')?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return null
        }
        return when (normalized) {
            "OpenIdMissing", "OpenIdAccountNotFound", "AccessNotFound" ->
                AppAlertNotifier.DEFAULT_AUTH_MESSAGE
            "QuotaExceeded" ->
                "Квота запросов превышена"
            "TestRateLimited" ->
                "Тестовый доступ можно использовать не чаще заданного интервала"
            "TrialAccessNotAllowed" ->
                "Расширенная статистика недоступна для пробного аккаунта"
            else -> null
        }
    }

    private fun isGenericDeniedMessage(message: String): Boolean {
        val normalized = message.trim().trimEnd('.')
        return normalized.equals("Запрос отклонён", ignoreCase = true) ||
            normalized.equals("Request denied", ignoreCase = true)
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
            .replace(Regex("(?i)Bad Request"), "Некорректный запрос")
            .replace(Regex("(?i)Request denied"), "Запрос отклонён")
            .replace(Regex("(?i)Quota exceeded"), "Квота запросов превышена")
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

    private data class ParsedProblem(
        val problemDetails: ProblemDetailsDto?,
        val retryAfter: OffsetDateTime?,
        val rawBody: String,
    )
}
