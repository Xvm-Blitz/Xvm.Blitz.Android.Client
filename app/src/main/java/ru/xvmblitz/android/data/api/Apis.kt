package ru.xvmblitz.android.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface StatisticsApi {
    @Multipart
    @POST("v1/battles/statistics")
    suspend fun getBattleStatistics(
        @Part file: MultipartBody.Part,
    ): BattleStatisticsDto
}

interface UsageApi {
    @GET("v1/auth/openid/usage")
    suspend fun getUsage(): GetUsageResponseDto
}

interface OpenIdApi {
    @POST("v1/auth/openid/refresh")
    suspend fun refresh(
        @Body request: OpenIdRefreshRequestDto,
    ): OpenIdAuthResponseDto

    @POST("v1/auth/openid/logout")
    suspend fun logout()
}

interface UpdatesApi {
    @GET("v1/releases")
    suspend fun getLatestVersion(
        @Query("current_version") currentVersion: String,
        @Query("platform") platform: ClientPlatform,
    ): GetAppUpdateResponseDto
}
