package ru.xvmblitz.android.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SessionsApi {
    @POST("v1/sessions")
    suspend fun create(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Body request: CreateSessionRequestDto,
    ): CreateSessionResponseDto

    @GET("v1/sessions/restore")
    suspend fun restore(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Query("nickname") nickname: String,
        @Query("secret_key") secretKey: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): RestoreSessionsResponseDto

    @GET("v1/sessions/statistics/extended")
    suspend fun getExtendedStatistics(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Query("uuid") sessionId: String,
    ): List<SessionExtendedStatisticsDto>

    @GET("v1/sessions/statistics/aggregated")
    suspend fun getAggregatedStatistics(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Query("uuid") sessionId: String,
    ): List<SessionAggregatedStatisticsDto>

    @POST("v1/sessions/{sessionId}/end")
    suspend fun end(
        @Header("X-Xvm-Api-Key") apiKey: String,
        @Path("sessionId") sessionId: String,
        @Body request: EndSessionRequestDto,
    )
}
