package com.ocrscreencapture.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/**
 * واجهة مخصصة لتحديد منطقة على الشاشة
 * تدعم الرسم بالإصبع واحد، التعديل بالمقابض، والتحريك
 */
class SelectionOverlayView(context: Context) : View(context) {

    enum class Mode { IDLE, DRAWING, ADJUSTING }

    var mode = Mode.IDLE; private set
    var selectionRect = RectF(); private set

    // --- متغيرات تتبع اللمس ---
    private var startX = 0f
    private var startY = 0f
    private var activeHandle = -1 // -1=لا شيء, 0-3=زوايا, 4=تحريك

    // --- المقاسات ---
    private val handleRadius = 28f
    private val handleTouchRadius = 64f
    private val minSelectionSize = 50f
    private val btnH = 64f
    private val btnW = 220f
    private val btnGap = 20f
    private val btnMargin = 16f

    // --- الرسومات ---
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000"); style = Paint.Style.FILL
    }
    private val clearPaint = Paint().apply {
        color = Color.TRANSPARENT; style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f); isAntiAlias = true
    }
    private val handleFillPaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val handleStrokePaint = Paint().apply {
        color = Color.parseColor("#4CAF50"); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#30FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val sizeTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
        isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    // زر الاستخراج
    val extractBtnRect = RectF()
    private val extractPaint = Paint().apply {
        color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL; isAntiAlias = true
    }

    // زر الإلغاء
    val cancelBtnRect = RectF()
    private val cancelPaint = Paint().apply {
        color = Color.parseColor("#F44336"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val btnTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER
        isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }

    // Callbacks
    var onExtract: ((RectF) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    // ====================== الرسم ======================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 1) الطبقة الداكنة
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        if (!selectionRect.isEmpty &&
            selectionRect.width() > minSelectionSize &&
            selectionRect.height() > minSelectionSize
        ) {
            // 2) مسح منطقة التحديد
            canvas.drawRect(selectionRect, clearPaint)

            // 3) شبكة قاعدة الأثلاث
            drawGrid(canvas)

            // 4) الحدود المتقطعة
            canvas.drawRect(selectionRect, borderPaint)

            // 5) المقابض
            drawHandles(canvas)

            // 6) نص المساحة
            canvas.drawText(
                "${selectionRect.width().toInt()} × ${selectionRect.height().toInt()}",
                selectionRect.centerX(), selectionRect.top - 14f, sizeTextPaint
            )

            // 7) الأزرار
            computeButtonRects()
            drawButton(canvas, extractBtnRect, extractPaint, "استخراج")
            drawButton(canvas, cancelBtnRect, cancelPaint, "إلغاء")
        }
        canvas.restoreToCount(sc)
    }

    private fun drawGrid(c: Canvas) {
        val w = selectionRect.width(); val h = selectionRect.height()
        for (i in 1..2) {
            c.drawLine(selectionRect.left + w * i / 3, selectionRect.top,
                selectionRect.left + w * i / 3, selectionRect.bottom, gridPaint)
            c.drawLine(selectionRect.left, selectionRect.top + h * i / 3,
                selectionRect.right, selectionRect.top + h * i / 3, gridPaint)
        }
    }

    private fun drawHandles(c: Canvas) {
        arrayOf(
            selectionRect.left to selectionRect.top,
            selectionRect.right to selectionRect.top,
            selectionRect.left to selectionRect.bottom,
            selectionRect.right to selectionRect.bottom
        ).forEach { (x, y) ->
            c.drawCircle(x, y, handleRadius, handleStrokePaint)
            c.drawCircle(x, y, handleRadius - 3, handleFillPaint)
        }
    }

    private fun computeButtonRects() {
        if (selectionRect.isEmpty) return
        val total = btnW * 2 + btnGap
        val left = selectionRect.centerX() - total / 2
        val y = if (selectionRect.bottom + btnMargin + btnH < height)
            selectionRect.bottom + btnMargin
        else selectionRect.top - btnMargin - btnH
        extractBtnRect.set(left, y, left + btnW, y + btnH)
        cancelBtnRect.set(left + btnW + btnGap, y, left + 2 * btnW + btnGap, y + btnH)
    }

    private fun drawButton(c: Canvas, rect: RectF, paint: Paint, label: String) {
        c.drawRoundRect(rect, 16f, 16f, paint)
        val ty = rect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2
        c.drawText(label, rect.centerX(), ty, btnTextPaint)
    }

    // ====================== اللمس ======================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> onDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> onMove(event.x, event.y)
            MotionEvent.ACTION_UP   -> onUp()
        }
        invalidate()
        return true
    }

    private fun onDown(x: Float, y: Float) {
        when (mode) {
            Mode.IDLE -> {
                startX = x; startY = y
                selectionRect.set(x, y, x, y)
                mode = Mode.DRAWING
            }
            Mode.ADJUSTING -> {
                computeButtonRects()
                when {
                    extractBtnRect.contains(x, y) -> {
                        if (selectionRect.width() > minSelectionSize)
                            onExtract?.invoke(RectF(selectionRect))
                        return
                    }
                    cancelBtnRect.contains(x, y) -> { onCancel?.invoke(); return }
                    else -> {
                        activeHandle = findHandle(x, y)
                        if (activeHandle >= 0) return
                        if (selectionRect.contains(x, y)) {
                            activeHandle = 4; startX = x; startY = y; return
                        }
                        // بدء تحديد جديد
                        startX = x; startY = y
                        selectionRect.set(x, y, x, y)
                        mode = Mode.DRAWING
                    }
                }
            }
            Mode.DRAWING -> {}
        }
    }

    private fun onMove(x: Float, y: Float) {
        when (mode) {
            Mode.DRAWING -> {
                selectionRect.set(
                    minOf(startX, x), minOf(startY, y),
                    maxOf(startX, x), maxOf(startY, y)
                )
            }
            Mode.ADJUSTING -> {
                when (activeHandle) {
                    0 -> { selectionRect.left = minOf(x, selectionRect.right - 20)
                           selectionRect.top  = minOf(y, selectionRect.bottom - 20) }
                    1 -> { selectionRect.right = maxOf(x, selectionRect.left + 20)
                           selectionRect.top   = minOf(y, selectionRect.bottom - 20) }
                    2 -> { selectionRect.left   = minOf(x, selectionRect.right - 20)
                           selectionRect.bottom = maxOf(y, selectionRect.top + 20) }
                    3 -> { selectionRect.right  = maxOf(x, selectionRect.left + 20)
                           selectionRect.bottom = maxOf(y, selectionRect.top + 20) }
                    4 -> {
                        selectionRect.offset(x - startX, y - startY)
                        // منع الخروج من حدود الشاشة
                        if (selectionRect.left < 0)   selectionRect.offset(-selectionRect.left, 0f)
                        if (selectionRect.top < 0)    selectionRect.offset(0f, -selectionRect.top)
                        if (selectionRect.right > width)  selectionRect.offset(width - selectionRect.right, 0f)
                        if (selectionRect.bottom > height) selectionRect.offset(0f, height - selectionRect.bottom)
                        startX = x; startY = y
                    }
                }
            }
            else -> {}
        }
    }

    private fun onUp() {
        if (mode == Mode.DRAWING) {
            if (selectionRect.width() > minSelectionSize && selectionRect.height() > minSelectionSize)
                mode = Mode.ADJUSTING
            else { selectionRect.setEmpty(); mode = Mode.IDLE }
        }
        activeHandle = -1
    }

    private fun findHandle(x: Float, y: Float): Int {
        val points = arrayOf(
            selectionRect.left to selectionRect.top,
            selectionRect.right to selectionRect.top,
            selectionRect.left to selectionRect.bottom,
            selectionRect.right to selectionRect.bottom
        )
        return points.indexOfFirst { (hx, hy) ->
            val dx = x - hx; val dy = y - hy
            dx * dx + dy * dy <= handleTouchRadius * handleTouchRadius
        }
    }

    fun resetSelection() {
        selectionRect.setEmpty(); mode = Mode.IDLE; invalidate()
    }
}
