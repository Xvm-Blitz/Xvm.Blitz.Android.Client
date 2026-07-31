package ru.xvmblitz.android.data.session

import retrofit2.HttpException
import ru.xvmblitz.android.data.api.SessionAggregatedStatisticsDto
import ru.xvmblitz.android.data.api.SessionExtendedStatisticsDto
import ru.xvmblitz.android.data.api.SessionsApi
import ru.xvmblitz.android.data.auth.AuthRepository
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.util.HttpErrorMessages

class SessionsRepository(
    private var sessionsApi: SessionsApi,
    private val authRepository: AuthRepository,
) {
    fun updateApi(api: SessionsApi) {
        sessionsApi = api
    }

    suspend fun create(): Result<String> =
        runCatching {
            requireAuthorized()
            sessionsApi.create().id
        }.recoverCatching { exception ->
            throw Exception(resolveCreateError(exception), exception)
        }

    suspend fun restore(
        page: Int,
        pageSize: Int,
    ): Result<RestoreSessionsResult> =
        runCatching {
            requireAuthorized()
            val response = sessionsApi.restore(page, pageSize)
            RestoreSessionsResult(
                sessions = response.sessions,
                page = response.page,
                totalCount = response.totalCount,
            )
        }.recoverCatching { exception ->
            throw Exception(resolveError(exception), exception)
        }

    suspend fun getExtendedStatistics(
        sessionId: String,
        page: Int,
        pageSize: Int,
    ): Result<SessionExtendedStatisticsDto> =
        runCatching {
            requireAuthorized()
            val items = sessionsApi.getExtendedStatistics(sessionId, page, pageSize)
            items.firstOrNull() ?: error("Сессия не найдена")
        }.recoverCatching { exception ->
            throw Exception(resolveSessionStatisticsError(exception), exception)
        }

    suspend fun getAggregatedStatistics(sessionId: String): Result<SessionAggregatedStatisticsDto> =
        runCatching {
            requireAuthorized()
            val items = sessionsApi.getAggregatedStatistics(sessionId)
            items.firstOrNull() ?: error("Сессия не найдена")
        }.recoverCatching { exception ->
            throw Exception(resolveSessionStatisticsError(exception), exception)
        }

    suspend fun end(sessionId: String): Result<Unit> =
        runCatching {
            requireAuthorized()
            sessionsApi.end(sessionId)
        }.recoverCatching { exception ->
            throw Exception(resolveError(exception), exception)
        }

    private fun requireAuthorized() {
        if (!authRepository.isAuthorized) {
            error(AppAlertNotifier.DEFAULT_AUTH_MESSAGE)
        }
    }

    private fun resolveSessionStatisticsError(exception: Throwable): String {
        val httpException = exception as? HttpException
        if (httpException != null) {
            return HttpErrorMessages.fromHttpException(httpException, includeRetryAfter = false)
                ?: HttpErrorMessages.fallbackMessageForSessionStatistics(httpException.code())
        }
        return exception.message ?: "Не удалось выполнить запрос"
    }

    private fun resolveCreateError(exception: Throwable): String {
        val httpException = exception as? HttpException
        if (httpException != null) {
            return HttpErrorMessages.fromHttpExceptionForSessionCreate(httpException)
                ?: AppAlertNotifier.fallbackMessageForStatus(httpException.code())
        }
        return exception.message ?: "Не удалось выполнить запрос"
    }

    private fun resolveError(exception: Throwable): String {
        val httpException = exception as? HttpException
        if (httpException != null) {
            return HttpErrorMessages.fromHttpException(httpException)
                ?: AppAlertNotifier.fallbackMessageForStatus(httpException.code())
        }
        return exception.message ?: "Не удалось выполнить запрос"
    }

    data class RestoreSessionsResult(
        val sessions: List<ru.xvmblitz.android.data.api.RestoredSessionDto>,
        val page: Int,
        val totalCount: Int,
    )
}
