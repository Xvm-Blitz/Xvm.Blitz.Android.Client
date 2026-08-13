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

interface SubscriptionApi {
    @GET("v1/subscriptions/pricing")
    suspend fun getPublicPricing(): GetSubscriptionPublicPricingResponseDto

    @GET("v1/subscriptions/pricing/me")
    suspend fun getUserPricing(): GetSubscriptionUserPricingResponseDto

    @POST("v1/subscriptions/payments")
    suspend fun createPayment(
        @Body request: CreateSubscriptionPaymentRequestDto,
    ): CreateSubscriptionPaymentResponseDto

    @GET("v1/subscriptions/payments/{paymentId}")
    suspend fun getPayment(
        @retrofit2.http.Path("paymentId") paymentId: String,
    ): GetSubscriptionPaymentResponseDto
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

interface VoiceApi {
    @GET("v1/voice/ice-servers")
    suspend fun getIceServers(): VoiceIceServersResponseDto
}
