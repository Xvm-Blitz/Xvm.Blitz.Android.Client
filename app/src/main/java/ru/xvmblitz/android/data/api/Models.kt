package ru.xvmblitz.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BattleStatisticsDto(
    @SerialName("allies") val allies: List<BattlePlayerStatisticsDto> = emptyList(),
    @SerialName("enemies") val enemies: List<BattlePlayerStatisticsDto> = emptyList(),
)

@Serializable
data class BattlePlayerStatisticsDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("clan_tag") val clanTag: String? = null,
    @SerialName("tank") val tank: String? = null,
    @SerialName("table_number") val tableNumber: Int = 0,
    @SerialName("win_rate_percents") val winRatePercents: Double? = null,
    @SerialName("number_of_battles") val numberOfBattles: Int? = null,
)

@Serializable
data class GetUsageResponseDto(
    @SerialName("api_key") val apiKey: String,
    @SerialName("total_limit") val totalLimit: Int,
    @SerialName("current_usage") val currentUsage: Int,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
)

@Serializable
enum class ClientPlatform {
    @SerialName("android")
    Android,

    @SerialName("windows")
    Windows,
}

@Serializable
data class GetAppUpdateResponseDto(
    @SerialName("version") val version: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("platform") val platform: ClientPlatform,
)
