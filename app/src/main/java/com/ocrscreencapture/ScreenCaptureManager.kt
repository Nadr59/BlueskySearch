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
import android.view.WindowManager

/**
 * مدير التقاط الشاشة باستخدام MediaProjection API
 * يدير دورة حياة MediaProjection وال VirtualDisplay
 */
class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var latestBitmap: Bitmap? = null

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var screenWidth = 0;  private set
    var screenHeight = 0; private set
    var screenDpi = 0;    private set

    // Callback عند إيقاف المشاركة من قبل النظام
    var onProjectionStopped: (() -> Unit)? = null

    /**
     * تهيئة MediaProjection وإنشاء VirtualDisplay
     */
    fun initialize() {
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        // الحصول على أبعاد الشاشة الفعلية
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

        // إنشاء MediaProjection من بيانات الموافقة
        val projManager = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        mediaProjection = projManager.getMediaProjection(resultCode, resultData)

        // تسجيل callback للتعامل مع إيقاف المشاركة
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
                handler?.post { onProjectionStopped?.invoke() }
            }
        }, handler)

        // إنشاء ImageReader بتنسيق RGBA
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )

        // تحديث أحدث إطار عند توفره
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                latestBitmap = imageToBitmap(image)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }, handler)

        // إنشاء VirtualDisplay يعكس الشاشة
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "OCRScreenCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )
    }

    /**
     * التقاط نسخة من أحدث إطار على الشاشة
     */
    fun captureScreen(): Bitmap? {
        val src = latestBitmap ?: return null
        return if (!src.isRecycled) src.copy(Bitmap.Config.ARGB_8888, false) else null
    }

    /**
     * قص منطقة محددة من الصورة مع التحقق من الحدود
     */
    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    /**
     * تحويل Image (RGBA) إلى Bitmap
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
        } else bitmap
    }

    /**
     * تحرير جميع الموارد
     */
    fun release() {
        virtualDisplay?.release();   virtualDisplay = null
        imageReader?.close();        imageReader = null
        mediaProjection?.stop();     mediaProjection = null
        latestBitmap?.recycle();     latestBitmap = null
        handlerThread?.quitSafely(); handlerThread = null
    }
}
