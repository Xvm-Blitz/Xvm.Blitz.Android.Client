package ru.xvmblitz.android.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SessionsApi {
    @POST("v1/sessions")
    suspend fun create(): CreateSessionResponseDto

    @GET("v1/sessions/restore")
    suspend fun restore(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): RestoreSessionsResponseDto

    @GET("v1/sessions/statistics/extended")
    suspend fun getExtendedStatistics(
        @Query("uuid") sessionId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): List<SessionExtendedStatisticsDto>

    @GET("v1/sessions/statistics/aggregated")
    suspend fun getAggregatedStatistics(
        @Query("uuid") sessionId: String,
    ): List<SessionAggregatedStatisticsDto>

    @POST("v1/sessions/{sessionId}/end")
    suspend fun end(
        @Path("sessionId") sessionId: String,
    )
}
