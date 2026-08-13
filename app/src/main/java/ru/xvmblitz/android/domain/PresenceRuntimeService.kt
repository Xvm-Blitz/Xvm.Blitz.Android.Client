package ru.xvmblitz.android.domain

import android.net.Uri
import android.util.Log
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.api.VoiceCallCanceledPayload
import ru.xvmblitz.android.data.api.VoiceCallRejectedPayload
import ru.xvmblitz.android.data.api.VoiceDoNotDisturbChangedPayload
import ru.xvmblitz.android.data.api.VoiceIceCandidatePayload
import ru.xvmblitz.android.data.api.VoiceIncomingCallPayload
import ru.xvmblitz.android.data.api.VoicePeerJoinedPayload
import ru.xvmblitz.android.data.api.VoicePeerLeftPayload
import ru.xvmblitz.android.data.api.VoiceRoomEndedPayload
import ru.xvmblitz.android.data.api.VoiceSdpPayload

interface PresenceVoiceListener {
    fun onHubConnected()
    fun onHubDisconnected()
    fun onIncomingCall(payload: VoiceIncomingCallPayload)
    fun onCallRejected(payload: VoiceCallRejectedPayload)
    fun onCallCanceled(payload: VoiceCallCanceledPayload)
    fun onPeerJoined(payload: VoicePeerJoinedPayload)
    fun onPeerLeft(payload: VoicePeerLeftPayload)
    fun onRoomEnded(payload: VoiceRoomEndedPayload)
    fun onOffer(payload: VoiceSdpPayload)
    fun onAnswer(payload: VoiceSdpPayload)
    fun onIceCandidate(payload: VoiceIceCandidatePayload)
    fun onDoNotDisturbChanged(payload: VoiceDoNotDisturbChangedPayload)
}

class PresenceRuntimeService(
    private val apiBaseUrlProvider: () -> String,
    private val accessTokenProvider: () -> String?,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: HubConnection? = null
    private var loopJob: Job? = null
    private var connectGeneration = 0
    private var enabled = false

    @Volatile
    var voiceListener: PresenceVoiceListener? = null

    @Volatile
    var localDoNotDisturb: Boolean = false

    val isConnected: Boolean
        get() = connection?.connectionState == HubConnectionState.CONNECTED

    suspend fun start() {
        mutex.withLock {
            enabled = true
            restartLoop()
        }
    }

    suspend fun stop() {
        mutex.withLock {
            enabled = false
            stopLoop()
            disconnectInternal()
        }
    }

    suspend fun ensureConnected() {
        mutex.withLock {
            if (!enabled) {
                return
            }
            ensureConnectedInternal()
        }
    }

    suspend fun dispose() {
        mutex.withLock {
            enabled = false
            stopLoop()
            disconnectInternal()
        }
    }

    suspend fun invite(targetPlayerId: Long) {
        invokeVoice("Invite", targetPlayerId)
    }

    suspend fun accept(roomId: String) {
        invokeVoice("Accept", roomId)
    }

    suspend fun reject(roomId: String) {
        invokeVoice("Reject", roomId)
    }

    suspend fun cancel(roomId: String?) {
        invokeVoice("Cancel", roomId)
    }

    suspend fun leave() {
        invokeVoice("Leave")
    }

    suspend fun offer(targetPlayerId: Long, sdp: String) {
        invokeVoice("Offer", targetPlayerId, sdp)
    }

    suspend fun answer(targetPlayerId: Long, sdp: String) {
        invokeVoice("Answer", targetPlayerId, sdp)
    }

    suspend fun iceCandidate(targetPlayerId: Long, candidate: String) {
        invokeVoice("IceCandidate", targetPlayerId, candidate)
    }

    suspend fun setDoNotDisturb(enabled: Boolean) {
        localDoNotDisturb = enabled
        if (!isConnected) {
            return
        }
        invokeVoice("SetDoNotDisturb", enabled)
    }

    private suspend fun invokeVoice(method: String, vararg args: Any?) {
        val hub = mutex.withLock {
            val current = connection ?: throw IllegalStateException("Нет соединения с голосовым хабом")
            if (current.connectionState != HubConnectionState.CONNECTED) {
                throw IllegalStateException("Нет соединения с голосовым хабом")
            }
            current
        }
        withContext(Dispatchers.IO) {
            val invocation = if (args.isEmpty()) {
                hub.invoke(method)
            } else {
                hub.invoke(method, *args)
            }
            invocation.blockingAwait()
        }
    }

    private fun restartLoop() {
        stopLoop()
        loopJob = scope.launch {
            while (isActive) {
                try {
                    mutex.withLock {
                        if (!enabled) {
                            return@withLock
                        }
                        ensureConnectedInternal()
                        sendHeartbeatInternal()
                    }
                } catch (exception: Exception) {
                    Log.w(Tag, "Presence loop iteration failed", exception)
                }
                delay(HeartbeatIntervalMs)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun ensureConnectedInternal() {
        if (!enabled) {
            return
        }
        if (connection?.connectionState == HubConnectionState.CONNECTED) {
            return
        }
        disconnectInternal()
        connectInternal()
    }

    private suspend fun connectInternal() {
        val generation = connectGeneration
        val hubUrl = buildHubUrl() ?: return
        val builder = HubConnectionBuilder.create(hubUrl)
        if (BuildConfig.DEBUG && shouldTrustAllCertificates(hubUrl)) {
            builder.setHttpClientBuilderCallback { clientBuilder ->
                val trustAll = trustAllCertificates()
                clientBuilder.sslSocketFactory(trustAll.first, trustAll.second)
            }
        }
        val hub = builder.build()
        bindVoiceEvents(hub)
        hub.onClosed {
            scope.launch {
                mutex.withLock {
                    if (connection === hub) {
                        connection = null
                    }
                }
                voiceListener?.onHubDisconnected()
            }
        }
        withContext(Dispatchers.IO) {
            runCatching { hub.start().blockingAwait() }
                .onFailure { Log.w(Tag, "Failed to connect presence hub", it) }
        }
        if (generation != connectGeneration) {
            withContext(Dispatchers.IO) {
                runCatching { hub.stop().blockingAwait() }
            }
            return
        }
        if (hub.connectionState != HubConnectionState.CONNECTED) {
            return
        }
        connection = hub
        sendHeartbeatInternal()
        sendDoNotDisturbInternal()
        scope.launch { voiceListener?.onHubConnected() }
    }

    private fun bindVoiceEvents(hub: HubConnection) {
        hub.on(
            "incomingCall",
            { payload: VoiceIncomingCallPayload -> voiceListener?.onIncomingCall(payload) },
            VoiceIncomingCallPayload::class.java,
        )
        hub.on(
            "callRejected",
            { payload: VoiceCallRejectedPayload -> voiceListener?.onCallRejected(payload) },
            VoiceCallRejectedPayload::class.java,
        )
        hub.on(
            "callCanceled",
            { payload: VoiceCallCanceledPayload -> voiceListener?.onCallCanceled(payload) },
            VoiceCallCanceledPayload::class.java,
        )
        hub.on(
            "peerJoined",
            { payload: VoicePeerJoinedPayload -> voiceListener?.onPeerJoined(payload) },
            VoicePeerJoinedPayload::class.java,
        )
        hub.on(
            "peerLeft",
            { payload: VoicePeerLeftPayload -> voiceListener?.onPeerLeft(payload) },
            VoicePeerLeftPayload::class.java,
        )
        hub.on(
            "roomEnded",
            { payload: VoiceRoomEndedPayload -> voiceListener?.onRoomEnded(payload) },
            VoiceRoomEndedPayload::class.java,
        )
        hub.on(
            "offer",
            { payload: VoiceSdpPayload -> voiceListener?.onOffer(payload) },
            VoiceSdpPayload::class.java,
        )
        hub.on(
            "answer",
            { payload: VoiceSdpPayload -> voiceListener?.onAnswer(payload) },
            VoiceSdpPayload::class.java,
        )
        hub.on(
            "iceCandidate",
            { payload: VoiceIceCandidatePayload -> voiceListener?.onIceCandidate(payload) },
            VoiceIceCandidatePayload::class.java,
        )
        hub.on(
            "doNotDisturbChanged",
            { payload: VoiceDoNotDisturbChangedPayload -> voiceListener?.onDoNotDisturbChanged(payload) },
            VoiceDoNotDisturbChangedPayload::class.java,
        )
    }

    private suspend fun sendHeartbeatInternal() {
        val hub = connection ?: return
        if (hub.connectionState != HubConnectionState.CONNECTED) {
            return
        }
        withContext(Dispatchers.IO) {
            runCatching { hub.invoke("Heartbeat").blockingAwait() }
                .onFailure { Log.w(Tag, "Presence heartbeat failed", it) }
        }
    }

    private suspend fun sendDoNotDisturbInternal() {
        val hub = connection ?: return
        if (hub.connectionState != HubConnectionState.CONNECTED) {
            return
        }
        val enabled = localDoNotDisturb
        withContext(Dispatchers.IO) {
            runCatching { hub.invoke("SetDoNotDisturb", enabled).blockingAwait() }
                .onFailure { Log.w(Tag, "SetDoNotDisturb failed", it) }
        }
    }

    private suspend fun disconnectInternal() {
        connectGeneration++
        val hub = connection ?: return
        connection = null
        withContext(Dispatchers.IO) {
            runCatching { hub.stop().blockingAwait() }
        }
    }

    private fun buildHubUrl(): String? {
        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: return null
        val base = apiBaseUrlProvider().trimEnd('/')
        return "$base/v1/hubs/presence?access_token=${Uri.encode(token)}"
    }

    private fun shouldTrustAllCertificates(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("localhost") ||
            lower.contains("127.0.0.1") ||
            lower.contains("10.0.2.2")
    }

    private fun trustAllCertificates(): Pair<javax.net.ssl.SSLSocketFactory, X509TrustManager> {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return sslContext.socketFactory to trustManager
    }

    private companion object {
        const val Tag = "PresenceRuntime"
        const val HeartbeatIntervalMs = 20_000L
    }
}
