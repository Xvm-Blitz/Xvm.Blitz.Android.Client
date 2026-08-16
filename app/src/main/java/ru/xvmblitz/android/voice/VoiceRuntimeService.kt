package ru.xvmblitz.android.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.xvmblitz.android.data.api.AccessType
import ru.xvmblitz.android.data.api.VoiceCallCanceledPayload
import ru.xvmblitz.android.data.api.VoiceCallRejectedPayload
import ru.xvmblitz.android.data.api.VoiceDoNotDisturbChangedPayload
import ru.xvmblitz.android.data.api.VoiceIceCandidatePayload
import ru.xvmblitz.android.data.api.VoiceIceServersResponseDto
import ru.xvmblitz.android.data.api.VoiceIncomingCallPayload
import ru.xvmblitz.android.data.api.VoicePeerJoinedPayload
import ru.xvmblitz.android.data.api.VoicePeerLeftPayload
import ru.xvmblitz.android.data.api.VoiceRoomEndedPayload
import ru.xvmblitz.android.data.api.VoiceSdpPayload
import ru.xvmblitz.android.data.auth.AuthRepository
import ru.xvmblitz.android.data.settings.SettingsRepository
import ru.xvmblitz.android.domain.BattleStatisticsStore
import ru.xvmblitz.android.domain.PresenceRuntimeService
import ru.xvmblitz.android.domain.PresenceVoiceListener

class VoiceRuntimeService(
    private val appContext: Context,
    private val presence: PresenceRuntimeService,
    private val voiceApiProvider: () -> ru.xvmblitz.android.data.api.VoiceApi,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val battleStatisticsStore: BattleStatisticsStore,
) : PresenceVoiceListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val rtc = VoiceWebRtcClient(
        appContext = appContext,
        signaling = object : VoiceWebRtcClient.Signaling {
            override fun sendOffer(targetPlayerId: Long, sdp: String) {
                scope.launch(Dispatchers.IO) {
                    runCatching { presence.offer(targetPlayerId, sdp) }
                        .onFailure { showError(it) }
                }
            }

            override fun sendAnswer(targetPlayerId: Long, sdp: String) {
                scope.launch(Dispatchers.IO) {
                    runCatching { presence.answer(targetPlayerId, sdp) }
                        .onFailure { showError(it) }
                }
            }

            override fun sendIceCandidate(targetPlayerId: Long, candidate: String) {
                scope.launch(Dispatchers.IO) {
                    runCatching { presence.iceCandidate(targetPlayerId, candidate) }
                }
            }
        },
    )

    private var iceConfig: VoiceIceServersResponseDto? = null
    private var pendingAfterMic: (() -> Unit)? = null
    private var disconnectTeardownJob: Job? = null
    private val tones = VoiceCallTonePlayer(appContext)
    private var suppressBusy = false
    private var tonePhase = VoicePhase.Idle
    private var toneStatus: String? = null

    init {
        presence.voiceListener = this
        scope.launch {
            settingsRepository.settings
                .map { settings -> settings.voiceDoNotDisturb }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    runCatching { presence.setDoNotDisturb(enabled) }
                }
        }
        scope.launch {
            battleStatisticsStore.state.collectLatest { battle ->
                val nicks = (battle.allies + battle.enemies)
                    .filter { player -> player.id != null && !player.nickname.isNullOrBlank() }
                    .associate { player -> player.id!! to player.nickname!! }
                _state.update { current -> current.copy(nicks = current.nicks + nicks) }
            }
        }
        refreshAccount()
        scope.launch {
            state.collect { current ->
                syncTones(current.phase, current.statusMessage)
            }
        }
    }

    fun refreshAccount() {
        val selfId = authRepository.getLestaAccountId()
        _state.update { current ->
            current.copy(selfPlayerId = selfId, isLocalPremium = current.isLocalPremium)
        }
    }

    fun setLocalPremium(premium: Boolean) {
        _state.update { current -> current.copy(isLocalPremium = premium) }
    }

    fun setAccessType(type: AccessType?) {
        setLocalPremium(type == AccessType.FullAccess || type == AccessType.Trial)
        refreshAccount()
    }

    fun invite(targetPlayerId: Long, targetOnline: Boolean = true, nickname: String? = null) {
        if (targetPlayerId <= 0L || targetPlayerId == _state.value.selfPlayerId) {
            return
        }
        if (!_state.value.isLocalPremium) {
            toast("Голосовой чат можно начать только с премиум-подпиской.")
            return
        }
        if (!targetOnline) {
            tones.playBusy()
            return
        }
        withMicrophone {
            scope.launch {
                _state.update { state ->
                    val withNick = if (!nickname.isNullOrBlank()) {
                        state.copy(nicks = state.nicks + (targetPlayerId to nickname.trim()))
                    } else {
                        state
                    }
                    if (withNick.phase == VoicePhase.InCall) {
                        withNick.copy(
                            outgoingTargetPlayerId = targetPlayerId,
                            statusMessage = "Вызов ${withNick.nickname(targetPlayerId)}…",
                        )
                    } else {
                        withNick.copy(
                            phase = VoicePhase.OutgoingRinging,
                            outgoingTargetPlayerId = targetPlayerId,
                            incomingExpiresAtMs = System.currentTimeMillis() + inviteTimeoutMs(),
                            muted = false,
                            capturingAudio = true,
                            statusMessage = null,
                        )
                    }
                }
                try {
                    presence.invite(targetPlayerId)
                    runCatching { ensureIceConfig() }
                } catch (error: Exception) {
                    showError(error)
                    teardownCall()
                    tones.playBusy()
                }
            }
        }
    }

    fun acceptIncoming() {
        val roomId = _state.value.roomId ?: return
        withMicrophone {
            scope.launch {
                try {
                    ensureIceConfig()
                    presence.accept(roomId)
                } catch (error: Exception) {
                    showError(error)
                    resetToIdle()
                }
            }
        }
    }

    fun rejectIncoming() {
        val roomId = _state.value.roomId ?: return
        suppressBusy = true
        scope.launch {
            runCatching { presence.reject(roomId) }
            resetToIdle()
        }
    }

    fun hangup() {
        suppressBusy = true
        scope.launch {
            val current = _state.value
            when (current.phase) {
                VoicePhase.InCall -> runCatching { presence.leave() }
                VoicePhase.OutgoingRinging -> runCatching { presence.cancel(current.roomId) }
                VoicePhase.IncomingRinging -> {
                    val roomId = current.roomId
                    if (roomId != null) {
                        runCatching { presence.reject(roomId) }
                    }
                }
                VoicePhase.Idle -> Unit
            }
            teardownCall()
        }
    }

    fun toggleMute() {
        scope.launch {
            val muted = !_state.value.muted
            rtc.setMuted(muted)
            _state.update { state -> state.copy(muted = muted) }
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        val pending = pendingAfterMic
        pendingAfterMic = null
        if (granted) {
            pending?.invoke()
        } else {
            toast("Нужен доступ к микрофону")
        }
    }

    override fun onHubConnected() {
        disconnectTeardownJob?.cancel()
        disconnectTeardownJob = null
        refreshAccount()
    }

    override fun onHubDisconnected() {
        if (_state.value.phase == VoicePhase.Idle) {
            return
        }
        disconnectTeardownJob?.cancel()
        disconnectTeardownJob = scope.launch {
            delay(12_000)
            if (!presence.isConnected && _state.value.phase != VoicePhase.Idle) {
                teardownCall()
            }
        }
    }

    override fun onIncomingCall(payload: VoiceIncomingCallPayload) {
        scope.launch {
            val roomId = payload.roomId.takeIf { value -> value.isNotBlank() } ?: return@launch
            _state.update { state ->
                val nicks = if (!payload.fromNickname.isNullOrBlank()) {
                    state.nicks + (payload.fromPlayerId to payload.fromNickname.trim())
                } else {
                    state.nicks
                }
                state.copy(
                    phase = VoicePhase.IncomingRinging,
                    roomId = roomId,
                    incomingFromPlayerId = payload.fromPlayerId,
                    incomingExpiresAtMs = parseTimeMillis(payload.inviteExpiresAt),
                    nicks = nicks,
                    statusMessage = null,
                )
            }
            runCatching { ensureIceConfig() }
        }
    }

    override fun onCallRejected(payload: VoiceCallRejectedPayload) {
        scope.launch {
            if (!payload.nickname.isNullOrBlank()) {
                _state.update { state ->
                    state.copy(nicks = state.nicks + (payload.playerId to payload.nickname.trim()))
                }
            }
            val message = rejectMessage(payload.reason)
            toast(message)
            val current = _state.value
            if (current.phase == VoicePhase.OutgoingRinging) {
                teardownCall()
            } else {
                _state.update { state ->
                    state.copy(outgoingTargetPlayerId = null, statusMessage = message)
                }
            }
        }
    }

    override fun onCallCanceled(payload: VoiceCallCanceledPayload) {
        scope.launch {
            val current = _state.value
            if (current.phase == VoicePhase.IncomingRinging &&
                (current.roomId == null || current.roomId == payload.roomId)
            ) {
                resetToIdle()
            }
        }
    }

    override fun onPeerJoined(payload: VoicePeerJoinedPayload) {
        scope.launch {
            try {
                ensureIceConfig()
                val selfId = _state.value.selfPlayerId ?: authRepository.getLestaAccountId() ?: return@launch
                val members = payload.memberIds.distinct()
                rtc.prepare(
                    servers = iceConfig?.iceServers.orEmpty(),
                    selfPlayerId = selfId,
                    smallerPlayerIdIsPolite = iceConfig?.smallerPlayerIdIsPolite ?: true,
                )
                members.filter { memberId -> memberId != selfId }.forEach { memberId ->
                    rtc.ensurePeer(memberId)
                }
                rtc.setMuted(_state.value.muted)
                val payloadNicks = payload.nicknames
                    .filter { entry -> entry.playerId > 0L && entry.nickname.isNotBlank() }
                    .associate { entry -> entry.playerId to entry.nickname.trim() }
                _state.update { state ->
                    state.copy(
                        phase = VoicePhase.InCall,
                        roomId = payload.roomId,
                        memberIds = members,
                        endsAtMs = parseTimeMillis(payload.endsAt) ?: state.endsAtMs,
                        outgoingTargetPlayerId = state.outgoingTargetPlayerId
                            ?.takeIf { target -> target !in members },
                        nicks = state.nicks + payloadNicks,
                        capturingAudio = true,
                        statusMessage = null,
                    )
                }
            } catch (error: Exception) {
                showError(error)
            }
        }
    }

    override fun onPeerLeft(payload: VoicePeerLeftPayload) {
        scope.launch {
            rtc.closePeer(payload.playerId)
            val selfId = _state.value.selfPlayerId
            val members = payload.memberIds.distinct()
            if (selfId != null && selfId !in members) {
                teardownCall()
                return@launch
            }
            _state.update { state ->
                state.copy(memberIds = members, roomId = payload.roomId)
            }
        }
    }

    override fun onRoomEnded(payload: VoiceRoomEndedPayload) {
        scope.launch {
            teardownCall()
        }
    }

    override fun onOffer(payload: VoiceSdpPayload) {
        scope.launch {
            runCatching { rtc.handleRemoteOffer(payload.fromPlayerId, payload.sdp) }
                .onFailure { showError(it) }
        }
    }

    override fun onAnswer(payload: VoiceSdpPayload) {
        scope.launch {
            runCatching { rtc.handleRemoteAnswer(payload.fromPlayerId, payload.sdp) }
                .onFailure { showError(it) }
        }
    }

    override fun onIceCandidate(payload: VoiceIceCandidatePayload) {
        scope.launch {
            runCatching { rtc.handleRemoteIce(payload.fromPlayerId, payload.candidate) }
        }
    }

    override fun onDoNotDisturbChanged(payload: VoiceDoNotDisturbChangedPayload) = Unit

    fun shutdown() {
        suppressBusy = true
        scope.launch {
            teardownCall()
            tones.release()
        }
    }

    suspend fun shutdownAndWait() {
        teardownCall()
    }

    private fun withMicrophone(action: () -> Unit) {
        if (hasMicrophonePermission()) {
            action()
            return
        }
        pendingAfterMic = action
        VoiceMicPermissionActivity.start(appContext)
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private suspend fun ensureIceConfig() {
        if (iceConfig != null) {
            return
        }
        iceConfig = voiceApiProvider().getIceServers()
        _state.update { state ->
            state.copy(maxParticipants = iceConfig?.maxParticipants ?: 4)
        }
    }

    private suspend fun teardownCall() {
        runCatching { rtc.closeAll() }
        iceConfig = null
        resetToIdle()
    }

    private fun resetToIdle() {
        _state.update { state ->
            VoiceUiState(
                selfPlayerId = state.selfPlayerId,
                isLocalPremium = state.isLocalPremium,
                nicks = state.nicks,
                maxParticipants = state.maxParticipants,
            )
        }
    }

    private fun syncTones(phase: VoicePhase, status: String?) {
        if (phase == tonePhase && status == toneStatus) {
            return
        }
        val previous = tonePhase
        tonePhase = phase
        toneStatus = status
        when (phase) {
            VoicePhase.IncomingRinging -> tones.playIncoming()
            VoicePhase.OutgoingRinging -> tones.playRingback()
            VoicePhase.InCall -> {
                suppressBusy = false
                tones.stop()
            }
            VoicePhase.Idle -> {
                val playBusy = !suppressBusy &&
                    (
                        previous == VoicePhase.OutgoingRinging ||
                            (previous == VoicePhase.Idle && isUnavailableStatus(status))
                        )
                suppressBusy = false
                if (playBusy) {
                    tones.playBusy()
                } else {
                    tones.stop()
                }
            }
        }
    }

    private fun inviteTimeoutMs(): Long {
        val seconds = iceConfig?.inviteTimeoutSeconds?.takeIf { value -> value > 0 } ?: 30
        return seconds * 1_000L
    }

    private fun isUnavailableStatus(status: String?): Boolean {
        val text = status.orEmpty().lowercase()
        return text.contains("занят") ||
            text.contains("busy") ||
            text.contains("не беспокоит") ||
            text.contains("отклон") ||
            text.contains("отмен")
    }

    private fun showError(error: Throwable) {
        val message = userMessage(error)
        toast(message)
        _state.update { state -> state.copy(statusMessage = message) }
    }

    private fun userMessage(error: Throwable): String {
        val raw = generateSequence(error) { throwable -> throwable.cause }
            .mapNotNull { throwable -> throwable.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            .orEmpty()
        return when {
            raw.contains("премиум", ignoreCase = true) ->
                "Голосовой чат можно начать только с премиум-подпиской."
            raw.contains("хаб", ignoreCase = true) -> "Нет соединения с голосовым чатом"
            raw.isBlank() -> "Не удалось выполнить голосовой запрос"
            else -> raw
        }
    }

    private fun rejectMessage(reason: String): String {
        return when (reason.trim()) {
            "doNotDisturb" -> "Игрок не принимает вызовы"
            "declined" -> "Вызов отклонён"
            "busy" -> "Игрок занят"
            else -> "Вызов отклонён"
        }
    }

    private fun parseTimeMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
