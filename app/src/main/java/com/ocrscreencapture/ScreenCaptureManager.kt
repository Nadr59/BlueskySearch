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
import kotlinx.coroutines.delay

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
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var screenWidth = 0;  private set
    var screenHeight = 0; private set
    var screenDpi = 0;    private set
    var isReady = false;  private set

    var onProjectionStopped: (() -> Unit)? = null

    /**
     * تهيئة MediaProjection + VirtualDisplay + ImageReader
     */
    fun initialize() {
        Log.d(TAG, "=== INITIALIZE ===")

        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        // أبعاد الشاشة
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val m = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            screenWidth = m.widthPixels
            screenHeight = m.heightPixels
        }
        screenDpi = context.resources.displayMetrics.densityDpi
        Log.d(TAG, "Screen: ${screenWidth}x${screenHeight} dpi=$screenDpi")

        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "Invalid screen dimensions!")
            return
        }

        // إنشاء MediaProjection
        val projMgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projMgr.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            Log.e(TAG, "getMediaProjection returned null!")
            return
        }
        Log.d(TAG, "MediaProjection created")

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped by system!")
                isReady = false
                handler?.post { onProjectionStopped?.invoke() }
            }
        }, handler)

        // ✅ إنشاء ImageReader مع 3 buffers لضمان عدم فقدان الإطارات
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3)
        Log.d(TAG, "ImageReader created: ${screenWidth}x${screenHeight}")

        // ✅ إنشاء VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "OCRCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        if (virtualDisplay == null) {
            Log.e(TAG, "createVirtualDisplay returned null!")
            return
        }

        isReady = true
        Log.d(TAG, "=== READY ===")
    }

    /**
     * ✅ الانتظار حتى تتوفر إطارات من الشاشة
     */
    suspend fun waitForFrames(timeoutMs: Long = 5000): Boolean {
        Log.d(TAG, "Waiting for frames (timeout=${timeoutMs}ms)...")
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempts = 0
        while (System.currentTimeMillis() < deadline) {
            attempts++
            // محاولة مباشرة من ImageReader
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                image.close()
                Log.d(TAG, "Frame available after $attempts attempts")
                return true
            }
            delay(200)
        }
        Log.e(TAG, "No frames after $attempts attempts!")
        return false
    }

    /**
     * ✅ التقاط الشاشة مباشرة من ImageReader
     * لا يعتمد على callback — يحصل على الإطار مباشرة
     */
    fun captureScreen(): Bitmap? {
        val reader = imageReader
        if (reader == null) {
            Log.e(TAG, "captureScreen: ImageReader is null!")
            return null
        }
        if (mediaProjection == null) {
            Log.e(TAG, "captureScreen: MediaProjection is null!")
            return null
        }

        // ✅ محاولة مباشرة: احصل على آخر إطار
        val image: Image? = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "acquireLatestImage exception", e)
            null
        }

        if (image == null) {
            Log.w(TAG, "captureScreen: No image available")
            return null
        }

        try {
            val bitmap = imageToBitmap(image)
            Log.d(TAG, "captureScreen: SUCCESS ${bitmap.width}x${bitmap.height}")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "imageToBitmap failed", e)
            return null
        } finally {
            image.close()
        }
    }

    /**
     * ✅ التقاط مع إعادة المحاولة
     */
    suspend fun captureWithRetry(maxAttempts: Int = 15, delayMs: Long = 300): Bitmap? {
        for (i in 1..maxAttempts) {
            Log.d(TAG, "Capture attempt $i/$maxAttempts")
            val bitmap = captureScreen()
            if (bitmap != null) {
                Log.d(TAG, "Capture succeeded on attempt $i")
                return bitmap
            }
            delay(delayMs)
        }
        Log.e(TAG, "All $maxAttempts capture attempts failed!")
        return null
    }

    /**
     * قص منطقة من الصورة
     */
    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        Log.d(TAG, "Crop: ($l,$t,$r,$b) from ${bitmap.width}x${bitmap.height}")
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    /**
     * تحويل Image إلى Bitmap
     */
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
        } else {
            bitmap
        }
    }

    /**
     * تحرير الموارد
     */
    fun release() {
        Log.d(TAG, "Releasing resources")
        isReady = false
        virtualDisplay?.release();   virtualDisplay = null
        imageReader?.close();        imageReader = null
        mediaProjection?.stop();     mediaProjection = null
        handlerThread?.quitSafely(); handlerThread = null
        handler = null
    }
}
