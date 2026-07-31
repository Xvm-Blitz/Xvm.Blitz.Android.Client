package ru.xvmblitz.android.domain

import android.net.Uri
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

class PresenceRuntimeService(
    private val apiBaseUrlProvider: () -> String,
    private val accessTokenProvider: () -> String?,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: HubConnection? = null
    private var heartbeatJob: Job? = null
    private var connectGeneration = 0
    private var enabled = false

    suspend fun start() {
        mutex.withLock {
            enabled = true
            ensureConnectedInternal()
        }
    }

    suspend fun stop() {
        mutex.withLock {
            enabled = false
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
            disconnectInternal()
        }
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
        hub.onClosed {
            scope.launch {
                mutex.withLock {
                    if (connection === hub) {
                        stopHeartbeat()
                        connection = null
                    }
                }
            }
        }
        withContext(Dispatchers.IO) {
            runCatching { hub.start().blockingAwait() }
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
        startHeartbeat(hub)
    }

    private fun startHeartbeat(hub: HubConnection) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HeartbeatIntervalMs)
                val activeHub = mutex.withLock { connection }
                if (activeHub !== hub || hub.connectionState != HubConnectionState.CONNECTED) {
                    break
                }
                runCatching { hub.send("Heartbeat") }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun disconnectInternal() {
        connectGeneration++
        stopHeartbeat()
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
        const val HeartbeatIntervalMs = 30_000L
    }
}
