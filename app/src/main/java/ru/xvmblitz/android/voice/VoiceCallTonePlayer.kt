package ru.xvmblitz.android.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.sin

class VoiceCallTonePlayer(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var kind: Kind = Kind.None
    private val stopBusyRunnable = Runnable {
        synchronized(lock) {
            if (kind == Kind.Busy) {
                stopLocked()
            }
        }
    }

    fun playIncoming() = play(Kind.Incoming, IncomingPcm, loop = true)

    fun playRingback() = play(Kind.Ringback, RingbackPcm, loop = true)

    fun playBusy() = play(Kind.Busy, BusyPcm, loop = false)

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    fun release() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun play(next: Kind, pcm: ShortArray, loop: Boolean) {
        synchronized(lock) {
            if (kind == next && next != Kind.Busy) {
                return
            }
            stopLocked()
            kind = next
            val created = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull()
            if (created == null) {
                kind = Kind.None
                return
            }
            created.write(pcm, 0, pcm.size)
            if (loop) {
                created.setLoopPoints(0, pcm.size, -1)
            }
            created.play()
            track = created
            if (!loop) {
                val durationMs = pcm.size * 1_000L / SampleRate
                mainHandler.postDelayed(stopBusyRunnable, durationMs + 50L)
            }
        }
    }

    private fun stopLocked() {
        mainHandler.removeCallbacks(stopBusyRunnable)
        val current = track
        track = null
        kind = Kind.None
        if (current == null) {
            return
        }
        runCatching {
            current.pause()
            current.flush()
            current.stop()
            current.release()
        }
    }

    private enum class Kind {
        None,
        Incoming,
        Ringback,
        Busy,
    }

    private data class ToneSegment(
        val frequencyHz: Double,
        val durationMs: Int,
    )

    private companion object {
        const val SampleRate = 22_050
        const val Amplitude = 0.28

        val IncomingPcm = buildPcm(
            listOf(
                ToneSegment(880.0, 400),
                ToneSegment(0.0, 140),
                ToneSegment(880.0, 400),
                ToneSegment(0.0, 1_200),
            ),
        )

        val RingbackPcm = buildPcm(
            listOf(
                ToneSegment(425.0, 1_000),
                ToneSegment(0.0, 2_000),
            ),
        )

        val BusyPcm = buildPcm(
            listOf(
                ToneSegment(425.0, 350),
                ToneSegment(0.0, 350),
                ToneSegment(425.0, 350),
                ToneSegment(0.0, 350),
                ToneSegment(425.0, 350),
                ToneSegment(0.0, 400),
            ),
        )

        fun buildPcm(segments: List<ToneSegment>): ShortArray {
            val total = segments.sumOf { segment ->
                maxOf(1, SampleRate * segment.durationMs / 1_000)
            }
            val samples = ShortArray(total)
            var index = 0
            for (segment in segments) {
                val count = maxOf(1, SampleRate * segment.durationMs / 1_000)
                for (sample in 0 until count) {
                    val amplitude = if (segment.frequencyHz <= 0.0) {
                        0.0
                    } else {
                        sin(2.0 * Math.PI * segment.frequencyHz * sample / SampleRate) * Amplitude
                    }
                    samples[index++] = (amplitude * Short.MAX_VALUE).toInt().toShort()
                }
            }
            return samples
        }
    }
}
