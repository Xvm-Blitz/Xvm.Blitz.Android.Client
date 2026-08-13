package ru.xvmblitz.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BattleStatisticsDto(
    @SerialName("allies") val allies: List<BattlePlayerStatisticsDto> = emptyList(),
    @SerialName("enemies") val enemies: List<BattlePlayerStatisticsDto> = emptyList(),
)

@Serializable
enum class XvmUsageStatus {
    @SerialName("currently")
    Currently,

    @SerialName("previously")
    Previously,

    @SerialName("never")
    Never,
}

@Serializable
data class BattlePlayerStatisticsDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("clan_tag") val clanTag: String? = null,
    @SerialName("tank") val tank: String? = null,
    @SerialName("table_number") val tableNumber: Int = 0,
    @SerialName("win_rate_percents") val winRatePercents: Double? = null,
    @SerialName("number_of_battles") val numberOfBattles: Int? = null,
    @SerialName("xvm_usage") val xvmUsage: XvmUsageStatus = XvmUsageStatus.Never,
    @SerialName("do_not_disturb") val doNotDisturb: Boolean = false,
)

@Serializable
enum class AccessType {
    @SerialName("free")
    Free,

    @SerialName("fullAccess")
    FullAccess,

    @SerialName("trial")
    Trial,
}

@Serializable
data class GetUsageResponseDto(
    @SerialName("type") val type: AccessType = AccessType.FullAccess,
    @SerialName("total_limit") val totalLimit: Int,
    @SerialName("current_usage") val currentUsage: Int,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
)

@Serializable
enum class SubscriptionPaymentStatus {
    @SerialName("pending")
    Pending,

    @SerialName("succeeded")
    Succeeded,

    @SerialName("canceled")
    Canceled,

    @SerialName("paymentMismatch")
    PaymentMismatch,
}

@Serializable
data class GetSubscriptionPublicPricingResponseDto(
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("billing_period") val billingPeriod: String,
)

@Serializable
data class SubscriptionPeriodResponseDto(
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
)

@Serializable
data class GetSubscriptionUserPricingResponseDto(
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("billing_period") val billingPeriod: String,
    @SerialName("is_grandfathered") val isGrandfathered: Boolean = false,
    @SerialName("premium_until") val premiumUntil: String? = null,
    @SerialName("legacy_price_until") val legacyPriceUntil: String? = null,
    @SerialName("next_payment_period") val nextPaymentPeriod: SubscriptionPeriodResponseDto,
)

@Serializable
data class CreateSubscriptionPaymentRequestDto(
    @SerialName("receipt_email") val receiptEmail: String,
)

@Serializable
data class CreateSubscriptionPaymentResponseDto(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("confirmation_url") val confirmationUrl: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
)

@Serializable
data class GetSubscriptionPaymentResponseDto(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("status") val status: SubscriptionPaymentStatus,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("paid_at") val paidAt: String? = null,
)

@Serializable
data class OpenIdRefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class OpenIdAuthResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("lesta_expires_at") val lestaExpiresAt: String? = null,
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
