package com.ocrscreencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * معالج التعرف الضوئي على الحروف باستخدام ML Kit
 * يستخدم المعالج الافتراضي المضمّن الذي يدعم:
 * - الإنجليزية واللاتينية بشكل كامل
 * - العربية بشكل أساسي (يتحسن مع وضوح النص)
 */
class OcrProcessor {

    // ✅ المعالج الافتراضي — لا يحتاج مكتبة إضافية
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * معالجة الصورة واستخراج النص
     */
    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)
            val image = InputImage.fromBitmap(compressed, 0)

            val result = tryRecognize(image)

            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled)
                compressed.recycle()

            result
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private suspend fun tryRecognize(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    if (cont.isActive) cont.resume(text.text)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    if (cont.isActive) cont.resume("")
                }
        }

    /**
     * تحسين تباين الصورة لدقة OCR أفضل
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(
                bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        1.4f, 0f, 0f, 0f, -20f,
                        0f, 1.4f, 0f, 0f, -20f,
                        0f, 0f, 1.4f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * ضغط الصورة لتقليل وقت المعالجة
     */
    private fun compressBitmap(bitmap: Bitmap, maxDim: Int = 1920): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = minOf(
            maxDim.toFloat() / bitmap.width,
            maxDim.toFloat() / bitmap.height
        )
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}
