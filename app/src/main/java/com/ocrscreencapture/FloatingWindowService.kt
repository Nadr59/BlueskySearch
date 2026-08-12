package com.ocrscreencapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
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
        private const val TAG = "FloatingSvc"
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
    private val ocrProcessor by lazy { OcrProcessor(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debugView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

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

        if (code != 0 && data != null) {
            captureManager = ScreenCaptureManager(this, code, data)
            captureManager?.onProjectionStopped = {
                Log.w(TAG, "Projection stopped by system")
                scope.launch { showDebug("تم إلغاء إذن الشاشة", true) }
            }
            captureManager?.initialize()

            scope.launch {
                val ready = captureManager?.waitForFrames(5000) ?: false
                Log.d(TAG, "Frames ready: $ready")
                if (ready) {
                    showFloatingButton()
                } else {
                    showDebug("فشل تهيئة الالتقاط — أعد المحاولة", true)
                    delay(3000)
                    stopSelf()
                }
            }
        } else {
            showDebug("إذن الشاشة غير صالح", true)
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OCR Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "خدمة استخراج النصوص"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingWindowService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openIntent)
            .addAction(0, "إيقاف", stopIntent)
            .setOngoing(true)
            .build()
    }

    // ═══════════════ نافذة التشخيص ═══════════════

    private fun showDebug(msg: String, isError: Boolean = false) {
        Log.d(TAG, "DEBUG: $msg")
        try {
            if (debugView == null) {
                debugView = TextView(this).apply {
                    setBackgroundColor(0xEE000000.toInt())
                    textSize = 11f
                    setPadding(24, 16, 24, 16)
                    maxLines = 25
                    isVerticalScrollBarEnabled = true
                    movementMethod = ScrollingMovementMethod()
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 200
                }
                windowManager.addView(debugView, params)
            }
            debugView?.setTextColor(
                if (isError) 0xFFFF4444.toInt() else 0xFF44FF44.toInt()
            )
            debugView?.text = msg
            debugView?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Debug view error", e)
        }
    }

    private fun hideDebug() {
        debugView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        debugView = null
    }

    // ═══════════════ الزر العائم ═══════════════

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
            Log.d(TAG, "Floating button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating button", e)
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
            val layoutParams = floatingBtn?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams.x
                    initY = layoutParams.y
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
                    layoutParams.x = initX + dx.toInt()
                    layoutParams.y = initY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(floatingBtn, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onFloatingButtonClicked()
                    }
                    true
                }
                else -> false
            }
        }

        return container
    }

    // ═══════════════ أداة التحديد ═══════════════

    private fun onFloatingButtonClicked() {
        Log.d(TAG, "Floating button clicked")
        floatingBtn?.visibility = View.INVISIBLE
        hideDebug()
        showSelectionOverlay()
    }

    private fun showSelectionOverlay() {
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
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlay = null
    }

    // ═══════════════ عملية الاستخراج ═══════════════

    private fun onExtractRequested(rect: RectF) {
        scope.launch {
            try {
                showDebug("١/٥ — إخفاء أداة التحديد...")
                dismissOverlay()

                showDebug("٢/٥ — انتظار تحديث الشاشة...")
                delay(1500)

                if (captureManager?.isReady != true) {
                    showDebug("مدير الالتقاط غير جاهز!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                showDebug("٣/٥ — التقاط الشاشة...")
                val fullBitmap: Bitmap? = captureManager?.captureWithRetry(
                    maxAttempts = 15,
                    delayMs = 300
                )

                if (fullBitmap == null) {
                    showDebug("فشل التقاط الشاشة!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                showDebug("✓ التقاط: ${fullBitmap.width}x${fullBitmap.height}")
                delay(200)

                showDebug("٤/٥ — قص المنطقة...")
                val croppedBitmap: Bitmap? = withContext(Dispatchers.Default) {
                    captureManager?.cropBitmap(
                        fullBitmap,
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt()
                    )
                }
                fullBitmap.recycle()

                if (croppedBitmap == null) {
                    showDebug("فشل قص المنطقة!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                showDebug("✓ منطقة: ${croppedBitmap.width}x${croppedBitmap.height}")
                delay(200)

                showDebug("٥/٥ — استخراج النص...")
                ocrProcessor.clearDebugLog()

                val extractedText: String = withContext(Dispatchers.Default) {
                    try {
                        ocrProcessor.processImage(croppedBitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR error", e)
                        ""
                    }
                }
                croppedBitmap.recycle()

                if (extractedText.isBlank()) {
                    val diagnosticLog = ocrProcessor.debugLog.toString()
                    showDebug("لم يتم العثور على نص\n\n$diagnosticLog", true)
                    delay(15000)
                } else {
                    showDebug("✓ تم! ${extractedText.length} حرف")
                    delay(500)
                    try {
                        startActivity(
                            Intent(
                                this@FloatingWindowService,
                                TextResultActivity::class.java
                            ).apply {
                                putExtra("extracted_text", extractedText)
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "StartActivity failed", e)
                        showDebug("فشل فتح شاشة النتيجة!", true)
                        delay(3000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                showDebug("خطأ: ${e.message}", true)
                delay(5000)
            } finally {
                hideDebug()
                floatingBtn?.visibility = View.VISIBLE
            }
        }
    }

    // ═══════════════ التنظيف ═══════════════

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        scope.cancel()
        hideDebug()
        floatingBtn?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        captureManager?.release()
        ocrProcessor.close()
        floatingBtn = null
        overlay = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
