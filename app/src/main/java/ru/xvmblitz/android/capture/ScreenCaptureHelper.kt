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
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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

    suspend fun captureGrayscaleJpeg(): ByteArray {
        check(!closed) { "ScreenCaptureSession is closed" }
        delay(FRAME_SETTLE_DELAY_MS)
        val colorBitmap = awaitColorBitmap()
        return try {
            withContext(Dispatchers.Default) {
                colorBitmap.toGrayscaleJpegBytes()
            }
        } finally {
            colorBitmap.recycle()
        }
    }

    private suspend fun awaitColorBitmap(): Bitmap {
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        var virtualDisplay: VirtualDisplay? = null
        return try {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                var acceptedFrames = 0

                imageReader.setOnImageAvailableListener(
                    { reader ->
                        if (resumed) {
                            return@setOnImageAvailableListener
                        }
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            acceptedFrames += 1
                            if (acceptedFrames < FRAMES_TO_SKIP_BEFORE_CAPTURE) {
                                return@setOnImageAvailableListener
                            }

                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * width
                            buffer.rewind()
                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = if (rowPadding == 0) {
                                bitmap
                            } else {
                                Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
                            }
                            resumed = true
                            imageReader.setOnImageAvailableListener(null, null)
                            continuation.resume(cropped)
                        } catch (exception: Exception) {
                            if (!resumed) {
                                resumed = true
                                imageReader.setOnImageAvailableListener(null, null)
                                continuation.resumeWithException(exception)
                            }
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
                    if (!resumed) {
                        resumed = true
                        continuation.resumeWithException(exception)
                    }
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    imageReader.setOnImageAvailableListener(null, null)
                    virtualDisplay?.release()
                    imageReader.close()
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
        private const val FRAME_SETTLE_DELAY_MS = 150L
        private const val FRAMES_TO_SKIP_BEFORE_CAPTURE = 2
    }
}

private val grayscalePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
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

private fun Bitmap.toGrayscaleJpegBytes(): ByteArray {
    val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(grayscale).drawBitmap(this, 0f, 0f, grayscalePaint)
    return try {
        val stream = ByteArrayOutputStream(width * height)
        if (!grayscale.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
            error("JPEG encode failed")
        }
        stream.toByteArray()
    } finally {
        grayscale.recycle()
    }
}
