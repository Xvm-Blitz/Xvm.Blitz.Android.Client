package ru.xvmblitz.android.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.api.OpenIdApi
import ru.xvmblitz.android.data.api.OpenIdAuthResponseDto
import ru.xvmblitz.android.data.api.OpenIdRefreshRequestDto
import ru.xvmblitz.android.data.api.SessionsApi
import ru.xvmblitz.android.data.api.StatisticsApi
import ru.xvmblitz.android.data.api.UpdatesApi
import ru.xvmblitz.android.data.api.UsageApi
import ru.xvmblitz.android.data.auth.AuthRepository
import ru.xvmblitz.android.data.auth.SecureStorage
import ru.xvmblitz.android.data.session.SessionsRepository
import ru.xvmblitz.android.data.settings.SettingsRepository
import ru.xvmblitz.android.domain.BattleSessionRuntimeService
import ru.xvmblitz.android.domain.BattleStatisticsStore
import ru.xvmblitz.android.domain.PresenceRuntimeService
import ru.xvmblitz.android.domain.SessionSummaryStore
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val appContext = context.applicationContext

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val secureStorage = SecureStorage(appContext)
    val authRepository = AuthRepository(secureStorage)
    val settingsRepository = SettingsRepository(appContext)
    val battleStatisticsStore = BattleStatisticsStore()
    val sessionSummaryStore = SessionSummaryStore()

    private val tokenLock = Any()
    private val jsonMediaType = "application/json".toMediaType()

    private val refreshHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
            ),
        )
        .build()

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
            ),
        )
        .addInterceptor { chain ->
            val original = chain.request()
            val token = authRepository.getAccessToken()
            val request = if (!token.isNullOrBlank() && !isOpenIdRefreshRequest(original)) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }
        .authenticator(TokenAuthenticator())
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    @Volatile
    var apiBaseUrl: String = runBlocking {
        if (BuildConfig.DEBUG) {
            settingsRepository.current().apiBaseUrl
        } else {
            ApiDefaults.BASE_URL
        }
    }
        private set

    @Volatile
    private var retrofit: Retrofit = createRetrofit(apiBaseUrl)

    @Volatile
    var statisticsApi: StatisticsApi = retrofit.create(StatisticsApi::class.java)
        private set

    @Volatile
    var usageApi: UsageApi = retrofit.create(UsageApi::class.java)
        private set

    @Volatile
    var openIdApi: OpenIdApi = retrofit.create(OpenIdApi::class.java)
        private set

    @Volatile
    var updatesApi: UpdatesApi = retrofit.create(UpdatesApi::class.java)
        private set

    @Volatile
    var sessionsApi: SessionsApi = retrofit.create(SessionsApi::class.java)
        private set

    val sessionsRepository = SessionsRepository(sessionsApi, authRepository)

    val battleSessionRuntimeService = BattleSessionRuntimeService(
        apiBaseUrlProvider = { apiBaseUrl },
        accessTokenProvider = { getValidAccessToken() },
    )

    val presenceRuntimeService = PresenceRuntimeService(
        apiBaseUrlProvider = { apiBaseUrl },
        accessTokenProvider = { getValidAccessToken() },
    )

    fun openIdLoginUrl(): String {
        val base = apiBaseUrl.trimEnd('/')
        return "$base/v1/auth/openid/login?client=Android"
    }

    fun setApiBaseUrl(baseUrl: String) {
        require(BuildConfig.DEBUG) { "Custom API base URL is only available in debug builds" }
        val normalized = ApiDefaults.normalizeBaseUrl(baseUrl)
        if (normalized == apiBaseUrl) {
            return
        }
        apiBaseUrl = normalized
        retrofit = createRetrofit(normalized)
        statisticsApi = retrofit.create(StatisticsApi::class.java)
        usageApi = retrofit.create(UsageApi::class.java)
        openIdApi = retrofit.create(OpenIdApi::class.java)
        updatesApi = retrofit.create(UpdatesApi::class.java)
        sessionsApi = retrofit.create(SessionsApi::class.java)
        sessionsRepository.updateApi(sessionsApi)
    }

    fun tryRefreshAccessToken(): Boolean {
        synchronized(tokenLock) {
            return refreshAccessTokenLocked()
        }
    }

    fun getValidAccessToken(): String? {
        synchronized(tokenLock) {
            val token = authRepository.getAccessToken() ?: return null
            if (!isAccessTokenExpiringSoon(token)) {
                return token
            }
            if (!refreshAccessTokenLocked()) {
                return null
            }
            return authRepository.getAccessToken()
        }
    }

    private fun isAccessTokenExpiringSoon(accessToken: String): Boolean {
        val exp = runCatching {
            val parts = accessToken.split('.')
            if (parts.size < 2) {
                return true
            }
            var payload = parts[1]
            val padding = (4 - payload.length % 4) % 4
            if (padding > 0) {
                payload += "=".repeat(padding)
            }
            val jsonPayload = String(
                android.util.Base64.decode(payload, android.util.Base64.URL_SAFE),
                Charsets.UTF_8,
            )
            val obj = json.parseToJsonElement(jsonPayload) as? JsonObject
            obj?.get("exp")?.jsonPrimitive?.longOrNull
        }.getOrNull() ?: return true
        val expiresAtMs = exp * 1000L
        return expiresAtMs - System.currentTimeMillis() <= 2 * 60 * 1000L
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory(jsonMediaType))
            .build()
    }

    private fun refreshAccessTokenLocked(): Boolean {
        val refreshToken = authRepository.getRefreshToken() ?: return false
        val url = "${apiBaseUrl.trimEnd('/')}/v1/auth/openid/refresh"
        val bodyJson = json.encodeToString(OpenIdRefreshRequestDto(refreshToken))
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        return runCatching {
            refreshHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    authRepository.clear()
                    return false
                }
                val payload = response.body?.string().orEmpty()
                val dto = json.decodeFromString<OpenIdAuthResponseDto>(payload)
                val access = dto.accessToken?.trim().orEmpty()
                val refresh = dto.refreshToken?.trim().orEmpty()
                if (access.isEmpty() || refresh.isEmpty()) {
                    authRepository.clear()
                    return false
                }
                authRepository.saveTokens(access, refresh, dto.lestaExpiresAt)
                true
            }
        }.getOrElse {
            false
        }
    }

    private fun isOpenIdRefreshRequest(request: Request): Boolean {
        return request.url.encodedPath.contains("/v1/auth/openid/refresh")
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private inner class TokenAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) >= 2) {
                return null
            }
            if (isOpenIdRefreshRequest(response.request)) {
                return null
            }
            synchronized(tokenLock) {
                val requestToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val currentToken = authRepository.getAccessToken()
                if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }
                if (!refreshAccessTokenLocked()) {
                    return null
                }
                val newToken = authRepository.getAccessToken() ?: return null
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }
        }
    }
}
