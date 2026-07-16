package ru.xvmblitz.android.data.api

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface StatisticsApi {
    @Multipart
    @POST("v1/battles/statistics")
    suspend fun getBattleStatistics(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Part file: MultipartBody.Part,
    ): BattleStatisticsDto
}

interface UsageApi {
    @GET("v1/api_keys/usage")
    suspend fun getUsage(
        @Header("X-Xvm-Api-Key") apiKey: String,
    ): GetUsageResponseDto
}

interface UpdatesApi {
    @GET("v1/updates/versions")
    suspend fun getLatestVersion(
        @Query("current_version") currentVersion: String,
        @Query("platform") platform: ClientPlatform,
    ): GetAppUpdateResponseDto
}
