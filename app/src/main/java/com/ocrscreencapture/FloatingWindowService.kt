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
        Log.d(TAG, "Service created")
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

        if (code != 0 && data != null) {
            captureManager = ScreenCaptureManager(this, code, data)
            captureManager?.onProjectionStopped = { stopSelf() }
            captureManager?.initialize()

            // ✅ الانتظار حتى يكون أول إطار جاهز قبل إظهار الزر
            scope.launch {
                val ready = captureManager?.waitForFirstFrame(5000) ?: false
                Log.d(TAG, "First frame ready: $ready")
                showFloatingButton()
            }
        } else {
            showNotificationError("فشل تهيئة — أعد المحاولة")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
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
            val ch = NotificationChannel(CHANNEL_ID, "OCR Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "خدمة استخراج النصوص"
            }
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
            .setContentTitle("OCR Screen Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPI)
            .addAction(0, "إيقاف", stopPI)
            .setOngoing(true)
            .build()
    }

    private fun showNotificationError(msg: String) {
        Log.e(TAG, "Error: $msg")
        try {
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OCR - خطأ").setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }

    // ================ الزر العائم ================

    private fun showFloatingButton() {
        if (floatingBtn != null) return
        val btn = createFloatingButtonView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }
        try {
            windowManager.addView(btn, params)
            floatingBtn = btn
            Log.d(TAG, "Floating button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show button", e)
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

        var initX = 0; var initY = 0; var tx = 0f; var ty = 0f; var click = true
        container.setOnTouchListener { _, ev ->
            val lp = floatingBtn?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = lp.x; initY = lp.y; tx = ev.rawX; ty = ev.rawY; click = true; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(ev.rawX - tx) > 10 || kotlin.math.abs(ev.rawY - ty) > 10) click = false
                    lp.x = initX + (ev.rawX - tx).toInt()
                    lp.y = initY + (ev.rawY - ty).toInt()
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
        Log.d(TAG, "Button clicked")
        floatingBtn?.visibility = View.INVISIBLE
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
                // 1) إخفاء أداة التحديد
                Log.d(TAG, "Step 1: Dismissing overlay")
                dismissOverlay()

                // 2) ✅ انتظار حتى تختفي الواجهة وتحدّث الشاشة
                Log.d(TAG, "Step 2: Waiting 1200ms for screen refresh...")
                delay(1200)

                // 3) ✅ محاولة الالتقاط مع إعادة المحاولة
                Log.d(TAG, "Step 3: Capturing screen...")
                var fullBitmap: Bitmap? = null
                for (attempt in 1..5) {
                    fullBitmap = captureManager?.captureScreen()
                    if (fullBitmap != null) {
                        Log.d(TAG, "Capture success on attempt $attempt: ${fullBitmap.width}x${fullBitmap.height}")
                        break
                    }
                    Log.w(TAG, "Capture attempt $attempt failed, waiting...")
                    delay(500)
                }

                if (fullBitmap == null) {
                    Log.e(TAG, "All capture attempts failed!")
                    showNotificationError("فشل التقاط الشاشة — تأكد من إعطاء إذن الشاشة")
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }

                // 4) قص المنطقة
                Log.d(TAG, "Step 4: Cropping: L=${rect.left} T=${rect.top} R=${rect.right} B=${rect.bottom}")
                val cropped = withContext(Dispatchers.Default) {
                    captureManager?.cropBitmap(fullBitmap,
                        rect.left.toInt(), rect.top.toInt(),
                        rect.right.toInt(), rect.bottom.toInt())
                }
                fullBitmap.recycle()

                if (cropped == null) {
                    Log.e(TAG, "Crop failed")
                    showNotificationError("فشل قص المنطقة")
                    floatingBtn?.visibility = View.VISIBLE
                    return@launch
                }
                Log.d(TAG, "Cropped: ${cropped.width}x${cropped.height}")

                // 5) OCR
                Log.d(TAG, "Step 5: Running OCR...")
                val text = try { ocrProcessor.processImage(cropped) } catch (e: Exception) {
                    Log.e(TAG, "OCR error", e); ""
                }
                cropped.recycle()
                Log.d(TAG, "OCR result: ${text.length} chars")

                // 6) عرض النتيجة
                if (text.isBlank()) {
                    Log.w(TAG, "No text found")
                    showNotificationError("لم يتم العثور على نص — جرّب منطقة أكبر أو نص أوضح")
                } else {
                    Log.d(TAG, "Opening result: ${text.take(50)}...")
                    try {
                        startActivity(Intent(this@FloatingWindowService, TextResultActivity::class.java).apply {
                            putExtra("extracted_text", text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "StartActivity failed", e)
                        showNotificationError("فشل فتح شاشة النتيجة")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                showNotificationError("خطأ: ${e.message}")
            } finally {
                // ✅ دائماً أعد الزر
                Log.d(TAG, "Restoring floating button")
                floatingBtn?.visibility = View.VISIBLE
            }
        }
    }

    // ================ التنظيف ================

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        scope.cancel()
        floatingBtn?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        captureManager?.release()
        ocrProcessor.close()
        floatingBtn = null; overlay = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
