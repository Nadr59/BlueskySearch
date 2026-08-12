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

    // ✅ نافذة تشخيصية مرئية
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
                Log.w(TAG, "Projection stopped!")
                scope.launch { showDebug("⚠️ تم إلغاء إذن الشاشة!", true) }
            }
            captureManager?.initialize()

            // ✅ الانتظار حتى تتوفر الإطارات قبل إظهار الزر
            scope.launch {
                val ready = captureManager?.waitForFrames(5000) ?: false
                Log.d(TAG, "Frames ready: $ready")
                if (ready) {
                    showFloatingButton()
                } else {
                    showDebug("⚠️ فشل تهيئة الالتقاط — أعد المحاولة", true)
                    delay(3000)
                    stopSelf()
                }
            }
        } else {
            showDebug("⚠️ إذن الشاشة غير صالح", true)
            stopSelf()
        }

        return START_STICKY
    }

    // ================ نافذة التشخيص المرئية ================

    private fun showDebug(msg: String, isError: Boolean = false) {
        Log.d(TAG, "DEBUG: $msg")
        try {
            if (debugView == null) {
                debugView = TextView(this).apply {
                    setBackgroundColor(0xDD000000.toInt())
                    textSize = 14f
                    setPadding(24, 16, 24, 16)
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 300
                }
                windowManager.addView(debugView, params)
            }
            debugView?.setTextColor(if (isError) 0xFFFF4444.toInt() else 0xFF44FF44.toInt())
            debugView?.text = msg
            debugView?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Debug view error: ${e.message}")
        }
    }

    private fun hideDebug() {
        debugView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        debugView = null
    }

    // ================ foreground service ================

    private fun startForegroundCompat() {
        val n = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "OCR Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPI = PendingIntent.getService(this, 0,
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val openPI = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPI)
            .addAction(0, "إيقاف", stopPI)
            .setOngoing(true).build()
    }

    // ================ الزر العائم ================

    private fun showFloatingButton() {
        if (floatingBtn != null) return
        val btn = createFloatingButtonView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }
        try {
            windowManager.addView(btn, params)
            floatingBtn = btn
            Log.d(TAG, "Floating button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Button failed", e)
        }
    }

    private fun createFloatingButtonView(): View {
        val container = FrameLayout(this)
        val circle = TextView(this).apply {
            text = "T"; textSize = 22f
            setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#4CAF50"))
                setStroke(3, android.graphics.Color.WHITE)
            }
            setPadding(28, 28, 28, 28)
        }
        container.addView(circle, FrameLayout.LayoutParams(dp(56), dp(56)))

        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var click = true
        container.setOnTouchListener { _, ev ->
            val lp = floatingBtn?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = lp.x; iy = lp.y; tx = ev.rawX; ty = ev.rawY; click = true; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(ev.rawX - tx) > 10 || kotlin.math.abs(ev.rawY - ty) > 10) click = false
                    lp.x = ix + (ev.rawX - tx).toInt(); lp.y = iy + (ev.rawY - ty).toInt()
                    try { windowManager.updateViewLayout(floatingBtn, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> { if (click) onFloatingClick(); true }
                else -> false
            }
        }
        return container
    }

    // ================ أداة التحديد ================

    private fun onFloatingClick() {
        Log.d(TAG, "Button clicked — showing overlay")
        floatingBtn?.visibility = View.INVISIBLE
        hideDebug()
        showOverlay()
    }

    private fun showOverlay() {
        val ov = SelectionOverlayView(this).apply {
            onExtract = { rect -> onExtractRequested(rect) }
            onCancel = { dismissOverlay(); floatingBtn?.visibility = View.VISIBLE }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(ov, params); overlay = ov
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Overlay failed", e); floatingBtn?.visibility = View.VISIBLE
        }
    }

    private fun dismissOverlay() {
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    // ================ ✅ عملية الاستخراج المصححة ================

    private fun onExtractRequested(rect: RectF) {
        scope.launch {
            try {
                // === الخطوة 1: إخفاء أداة التحديد ===
                showDebug("١/٥ — جاري إخفاء أداة التحديد...")
                dismissOverlay()

                // === الخطوة 2: انتظار تحديث الشاشة ===
                showDebug("٢/٥ — انتظار تحديث الشاشة...")
                delay(1500)

                // === الخطوة 3: التحقق من جاهزية المدير ===
                if (captureManager?.isReady != true) {
                    showDebug("❌ مدير الالتقاط غير جاهز!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                // === الخطوة 4: التقاط الشاشة ===
                showDebug("٣/٥ — جاري التقاط الشاشة...")
                val fullBitmap: Bitmap? = captureManager?.captureWithRetry(maxAttempts = 15, delayMs = 300)

                if (fullBitmap == null) {
                    showDebug("❌ فشل التقاط الشاشة — لا توجد إطارات!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                showDebug("✓ تم الالتقاط: ${fullBitmap.width}x${fullBitmap.height}")
                delay(300)

                // === الخطوة 5: قص المنطقة ===
                showDebug("٤/٥ — جاري قص المنطقة...")
                val cropped: Bitmap? = withContext(Dispatchers.Default) {
                    captureManager?.cropBitmap(
                        fullBitmap,
                        rect.left.toInt(), rect.top.toInt(),
                        rect.right.toInt(), rect.bottom.toInt()
                    )
                }
                fullBitmap.recycle()

                if (cropped == null) {
                    showDebug("❌ فشل قص المنطقة!", true)
                    delay(3000)
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                showDebug("✓ المنطقة: ${cropped.width}x${cropped.height}")
                delay(200)

                // === الخطوة 6: OCR ===
                showDebug("٥/٥ — جاري استخراج النص (OCR)...")
                val text: String = withContext(Dispatchers.Default) {
                    try { ocrProcessor.processImage(cropped) } catch (e: Exception) {
                        Log.e(TAG, "OCR error", e); ""
                    }
                }
                cropped.recycle()

                // === الخطوة 7: عرض النتيجة ===
        
if (text.isBlank()) {
    showDebug("❌ لم يتم العثور على نص — راجع Logcat", true)
    delay(3000)
} else if (text.startsWith("خطأ")) {
    // ✅ عرض رسالة الخطأ بدلاً من افتراض عدم وجود نص
    showDebug("❌ $text", true)
    delay(5000)
} else {
    // ✅ النص المستخرج
    showDebug("✓ تم! ${text.length} حرف", false)
    delay(500)
    try {
        startActivity(
            Intent(this@FloatingWindowService, TextResultActivity::class.java).apply {
                putExtra("extracted_text", text)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    } catch (e: Exception) {
        Log.e(TAG, "StartActivity failed", e)
        showDebug("❌ فشل فتح شاشة النتيجة!", true)
        delay(3000)
    }
}

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                showDebug("❌ خطأ: ${e.message}", true)
                delay(3000)
            } finally {
                hideDebug()
                floatingBtn?.visibility = View.VISIBLE
            }
        }
    }

    // ================ التنظيف ================

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        scope.cancel()
        hideDebug()
        floatingBtn?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        captureManager?.release()
        ocrProcessor.close()
        floatingBtn = null; overlay = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
