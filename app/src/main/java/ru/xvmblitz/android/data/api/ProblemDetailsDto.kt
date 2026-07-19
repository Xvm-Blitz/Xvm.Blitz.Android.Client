package ru.xvmblitz.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetailsDto(
    @SerialName("type") val type: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("detail") val detail: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("retryAfter") val retryAfter: String? = null,
)
