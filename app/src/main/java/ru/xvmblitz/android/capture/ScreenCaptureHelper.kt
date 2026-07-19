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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayReady = false
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
        ensureVirtualDisplay()
        if (!displayReady) {
            delay(FRAME_SETTLE_DELAY_MS)
        }
        val framesToSkip = if (displayReady) 0 else FRAMES_TO_SKIP_BEFORE_CAPTURE
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            var acceptedFrames = 0
            val reader = imageReader ?: run {
                continuation.resumeWithException(IllegalStateException("ImageReader is null"))
                return@suspendCancellableCoroutine
            }

            reader.setOnImageAvailableListener(
                { availableReader ->
                    if (resumed) {
                        return@setOnImageAvailableListener
                    }
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        acceptedFrames += 1
                        if (acceptedFrames <= framesToSkip) {
                            return@setOnImageAvailableListener
                        }

                        val jpegBytes = image.toGrayscaleJpegBytes(width, height, JPEG_QUALITY)
                        displayReady = true
                        resumed = true
                        availableReader.setOnImageAvailableListener(null, null)
                        continuation.resume(jpegBytes)
                    } catch (exception: Exception) {
                        if (!resumed) {
                            resumed = true
                            availableReader.setOnImageAvailableListener(null, null)
                            continuation.resumeWithException(exception)
                        }
                    } finally {
                        image.close()
                    }
                },
                handler,
            )

            continuation.invokeOnCancellation {
                reader.setOnImageAvailableListener(null, null)
            }
        }
    }

    private fun ensureVirtualDisplay() {
        if (imageReader != null && virtualDisplay != null) {
            return
        }
        releaseDisplay()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "xvm-blitz-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        displayReady = false
    }

    private fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        displayReady = false
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        releaseDisplay()
        runCatching { mediaProjection.stop() }
        handlerThread.quitSafely()
    }

    companion object {
        private const val FRAME_SETTLE_DELAY_MS = 50L
        private const val FRAMES_TO_SKIP_BEFORE_CAPTURE = 1
        private const val JPEG_QUALITY = 88
    }
}

private fun Image.toGrayscaleJpegBytes(width: Int, height: Int, quality: Int): ByteArray {
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
        val stream = ByteArrayOutputStream(width * height / 4)
        if (!grayscaleBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
            error("JPEG compress failed")
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
