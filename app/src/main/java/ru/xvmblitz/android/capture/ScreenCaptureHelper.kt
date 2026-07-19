package ru.xvmblitz.android.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class ScreenCaptureSession(
    context: Context,
    resultCode: Int,
    data: Intent,
) : AutoCloseable {
    private val mediaProjection: MediaProjection
    private val handlerThread = HandlerThread("xvm-capture").also { it.start() }
    private val handler = Handler(handlerThread.looper)
    private val width: Int
    private val height: Int
    private val density: Int
    private val captureMutex = Mutex()
    private var closed = false

    init {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            ?: error("MediaProjection is null")
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = Unit
            },
            handler,
        )
    }

    suspend fun captureJpeg(): ByteArray {
        check(!closed) { "ScreenCaptureSession is closed" }
        return captureMutex.withLock {
            val bitmap = withTimeout(FRAME_WAIT_TIMEOUT) {
                awaitUsableBitmap()
            }
            try {
                bitmap.toGrayscaleJpegBytes()
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun awaitUsableBitmap(): Bitmap {
        val imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            IMAGE_READER_MAX_IMAGES,
        )
        var virtualDisplay: VirtualDisplay? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                var acceptedFrames = 0

                fun complete(result: Result<Bitmap>) {
                    if (!completed.compareAndSet(false, true)) {
                        result.getOrNull()?.recycle()
                        return
                    }
                    imageReader.setOnImageAvailableListener(null, null)
                    result.fold(
                        onSuccess = { bitmap ->
                            continuation.resume(bitmap) { _, value, _ -> value.recycle() }
                        },
                        onFailure = { error ->
                            continuation.resumeWithException(error)
                        },
                    )
                }

                imageReader.setOnImageAvailableListener(
                    { reader ->
                        if (completed.get()) {
                            return@setOnImageAvailableListener
                        }
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            acceptedFrames += 1
                            if (acceptedFrames <= FRAMES_TO_SKIP) {
                                return@setOnImageAvailableListener
                            }
                            val bitmap = image.toBitmap(width, height)
                            if (bitmap.isMostlyBlack()) {
                                bitmap.recycle()
                                return@setOnImageAvailableListener
                            }
                            complete(Result.success(bitmap))
                        } catch (exception: Exception) {
                            complete(Result.failure(exception))
                        } finally {
                            image.close()
                        }
                    },
                    handler,
                )

                try {
                    virtualDisplay = mediaProjection.createVirtualDisplay(
                        "xvm-blitz-capture",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface,
                        null,
                        handler,
                    )
                } catch (exception: Exception) {
                    complete(Result.failure(exception))
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    completed.set(true)
                    imageReader.setOnImageAvailableListener(null, null)
                }
            }
        } finally {
            virtualDisplay?.release()
            runCatching { imageReader.close() }
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        runCatching { mediaProjection.stop() }
        handlerThread.quitSafely()
    }

    companion object {
        private const val IMAGE_READER_MAX_IMAGES = 3
        private const val FRAMES_TO_SKIP = 1
        private val FRAME_WAIT_TIMEOUT = 500.milliseconds
    }
}

private fun Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    buffer.rewind()

    val paddedWidth = width + rowPadding / pixelStride
    val source = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    source.copyPixelsFromBuffer(buffer)
    return if (paddedWidth == width) {
        source
    } else {
        Bitmap.createBitmap(source, 0, 0, width, height).also { source.recycle() }
    }
}

private fun Bitmap.isMostlyBlack(): Boolean {
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)
    var maxLuminance = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val color = getPixel(x, y)
            val luminance =
                (0.299 * ((color shr 16) and 0xFF)) +
                    (0.587 * ((color shr 8) and 0xFF)) +
                    (0.114 * (color and 0xFF))
            if (luminance > maxLuminance) {
                maxLuminance = luminance
            }
            x += stepX
        }
        y += stepY
    }
    return maxLuminance < 16.0
}

private fun Bitmap.toGrayscaleJpegBytes(): ByteArray {
    val grayscaleBitmap = toGrayscale()
    return try {
        val stream = ByteArrayOutputStream(width * height / 2)
        if (!grayscaleBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
            error("JPEG encode failed")
        }
        stream.toByteArray()
    } finally {
        grayscaleBitmap.recycle()
    }
}

private fun Bitmap.toGrayscale(): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    canvas.drawBitmap(this, 0f, 0f, paint)
    return result
}
