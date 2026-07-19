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
            withTimeout(CAPTURE_TIMEOUT) {
                captureJpegLocked()
            }
        }
    }

    private suspend fun captureJpegLocked(): ByteArray {
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

                fun complete(result: Result<ByteArray>) {
                    if (!completed.compareAndSet(false, true)) {
                        return
                    }
                    imageReader.setOnImageAvailableListener(null, null)
                    result.fold(
                        onSuccess = { bytes ->
                            continuation.resume(bytes) { _, _, _ -> }
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
                        val encoded = try {
                            Result.success(image.toGrayscaleJpegBytes(width, height))
                        } catch (exception: Exception) {
                            Result.failure(exception)
                        } finally {
                            image.close()
                        }
                        complete(encoded)
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
        private const val IMAGE_READER_MAX_IMAGES = 2
        private val CAPTURE_TIMEOUT = 500.milliseconds
    }
}

private fun Image.toGrayscaleJpegBytes(width: Int, height: Int): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    buffer.rewind()

    val paddedWidth = width + rowPadding / pixelStride
    val source = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    source.copyPixelsFromBuffer(buffer)
    val colorBitmap = if (paddedWidth == width) {
        source
    } else {
        Bitmap.createBitmap(source, 0, 0, width, height).also { source.recycle() }
    }

    val grayscaleBitmap = colorBitmap.toGrayscale()
    if (grayscaleBitmap !== colorBitmap) {
        colorBitmap.recycle()
    }

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
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    canvas.drawBitmap(this, 0f, 0f, paint)
    return result
}
