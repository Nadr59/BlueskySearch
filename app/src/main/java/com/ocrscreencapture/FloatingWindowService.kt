package com.ocrscreencapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ocrscreencapture.view.SelectionOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingService"
        const val NOTIFICATION_ID = 1001
        const val ERROR_NOTIFICATION_ID = 9999
        const val CHANNEL_ID = "ocr_channel"
        const val ACTION_STOP = "com.ocrscreencapture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
    }

    private lateinit var windowManager: WindowManager
    private var floatingBtn: View? = null
    private var overlay: SelectionOverlayView? = null
    private var captureManager: ScreenCaptureManager? = null
    private val ocrProcessor = OcrProcessor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        Log.d(TAG, "resultCode=$code hasData=${data != null}")

        if (code != 0 && data != null) {
            captureManager = ScreenCaptureManager(this, code, data)
            captureManager?.onProjectionStopped = {
                Log.w(TAG, "MediaProjection stopped by system")
                stopSelf()
            }
            captureManager?.initialize()
            Log.d(TAG, "ScreenCaptureManager initialized")
            showFloatingButton()
        } else {
            Log.e(TAG, "Invalid resultCode or data — stopping")
            showErrorMessage("فشل تهيئة التقاط الشاشة — أعد المحاولة")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ================ الإشعارات ================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "OCR Service", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "خدمة استخراج النصوص" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopPI = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPI = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Screen Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPI)
            .addAction(0, "إيقاف", stopPI)
            .setOngoing(true)
            .build()
    }

    /**
     * ✅ إظهار رسالة خطأ كإشعار (بدلاً من Toast الذي قد لا يعمل من Service)
     */
    private fun showErrorMessage(message: String) {
        Log.e(TAG, "Error: $message")
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OCR - خطأ")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error notification", e)
        }
    }

    /**
     * ✅ إظهار رسالة نجاح كإشعار
     */
    private fun showSuccessMessage(message: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OCR - تم الاستخراج")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setAutoCancel(true)
                .build()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show success notification", e)
        }
    }

    // ================ الزر العائم ================

    private fun showFloatingButton() {
        if (floatingBtn != null) return
        val button = createFloatingButtonView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 300
        }
        try {
            windowManager.addView(button, params)
            floatingBtn = button
            Log.d(TAG, "Floating button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating button", e)
        }
    }

    private fun createFloatingButtonView(): View {
        val container = FrameLayout(this)
        val circle = TextView(this).apply {
            text = "T"; textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#4CAF50"))
                setStroke(3, android.graphics.Color.WHITE)
            }
            setPadding(28, 28, 28, 28)
        }
        container.addView(circle, FrameLayout.LayoutParams(dp(56), dp(56)))

        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f; var isClick = true

        container.setOnTouchListener { _, event ->
            val lp = floatingBtn?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = lp.x; initY = lp.y
                    touchX = event.rawX; touchY = event.rawY
                    isClick = true; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - touchX) > 10 ||
                        kotlin.math.abs(event.rawY - touchY) > 10) isClick = false
                    lp.x = initX + (event.rawX - touchX).toInt()
                    lp.y = initY + (event.rawY - touchY).toInt()
                    try { windowManager.updateViewLayout(floatingBtn, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> { if (isClick) onFloatingClick(); true }
                else -> false
            }
        }
        return container
    }

    // ================ أداة التحديد ================

    private fun onFloatingClick() {
        Log.d(TAG, "Floating button clicked")
        floatingBtn?.visibility = View.INVISIBLE
        showOverlay()
    }

    private fun showOverlay() {
        val overlayView = SelectionOverlayView(this).apply {
            onExtract = { rect ->
                Log.d(TAG, "Extract requested: $rect")
                onExtractRequested(rect)
            }
            onCancel = {
                Log.d(TAG, "Selection cancelled")
                dismissOverlay()
                floatingBtn?.visibility = View.VISIBLE
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(overlayView, params)
            overlay = overlayView
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            floatingBtn?.visibility = View.VISIBLE
        }
    }

    private fun dismissOverlay() {
        overlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    // ================ عملية الاستخراج ================

    private fun onExtractRequested(rect: RectF) {
        scope.launch {
            try {
                // 1) إخفاء أداة التحديد
                Log.d(TAG, "Step 1: Dismissing overlay")
                dismissOverlay()

                // 2) ✅ انتظار أطول (800ms) حتى تختفي الواجهة وتحدّث الشاشة
                Log.d(TAG, "Step 2: Waiting for screen refresh...")
                delay(800)

                // 3) التقاط الشاشة
                Log.d(TAG, "Step 3: Capturing screen...")
                val fullBitmap = captureManager?.captureScreen()
                if (fullBitmap == null) {
                    Log.e(TAG, "captureScreen returned null!")
                    showErrorMessage("فشل التقاط الشاشة — تأكد من إعطاء إذن الشاشة")
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }
                Log.d(TAG, "Captured: ${fullBitmap.width}x${fullBitmap.height}")

                // 4) قص المنطقة
                Log.d(TAG, "Step 4: Cropping region: L=${rect.left} T=${rect.top} R=${rect.right} B=${rect.bottom}")
                val croppedBitmap = withContext(Dispatchers.Default) {
                    captureManager?.cropBitmap(
                        fullBitmap,
                        rect.left.toInt(), rect.top.toInt(),
                        rect.right.toInt(), rect.bottom.toInt()
                    )
                }
                fullBitmap.recycle()

                if (croppedBitmap == null) {
                    Log.e(TAG, "cropBitmap returned null!")
                    showErrorMessage("فشل قص المنطقة المحددة")
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }
                Log.d(TAG, "Cropped: ${croppedBitmap.width}x${croppedBitmap.height}")

                // 5) تشغيل OCR
                Log.d(TAG, "Step 5: Running OCR...")
                val extractedText = try {
                    ocrProcessor.processImage(croppedBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "OCR exception", e)
                    ""
                }
                croppedBitmap.recycle()
                Log.d(TAG, "OCR result: ${extractedText.length} chars, text='${extractedText.take(100)}'")

                // 6) عرض النتيجة
                if (extractedText.isBlank()) {
                    Log.w(TAG, "No text found")
                    showErrorMessage("لم يتم العثور على نص — جرّب تحديد منطقة أكبر أو نص أوضح")
                } else {
                    Log.d(TAG, "Opening result activity...")
                    try {
                        startActivity(
                            Intent(this@FloatingWindowService, TextResultActivity::class.java).apply {
                                putExtra("extracted_text", extractedText)
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                        )
                        showSuccessMessage("تم استخراج ${extractedText.length} حرف")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start TextResultActivity", e)
                        showErrorMessage("فشل فتح شاشة النتيجة: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in extraction", e)
                showErrorMessage("خطأ غير متوقع: ${e.message}")
            } finally {
                // 7) ✅ دائماً أعد الزر العائم
                Log.d(TAG, "Restoring floating button")
                floatingBtn?.visibility = View.VISIBLE
            }
        }
    }

    // ================ التنظيف ================

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        scope.cancel()
        floatingBtn?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        captureManager?.release()
        ocrProcessor.close()
        floatingBtn = null
        overlay = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
