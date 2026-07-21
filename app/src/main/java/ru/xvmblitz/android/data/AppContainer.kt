package ru.xvmblitz.android.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.api.StatisticsApi
import ru.xvmblitz.android.data.api.UsageApi
import ru.xvmblitz.android.data.auth.AuthRepository
import ru.xvmblitz.android.data.auth.SecureStorage
import ru.xvmblitz.android.data.settings.SettingsRepository
import ru.xvmblitz.android.domain.BattleStatisticsStore
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
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
