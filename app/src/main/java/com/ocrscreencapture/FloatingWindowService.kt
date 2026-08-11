package com.ocrscreencapture

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ocrscreencapture.view.SelectionOverlayView
import kotlinx.coroutines.*

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
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        // بدء الخدمة في المقدمة (إلزامي لـ MediaProjection)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // استلام بيانات إذن التقاط الشاشة
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

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

    // ================ قناة الإشعار ================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "OCR Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "خدمة استخراج النصوص من الشاشة"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPI = PendingIntent.getService(this, 0,
            Intent(this, javaClass).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val openPI = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Screen Capture")
            .setContentText("اضغط الزر العائم لاستخراج النص")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPI)
            .addAction(0, "إيقاف", stopPI)
            .setOngoing(true)
            .build()
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

        try { windowManager.addView(btn, params) } catch (e: Exception) { e.printStackTrace() }
        floatingBtn = btn
    }

    private fun createFloatingButtonView(): View {
        val container = FrameLayout(this)
        val circle = TextView(this).apply {
            text = "T"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50")); setStroke(3, Color.WHITE)
            }
            setPadding(28, 28, 28, 28)
        }
        container.addView(circle, FrameLayout.LayoutParams(dp(56), dp(56)))

        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f; var isClick = true

        container.setOnTouchListener { _, ev ->
            val lp = floatingBtn?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = lp.x; initY = lp.y; touchX = ev.rawX; touchY = ev.rawY; isClick = true; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(ev.rawX - touchX) > 10 || kotlin.math.abs(ev.rawY - touchY) > 10) isClick = false
                    lp.x = initX + (ev.rawX - touchX).toInt()
                    lp.y = initY + (ev.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatingBtn, lp); true
                }
                MotionEvent.ACTION_UP -> { if (isClick) onFloatingClick(); true }
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
        val ov = SelectionOverlayView(this).apply {
            onExtract = { rect -> onExtractRequested(rect) }
            onCancel  = { dismissOverlay(); floatingBtn?.visibility = View.VISIBLE }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try { windowManager.addView(ov, params) } catch (e: Exception) {
            e.printStackTrace(); return
        }
        overlay = ov
    }

    private fun dismissOverlay() {
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    // ================ عملية الاستخراج ================

    private fun onExtractRequested(rect: RectF) {
        scope.launch {
            // 1) إخفاء أداة التحديد
            dismissOverlay()

            // 2) انتظار قصير حتى تُحدّث الشاشة بدون الواجهة
            delay(300)

            // 3) التقاط الشاشة
            val full = captureManager?.captureScreen()
            if (full == null) { floatingBtn?.visibility = View.VISIBLE; return@launch }

            // 4) قص المنطقة المحددة
            val cropped = captureManager?.cropBitmap(
                full, rect.left.toInt(), rect.top.toInt(),
                rect.right.toInt(), rect.bottom.toInt()
            )
            full.recycle()

            if (cropped == null) { floatingBtn?.visibility = View.VISIBLE; return@launch }

            // 5) تشغيل OCR في الخلفية
            val text = withContext(Dispatchers.Default) {
                try { ocrProcessor.processImage(cropped) } catch (_: Exception) { "" }
            }
            cropped.recycle()

            // 6) عرض النتيجة
            if (text.isBlank()) {
                // يمكنك عرض Toast هنا
            } else {
                startActivity(Intent(this@FloatingWindowService, TextResultActivity::class.java).apply {
                    putExtra("extracted_text", text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            }

            // 7) إعادة الزر العائم
            floatingBtn?.visibility = View.VISIBLE
        }
    }

    // ================ التنظيف ================

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        floatingBtn?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        captureManager?.release()
        ocrProcessor.close()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
