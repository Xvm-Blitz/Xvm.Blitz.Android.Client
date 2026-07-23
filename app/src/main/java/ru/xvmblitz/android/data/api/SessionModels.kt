package ru.xvmblitz.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequestDto(
    @SerialName("nickname") val nickname: String,
    @SerialName("secretKey") val secretKey: String,
)

@Serializable
data class CreateSessionResponseDto(
    @SerialName("id") val id: String,
)

@Serializable
data class EndSessionRequestDto(
    @SerialName("secretKey") val secretKey: String,
)

@Serializable
data class RestoredSessionDto(
    @SerialName("id") val id: String,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("endedAt") val endedAt: String? = null,
)

@Serializable
data class RestoreSessionsResponseDto(
    @SerialName("sessions") val sessions: List<RestoredSessionDto> = emptyList(),
    @SerialName("page") val page: Int,
    @SerialName("pageSize") val pageSize: Int,
    @SerialName("totalCount") val totalCount: Int,
)

@Serializable
data class SessionBattleBriefDto(
    @SerialName("id") val id: Long,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("result") val result: String? = null,
    @SerialName("frags") val frags: Short? = null,
    @SerialName("damageDealt") val damageDealt: Short? = null,
    @SerialName("tankName") val tankName: String? = null,
    @SerialName("isAccurate") val isAccurate: Boolean? = null,
)

@Serializable
data class SessionExtendedStatisticsDto(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("battles") val battles: List<SessionBattleBriefDto> = emptyList(),
)

@Serializable
data class SessionAggregatedStatisticsDto(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("averageDamage") val averageDamage: Double,
    @SerialName("averageFrags") val averageFrags: Double,
    @SerialName("totalWins") val totalWins: Int,
    @SerialName("totalBattles") val totalBattles: Int,
)

@Serializable
data class SessionBattleAggregatedHubDto(
    @SerialName("averageDamage") val averageDamage: Double,
    @SerialName("averageFrags") val averageFrags: Double,
    @SerialName("totalWins") val totalWins: Int,
    @SerialName("totalBattles") val totalBattles: Int,
)

@Serializable
data class SessionBattleCompletedHubDto(
    @SerialName("battle") val battle: SessionBattleBriefDto,
    @SerialName("aggregated") val aggregated: SessionBattleAggregatedHubDto,
)

@Serializable
data class SessionEndedHubDto(
    @SerialName("sessionId") val sessionId: String,
)
