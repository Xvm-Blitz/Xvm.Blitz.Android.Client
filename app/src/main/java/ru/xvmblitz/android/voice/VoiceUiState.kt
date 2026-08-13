package ru.xvmblitz.android.voice

enum class VoicePhase {
    Idle,
    OutgoingRinging,
    IncomingRinging,
    InCall,
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val roomId: String? = null,
    val selfPlayerId: Long? = null,
    val isLocalPremium: Boolean = false,
    val incomingFromPlayerId: Long? = null,
    val incomingExpiresAtMs: Long? = null,
    val outgoingTargetPlayerId: Long? = null,
    val memberIds: List<Long> = emptyList(),
    val endsAtMs: Long? = null,
    val muted: Boolean = true,
    val nicks: Map<Long, String> = emptyMap(),
    val maxParticipants: Int = 4,
    val capturingAudio: Boolean = false,
    val statusMessage: String? = null,
) {
    val showIncomingBanner: Boolean
        get() = phase == VoicePhase.IncomingRinging && incomingFromPlayerId != null

    val showCallWidget: Boolean
        get() = phase == VoicePhase.OutgoingRinging || phase == VoicePhase.InCall

    fun nickname(playerId: Long?): String {
        if (playerId == null || playerId <= 0L) {
            return "игрок"
        }
        return nicks[playerId] ?: playerId.toString()
    }
}
