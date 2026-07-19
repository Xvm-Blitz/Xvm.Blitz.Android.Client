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

    fun fromHttpException(exception: HttpException): String? {
        val code = exception.code()
        if (code !in 400..499) {
            return null
        }

        val response = exception.response()
        val body = runCatching { response?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val problemDetails = parseProblemDetails(body)
        val baseMessage = resolveBaseMessage(problemDetails)
            ?: AppAlertNotifier.fallbackMessageForStatus(code)

        val retryAfter = resolveRetryAfter(
            problemDetails = problemDetails,
            retryAfterHeader = response?.headers()?.get("Retry-After"),
        )
        val retryText = formatRetryAfter(retryAfter) ?: return baseMessage
        return "$baseMessage\n$retryText"
    }

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
        ).firstOrNull()
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
