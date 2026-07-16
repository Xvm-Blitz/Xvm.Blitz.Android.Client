package ru.xvmblitz.android.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureHelper(private val context: Context) {
    suspend fun captureGrayscalePng(resultCode: Int, data: Intent): ByteArray {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            ?: error("MediaProjection is null")

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val handlerThread = HandlerThread("xvm-capture").also { it.start() }
        val handler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        var virtualDisplay: VirtualDisplay? = null

        return try {
            delay(FRAME_SETTLE_DELAY_MS)
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                var acceptedFrames = 0

                val callback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (!resumed) {
                            resumed = true
                            continuation.resumeWithException(IllegalStateException("MediaProjection stopped"))
                        }
                    }
                }
                mediaProjection.registerCallback(callback, handler)

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
                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            if (cropped !== bitmap) {
                                bitmap.recycle()
                            }
                            val grayscale = toGrayscale(cropped)
                            cropped.recycle()
                            val pngBytes = grayscale.toPngBytes()
                            grayscale.recycle()
                            resumed = true
                            continuation.resume(pngBytes)
                        } catch (exception: Exception) {
                            if (!resumed) {
                                resumed = true
                                continuation.resumeWithException(exception)
                            }
                        } finally {
                            image.close()
                        }
                    },
                    handler,
                )

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

                continuation.invokeOnCancellation {
                    virtualDisplay?.release()
                    imageReader.close()
                    mediaProjection.stop()
                    handlerThread.quitSafely()
                }
            }
        } finally {
            virtualDisplay?.release()
            imageReader.close()
            mediaProjection.stop()
            handlerThread.quitSafely()
        }
    }

    private fun toGrayscale(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val gray = (
                0.299 * Color.red(color) +
                    0.587 * Color.green(color) +
                    0.114 * Color.blue(color)
                ).toInt().coerceIn(0, 255)
            pixels[index] = Color.rgb(gray, gray, gray)
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    companion object {
        private const val FRAME_SETTLE_DELAY_MS = 150L
        private const val FRAMES_TO_SKIP_BEFORE_CAPTURE = 2
    }
}
