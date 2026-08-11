package com.ocrscreencapture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent
) {
    companion object {
        private const val TAG = "ScreenCapture"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var latestBitmap: Bitmap? = null

    // ✅ هذا يخبرنا متى يكون أول إطار جاهز
    private val firstFrameLatch = CountDownLatch(1)

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var screenWidth = 0;  private set
    var screenHeight = 0; private set
    var screenDpi = 0;    private set

    var onProjectionStopped: (() -> Unit)? = null

    fun initialize() {
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        screenDpi = context.resources.displayMetrics.densityDpi

        val projManager = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        mediaProjection = projManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped by system")
                release()
                handler?.post { onProjectionStopped?.invoke() }
            }
        }, handler)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                latestBitmap = imageToBitmap(image)
                // ✅ إشارة أن أول إطار وصل
                if (firstFrameLatch.count > 0) {
                    firstFrameLatch.countDown()
                    Log.d(TAG, "First frame captured: ${latestBitmap?.width}x${latestBitmap?.height}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "OCRScreenCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        Log.d(TAG, "Initialized: ${screenWidth}x${screenHeight} dpi=$screenDpi")
    }

    /**
     * ✅ الانتظار حتى يكون أول إطار جاهز
     */
    suspend fun waitForFirstFrame(timeoutMs: Long = 5000): Boolean {
        return withContext(Dispatchers.IO) {
            val ready = firstFrameLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
            Log.d(TAG, "waitForFirstFrame: ready=$ready")
            ready
        }
    }

    /**
     * ✅ التحقق من وجود إطارات
     */
    fun hasFrames(): Boolean = firstFrameLatch.count == 0L

    fun captureScreen(): Bitmap? {
        val src = latestBitmap
        if (src == null) {
            Log.w(TAG, "captureScreen: latestBitmap is null")
            return null
        }
        if (src.isRecycled) {
            Log.w(TAG, "captureScreen: latestBitmap is recycled")
            return null
        }
        return try {
            val copy = src.copy(Bitmap.Config.ARGB_8888, false)
            Log.d(TAG, "captureScreen: success ${copy.width}x${copy.height}")
            copy
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen: copy failed", e)
            null
        }
    }

    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        Log.d(TAG, "cropBitmap: ($l,$t,$r,$b) from ${bitmap.width}x${bitmap.height}")
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmpWidth = image.width + rowPadding / pixelStride

        val bitmap = Bitmap.createBitmap(bmpWidth, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                bitmap.recycle()
            }
        } else bitmap
    }

    fun release() {
        Log.d(TAG, "Releasing resources")
        virtualDisplay?.release();   virtualDisplay = null
        imageReader?.close();        imageReader = null
        mediaProjection?.stop();     mediaProjection = null
        latestBitmap?.recycle();     latestBitmap = null
        handlerThread?.quitSafely(); handlerThread = null
    }
}
