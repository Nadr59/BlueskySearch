package com.ocrscreencapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
        const val NOTIFICATION_ID = 1001
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ✅ بدء الخدمة في المقدمة مع دعم الأنواع المختلفة
        startForegroundCompat()

        // استلام بيانات إذن التقاط الشاشة
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (code != 0 && data != null) {
            captureManager = ScreenCaptureManager(this, code, data)
            captureManager?.onProjectionStopped = { stopSelf() }
            captureManager?.initialize()
            showFloatingButton()
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    // ✅ دالة مساعدة لحل مشكلة ServiceInfo
    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ يتطلب تحديد نوع الخدمة
            // نستخدم try-catch لأن بعض الأجهزة قد لا تدعم هذا الثابت
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (e: Exception) {
                // Fallback للأجهزة التي لا تدعم نوع mediaProjection
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ================ قناة الإشعار ================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OCR Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة استخراج النصوص من الشاشة"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingWindowService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Screen Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openIntent)
            .addAction(0, "إيقاف", stopIntent)
            .setOngoing(true)
            .build()
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
            x = 24
            y = 300
        }

        try {
            windowManager.addView(button, params)
            floatingBtn = button
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createFloatingButtonView(): View {
        val container = FrameLayout(this)
        val circle = TextView(this).apply {
            text = "T"
            textSize = 22f
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

        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var isClick = true

        container.setOnTouchListener { _, event ->
            val lp = floatingBtn?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = lp.x
                    initY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isClick = false
                    }
                    lp.x = initX + dx.toInt()
                    lp.y = initY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(floatingBtn, lp)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) onFloatingClick()
                    true
                }
                else -> false
            }
        }
        return container
    }

    // ================ أداة التحديد ================

    private fun onFloatingClick() {
        floatingBtn?.visibility = View.INVISIBLE
        showOverlay()
    }

    private fun showOverlay() {
        val overlayView = SelectionOverlayView(this).apply {
            onExtract = { rect -> onExtractRequested(rect) }
            onCancel = {
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
        } catch (e: Exception) {
            e.printStackTrace()
            floatingBtn?.visibility = View.VISIBLE
        }
    }

    private fun dismissOverlay() {
        overlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlay = null
    }

    // ================ عملية الاستخراج ================

    private fun onExtractRequested(rect: android.graphics.RectF) {
        scope.launch {
            // 1) إخفاء أداة التحديد
            dismissOverlay()

            // 2) انتظار قصير حتى تُحدّث الشاشة
            delay(300)

            // 3) التقاط الشاشة
            val fullBitmap = captureManager?.captureScreen()
            if (fullBitmap == null) {
                floatingBtn?.visibility = View.VISIBLE
                return@launch
            }

            // 4) قص المنطقة المحددة
            val croppedBitmap = captureManager?.cropBitmap(
                fullBitmap,
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            fullBitmap.recycle()

            if (croppedBitmap == null) {
                floatingBtn?.visibility = View.VISIBLE
                return@launch
            }

            // 5) تشغيل OCR في الخلفية
            val extractedText = try {
                ocrProcessor.processImage(croppedBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            croppedBitmap.recycle()

            // 6) عرض النتيجة
            if (extractedText.isBlank()) {
                android.widget.Toast.makeText(
                    this@FloatingWindowService,
                    "لم يتم العثور على نص في المنطقة المحددة",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                startActivity(
                    Intent(this@FloatingWindowService, TextResultActivity::class.java).apply {
                        putExtra("extracted_text", extractedText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }

            // 7) إعادة الزر العائم
            floatingBtn?.visibility = View.VISIBLE
        }
    }

    // ================ التنظيف ================

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        floatingBtn?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        captureManager?.release()
        ocrProcessor.close()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
