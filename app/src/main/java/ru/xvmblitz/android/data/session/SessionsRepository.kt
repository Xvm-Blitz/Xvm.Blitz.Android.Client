package ru.xvmblitz.android.data.session

import retrofit2.HttpException
import ru.xvmblitz.android.data.api.CreateSessionRequestDto
import ru.xvmblitz.android.data.api.EndSessionRequestDto
import ru.xvmblitz.android.data.api.SessionAggregatedStatisticsDto
import ru.xvmblitz.android.data.api.SessionExtendedStatisticsDto
import ru.xvmblitz.android.data.api.SessionsApi
import ru.xvmblitz.android.data.auth.AuthRepository
import ru.xvmblitz.android.data.auth.SecureStorage
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.util.HttpErrorMessages

class SessionsRepository(
    private var sessionsApi: SessionsApi,
    private val authRepository: AuthRepository,
    private val secureStorage: SecureStorage,
) {
    fun updateApi(api: SessionsApi) {
        sessionsApi = api
    }
    suspend fun loadSecretKey(): String? = secureStorage.loadSessionSecretKey()

    suspend fun saveSecretKey(secretKey: String) {
        secureStorage.saveSessionSecretKey(secretKey)
    }

    suspend fun create(nickname: String, secretKey: String): Result<String> =
        runCatching {
            sessionsApi.create(requireApiKey(), CreateSessionRequestDto(nickname, secretKey)).id
        }.recoverCatching { exception ->
            throw Exception(resolveCreateError(exception), exception)
        }

    suspend fun restore(
        nickname: String,
        secretKey: String,
        page: Int,
        pageSize: Int,
    ): Result<RestoreSessionsResult> =
        runCatching {
            val response = sessionsApi.restore(requireApiKey(), nickname, secretKey, page, pageSize)
            RestoreSessionsResult(
                sessions = response.sessions,
                page = response.page,
                totalCount = response.totalCount,
            )
        }.recoverCatching { exception ->
            throw Exception(resolveError(exception), exception)
        }

    suspend fun getExtendedStatistics(sessionId: String): Result<SessionExtendedStatisticsDto> =
        runCatching {
            val items = sessionsApi.getExtendedStatistics(requireApiKey(), sessionId)
            items.firstOrNull() ?: error("Сессия не найдена")
        }.recoverCatching { exception ->
            throw Exception(resolveSessionStatisticsError(exception), exception)
        }

    suspend fun getAggregatedStatistics(sessionId: String): Result<SessionAggregatedStatisticsDto> =
        runCatching {
            val items = sessionsApi.getAggregatedStatistics(requireApiKey(), sessionId)
            items.firstOrNull() ?: error("Сессия не найдена")
        }.recoverCatching { exception ->
            throw Exception(resolveSessionStatisticsError(exception), exception)
        }

    suspend fun end(sessionId: String, secretKey: String): Result<Unit> =
        runCatching {
            sessionsApi.end(requireApiKey(), sessionId, EndSessionRequestDto(secretKey))
        }.recoverCatching { exception ->
            throw Exception(resolveError(exception), exception)
        }

    private fun resolveSessionStatisticsError(exception: Throwable): String {
        val httpException = exception as? HttpException
        if (httpException != null) {
            return HttpErrorMessages.fromHttpException(httpException, includeRetryAfter = false)
                ?: HttpErrorMessages.fallbackMessageForSessionStatistics(httpException.code())
        }
        return exception.message ?: "Не удалось выполнить запрос"
    }

    private fun requireApiKey(): String =
        authRepository.getApiKeyOrNull() ?: error(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)

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
