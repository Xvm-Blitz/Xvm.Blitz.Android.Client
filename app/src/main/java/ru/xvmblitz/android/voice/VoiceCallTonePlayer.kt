package ru.xvmblitz.android.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoiceCallTonePlayer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var generator: ToneGenerator? = null
    private var loopJob: Job? = null
    private var kind: Kind = Kind.None

    fun playIncoming() = startLoop(Kind.Incoming) {
        val tone = generator ?: return@startLoop
        while (isActive) {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            delay(540)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            delay(1_200)
        }
    }

    fun playRingback() = startLoop(Kind.Ringback) {
        val tone = generator ?: return@startLoop
        while (isActive) {
            tone.startTone(ToneGenerator.TONE_SUP_DIAL, 1_000)
            delay(3_000)
        }
    }

    fun playBusy() {
        stopLoop()
        kind = Kind.Busy
        loopJob = scope.launch {
            val tone = ensureGenerator() ?: return@launch
            repeat(3) {
                if (!isActive) {
                    return@launch
                }
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
                delay(700)
            }
            kind = Kind.None
        }
    }

    fun stop() {
        stopLoop()
        kind = Kind.None
    }

    fun release() {
        stop()
        generator?.release()
        generator = null
    }

    private fun startLoop(next: Kind, block: suspend CoroutineScope.() -> Unit) {
        if (kind == next) {
            return
        }
        stopLoop()
        kind = next
        if (ensureGenerator() == null) {
            kind = Kind.None
            return
        }
        loopJob = scope.launch(block = block)
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        runCatching { generator?.stopTone() }
    }

    private fun ensureGenerator(): ToneGenerator? {
        generator?.let { return it }
        val stream = if (audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
            AudioManager.STREAM_VOICE_CALL
        } else {
            AudioManager.STREAM_NOTIFICATION
        }
        generator = runCatching { ToneGenerator(stream, 80) }.getOrNull()
        return generator
    }

    private enum class Kind {
        None,
        Incoming,
        Ringback,
        Busy,
    }
}
