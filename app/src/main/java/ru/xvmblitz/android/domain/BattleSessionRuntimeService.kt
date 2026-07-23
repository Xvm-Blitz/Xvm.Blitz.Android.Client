package ru.xvmblitz.android.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.api.BattleStatisticsDto
import ru.xvmblitz.android.data.api.SessionBattleBriefDto
import ru.xvmblitz.android.data.api.SessionBattleCompletedHubDto
import ru.xvmblitz.android.data.api.SessionEndedHubDto
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface BattleSessionRuntimeListener {
    fun onBattleStarted(battle: SessionBattleBriefDto)
    fun onBattleCompleted(notification: SessionBattleCompletedHubDto)
    fun onSessionEnded(sessionId: String)
}

class BattleSessionRuntimeService(
    private val apiBaseUrlProvider: () -> String,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: HubConnection? = null
    private var connectGeneration = 0
    private var activeSessionId: String? = null
    private var sessionNickname: String? = null
    private var listener: BattleSessionRuntimeListener? = null

    fun setListener(value: BattleSessionRuntimeListener?) {
        listener = value
    }

    suspend fun setActiveSession(sessionId: String?, nickname: String?) {
        val normalizedNickname = nickname?.trim()?.takeIf { it.isNotEmpty() }
        mutex.withLock {
            if (
                activeSessionId == sessionId &&
                sessionNickname == normalizedNickname &&
                isConnectionActive()
            ) {
                return
            }

            disconnectInternal()
            activeSessionId = sessionId
            sessionNickname = normalizedNickname

            if (sessionId.isNullOrBlank() || normalizedNickname.isNullOrBlank()) {
                return
            }

            connectInternal(sessionId)
        }
    }

    fun notifyBattleStarted(battle: BattleStatisticsDto) {
        scope.launch {
            mutex.withLock {
                val sessionId = activeSessionId
                val nickname = sessionNickname
                if (sessionId.isNullOrBlank() || nickname.isNullOrBlank()) {
                    return@withLock
                }
                val hub = connection
                if (hub == null || hub.connectionState != HubConnectionState.CONNECTED) {
                    return@withLock
                }
                val tankName = SessionBattlePlayerResolver.resolveTankName(nickname, battle) ?: return@withLock
                withContext(Dispatchers.IO) {
                    hub.send("StartBattle", sessionId, tankName)
                }
            }
        }
    }

    suspend fun dispose() {
        mutex.withLock {
            disconnectInternal()
        }
    }

    private suspend fun connectInternal(sessionId: String) {
        val generation = connectGeneration
        val hubUrl = buildHubUrl(sessionId)
        val builder = HubConnectionBuilder.create(hubUrl)
        if (BuildConfig.DEBUG && shouldTrustAllCertificates(hubUrl)) {
            builder.setHttpClientBuilderCallback { clientBuilder ->
                val trustAll = trustAllCertificates()
                clientBuilder.sslSocketFactory(trustAll.first, trustAll.second)
            }
        }
        val hub = builder.build()
        hub.on(
            "battleStarted",
            { battle: SessionBattleBriefDto ->
                listener?.onBattleStarted(battle)
            },
            SessionBattleBriefDto::class.java,
        )
        hub.on(
            "battleCompleted",
            { notification: SessionBattleCompletedHubDto ->
                listener?.onBattleCompleted(notification)
            },
            SessionBattleCompletedHubDto::class.java,
        )
        hub.on(
            "sessionEnded",
            { notification: SessionEndedHubDto ->
                listener?.onSessionEnded(notification.sessionId)
            },
            SessionEndedHubDto::class.java,
        )
        withContext(Dispatchers.IO) {
            runCatching { hub.start().blockingAwait() }
        }
        if (generation != connectGeneration) {
            withContext(Dispatchers.IO) {
                runCatching { hub.stop().blockingAwait() }
            }
            return
        }
        connection = hub
    }

    private suspend fun disconnectInternal() {
        connectGeneration++
        val hub = connection ?: return
        connection = null
        withContext(Dispatchers.IO) {
            runCatching { hub.stop().blockingAwait() }
        }
    }

    private fun isConnectionActive(): Boolean {
        return when (connection?.connectionState) {
            HubConnectionState.CONNECTED,
            HubConnectionState.CONNECTING,
            -> true
            else -> false
        }
    }

    private fun buildHubUrl(sessionId: String): String {
        val base = apiBaseUrlProvider().trimEnd('/')
        return "$base/v1/hubs/sessions?sessionId=$sessionId"
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
}
