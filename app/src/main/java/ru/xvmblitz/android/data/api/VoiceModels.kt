package ru.xvmblitz.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoiceIceServersResponseDto(
    @SerialName("ice_servers") val iceServers: List<VoiceIceServerDto> = emptyList(),
    @SerialName("call_duration_seconds") val callDurationSeconds: Int = 420,
    @SerialName("invite_timeout_seconds") val inviteTimeoutSeconds: Int = 30,
    @SerialName("max_participants") val maxParticipants: Int = 4,
    @SerialName("smaller_player_id_is_polite") val smallerPlayerIdIsPolite: Boolean = true,
)

@Serializable
data class VoiceIceServerDto(
    @SerialName("urls") val urls: List<String> = emptyList(),
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null,
)

data class VoiceNicknameEntry(
    val playerId: Long = 0L,
    val nickname: String = "",
)

data class VoiceIncomingCallPayload(
    val roomId: String = "",
    val fromPlayerId: Long = 0L,
    val inviteExpiresAt: String = "",
    val fromNickname: String? = null,
)

data class VoiceCallRejectedPayload(
    val playerId: Long = 0L,
    val reason: String = "",
    val nickname: String? = null,
)

data class VoiceCallCanceledPayload(
    val roomId: String = "",
    val playerId: Long = 0L,
    val nickname: String? = null,
)

data class VoicePeerJoinedPayload(
    val roomId: String = "",
    val playerId: Long = 0L,
    val memberIds: List<Long> = emptyList(),
    val endsAt: String? = null,
    val nicknames: List<VoiceNicknameEntry> = emptyList(),
)

data class VoicePeerLeftPayload(
    val roomId: String = "",
    val playerId: Long = 0L,
    val memberIds: List<Long> = emptyList(),
)

data class VoiceRoomEndedPayload(
    val roomId: String = "",
    val reason: String = "",
)

data class VoiceSdpPayload(
    val roomId: String = "",
    val fromPlayerId: Long = 0L,
    val sdp: String = "",
)

data class VoiceIceCandidatePayload(
    val roomId: String = "",
    val fromPlayerId: Long = 0L,
    val candidate: String = "",
)

data class VoiceDoNotDisturbChangedPayload(
    val enabled: Boolean = false,
)

@Serializable
data class VoiceIceCandidateJson(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
)
