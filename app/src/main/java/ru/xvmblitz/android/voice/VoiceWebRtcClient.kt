package ru.xvmblitz.android.voice

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import ru.xvmblitz.android.data.api.VoiceIceServerDto
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiceWebRtcClient(
    private val appContext: android.content.Context,
    private val signaling: Signaling,
) {
    interface Signaling {
        fun sendOffer(targetPlayerId: Long, sdp: String)
        fun sendAnswer(targetPlayerId: Long, sdp: String)
        fun sendIceCandidate(targetPlayerId: Long, candidate: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val peers = mutableMapOf<Long, PeerSession>()
    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private var selfPlayerId: Long = 0L
    private var smallerPlayerIdIsPolite: Boolean = true
    private var muted: Boolean = false
    private var lastNetworkKind: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager = appContext.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    private val connectivityManager =
        appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val kind = networkKind(networkCapabilities)
            val previous = lastNetworkKind
            lastNetworkKind = kind
            if (previous != null && previous != kind) {
                scope.launch { restartIce() }
            }
        }
    }

    val isPrepared: Boolean
        get() = factory != null

    suspend fun prepare(
        servers: List<VoiceIceServerDto>,
        selfPlayerId: Long,
        smallerPlayerIdIsPolite: Boolean,
    ) {
        mutex.withLock {
            this.selfPlayerId = selfPlayerId
            this.smallerPlayerIdIsPolite = smallerPlayerIdIsPolite
            iceServers = toIceServers(servers)
            if (factory != null) {
                applyMuteLocked()
                return
            }
            ensureFactoryInitialized()
            val adm = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseStereoInput(false)
                .setUseStereoOutput(false)
                .createAudioDeviceModule()
            audioDeviceModule = adm
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()
            val constraints = MediaConstraints().apply {
                optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = factory?.createAudioSource(constraints)
            localAudioTrack = factory?.createAudioTrack(LocalTrackId, audioSource)?.also { track ->
                track.setEnabled(true)
            }
            muted = false
            applyMuteLocked()
            requestAudioFocus()
            registerNetworkCallback()
        }
    }

    suspend fun ensurePeer(playerId: Long) {
        mutex.withLock {
            if (peers.containsKey(playerId) || playerId == selfPlayerId || playerId <= 0L) {
                return
            }
            val factory = factory ?: return
            val track = localAudioTrack ?: return
            val polite = if (smallerPlayerIdIsPolite) selfPlayerId < playerId else selfPlayerId > playerId
            val session = PeerSession(playerId = playerId, polite = polite)
            val observer = createObserver(session)
            val pc = factory.createPeerConnection(rtcConfig(), observer) ?: return
            session.peerConnection = pc
            pc.addTrack(track, listOf(LocalStreamId))
            peers[playerId] = session
        }
    }

    suspend fun closePeer(playerId: Long) {
        mutex.withLock {
            peers.remove(playerId)?.close()
        }
    }

    suspend fun closeAll() {
        mutex.withLock {
            peers.values.forEach { session -> session.close() }
            peers.clear()
            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            factory?.dispose()
            factory = null
            audioDeviceModule?.release()
            audioDeviceModule = null
            abandonAudioFocus()
            unregisterNetworkCallback()
            lastNetworkKind = null
        }
    }

    suspend fun setMuted(muted: Boolean) {
        mutex.withLock {
            this.muted = muted
            applyMuteLocked()
        }
    }

    suspend fun handleRemoteOffer(fromPlayerId: Long, sdp: String) {
        ensurePeer(fromPlayerId)
        mutex.withLock {
            val session = peers[fromPlayerId] ?: return
            val pc = session.peerConnection ?: return
            if (!session.polite &&
                (session.makingOffer || pc.signalingState() != PeerConnection.SignalingState.STABLE)
            ) {
                Log.i(Tag, "Ignore offer from $fromPlayerId: we are initiator")
                return
            }
            if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
                Log.w(Tag, "Cannot accept offer from $fromPlayerId in ${pc.signalingState()}")
                return
            }
            setRemoteDescription(pc, SessionDescription(SessionDescription.Type.OFFER, sdp))
            session.remoteDescriptionSet = true
            drainPendingIce(session)
            val answer = createAnswer(pc)
            val local = preferOpusMono(answer)
            setLocalDescription(pc, local)
            signaling.sendAnswer(fromPlayerId, local.description)
        }
    }

    suspend fun handleRemoteAnswer(fromPlayerId: Long, sdp: String) {
        mutex.withLock {
            val session = peers[fromPlayerId] ?: return
            val pc = session.peerConnection ?: return
            setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            session.remoteDescriptionSet = true
            drainPendingIce(session)
        }
    }

    suspend fun handleRemoteIce(fromPlayerId: Long, candidateJson: String) {
        ensurePeer(fromPlayerId)
        mutex.withLock {
            val session = peers[fromPlayerId] ?: return
            val candidate = decodeIceCandidate(candidateJson) ?: return
            if (!session.remoteDescriptionSet) {
                session.pendingIce.add(candidate)
                return
            }
            runCatching { session.peerConnection?.addIceCandidate(candidate) }
                .onFailure { error ->
                    Log.w(Tag, "ICE candidate failed", error)
                }
        }
    }

    suspend fun restartIce() {
        mutex.withLock {
            peers.values.forEach { session ->
                runCatching { session.peerConnection?.restartIce() }
            }
        }
    }

    private fun applyMuteLocked() {
        localAudioTrack?.setEnabled(!muted)
        peers.values.forEach { session ->
            session.peerConnection?.senders?.forEach { sender ->
                val track = sender.track()
                if (track is AudioTrack) {
                    track.setEnabled(!muted)
                }
            }
        }
    }

    private fun createObserver(session: PeerSession): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    scope.launch { restartIce() }
                }
            }

            override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) = Unit

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) {
                    return
                }
                signaling.sendIceCandidate(session.playerId, encodeIceCandidate(candidate))
            }

            override fun onIceCandidateError(event: IceCandidateErrorEvent?) = Unit

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) = Unit

            override fun onAddStream(stream: MediaStream?) = Unit

            override fun onRemoveStream(stream: MediaStream?) = Unit

            override fun onDataChannel(dataChannel: DataChannel?) = Unit

            override fun onRenegotiationNeeded() {
                if (session.polite) {
                    return
                }
                scope.launch { makeOffer(session.playerId) }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                }
            }

            override fun onRemoveTrack(receiver: RtpReceiver?) = Unit

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                }
            }
        }
    }

    private suspend fun makeOffer(playerId: Long) {
        mutex.withLock {
            val session = peers[playerId] ?: return
            val pc = session.peerConnection ?: return
            if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
                return
            }
            session.makingOffer = true
            try {
                val offer = createOffer(pc)
                val local = preferOpusMono(offer)
                setLocalDescription(pc, local)
                signaling.sendOffer(playerId, local.description)
            } catch (error: Exception) {
                Log.w(Tag, "Offer failed", error)
            } finally {
                session.makingOffer = false
            }
        }
    }

    private suspend fun createOffer(pc: PeerConnection): SessionDescription {
        return awaitSdp { observer -> pc.createOffer(observer, mediaConstraints()) }
    }

    private suspend fun createAnswer(pc: PeerConnection): SessionDescription {
        return awaitSdp { observer -> pc.createAnswer(observer, mediaConstraints()) }
    }

    private suspend fun setLocalDescription(pc: PeerConnection, sdp: SessionDescription) {
        awaitSet { observer -> pc.setLocalDescription(observer, sdp) }
    }

    private suspend fun setRemoteDescription(pc: PeerConnection, sdp: SessionDescription) {
        awaitSet { observer -> pc.setRemoteDescription(observer, sdp) }
    }

    private suspend fun awaitSdp(block: (SdpObserver) -> Unit): SessionDescription {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                block(
                    object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            if (sdp == null) {
                                continuation.resumeWithException(IllegalStateException("Пустой SDP"))
                            } else {
                                continuation.resume(sdp)
                            }
                        }

                        override fun onSetSuccess() = Unit

                        override fun onCreateFailure(error: String?) {
                            continuation.resumeWithException(IllegalStateException(error ?: "SDP"))
                        }

                        override fun onSetFailure(error: String?) = Unit
                    },
                )
            }
        }
    }

    private suspend fun awaitSet(block: (SdpObserver) -> Unit) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                block(
                    object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) = Unit

                        override fun onSetSuccess() {
                            continuation.resume(Unit)
                        }

                        override fun onCreateFailure(error: String?) = Unit

                        override fun onSetFailure(error: String?) {
                            continuation.resumeWithException(IllegalStateException(error ?: "SDP set"))
                        }
                    },
                )
            }
        }
    }

    private fun drainPendingIce(session: PeerSession) {
        val pc = session.peerConnection ?: return
        session.pendingIce.forEach { candidate ->
            runCatching { pc.addIceCandidate(candidate) }
        }
        session.pendingIce.clear()
    }

    private fun rtcConfig(): PeerConnection.RTCConfiguration {
        return PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
    }

    private fun mediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
    }

    private fun preferOpusMono(sdp: SessionDescription): SessionDescription {
        val patched = sdp.description
            .replace("useinbandfec=1", "useinbandfec=1;stereo=0;sprop-stereo=0")
            .replace("a=rtpmap:111 opus/48000/2", "a=rtpmap:111 opus/48000/2")
        return SessionDescription(sdp.type, patched)
    }

    private fun toIceServers(servers: List<VoiceIceServerDto>): List<PeerConnection.IceServer> {
        val result = servers.flatMap { server ->
            server.urls.filter { url -> url.isNotBlank() }.map { url ->
                val builder = PeerConnection.IceServer.builder(url)
                val username = server.username
                val credential = server.credential
                if (!username.isNullOrBlank() && !credential.isNullOrBlank()) {
                    builder.setUsername(username).setPassword(credential)
                }
                builder.createIceServer()
            }
        }
        if (result.isNotEmpty()) {
            return result
        }
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        )
    }

    private fun encodeIceCandidate(candidate: IceCandidate): String {
        return JSONObject()
            .put("candidate", candidate.sdp)
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
            .toString()
    }

    private fun decodeIceCandidate(raw: String): IceCandidate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("{")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            val candidate = json.optString("candidate")
            if (candidate.isBlank()) {
                return null
            }
            val sdpMid = json.optString("sdpMid").takeIf { value -> value.isNotBlank() }
            val mLine = if (json.has("sdpMLineIndex")) json.optInt("sdpMLineIndex") else 0
            return IceCandidate(sdpMid, mLine, candidate)
        }
        return IceCandidate("0", 0, trimmed)
    }

    private fun requestAudioFocus() {
        val attributesBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            attributesBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
        val attributes = attributesBuilder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
        audioManager.isSpeakerphoneOn = true
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
        audioManager.isSpeakerphoneOn = false
    }

    private fun registerNetworkCallback() {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun unregisterNetworkCallback() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun networkKind(capabilities: NetworkCapabilities): String {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            else -> "other"
        }
    }

    private fun ensureFactoryInitialized() {
        synchronized(FactoryLock) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .createInitializationOptions(),
                )
                factoryInitialized = true
            }
        }
    }

    private class PeerSession(
        val playerId: Long,
        val polite: Boolean,
    ) {
        var peerConnection: PeerConnection? = null
        var makingOffer: Boolean = false
        var remoteDescriptionSet: Boolean = false
        val pendingIce = mutableListOf<IceCandidate>()

        fun close() {
            runCatching { peerConnection?.close() }
            runCatching { peerConnection?.dispose() }
            peerConnection = null
            pendingIce.clear()
        }
    }

    private companion object {
        const val Tag = "XvmVoiceRtc"
        const val LocalTrackId = "xvm-audio"
        const val LocalStreamId = "xvm-voice"
        private val FactoryLock = Any()
        private var factoryInitialized = false
    }
}
