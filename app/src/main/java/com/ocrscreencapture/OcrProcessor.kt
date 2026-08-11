package com.ocrscreencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * معالج التعرف الضوئي على الحروف
 * يستخدم معالجين بالتوازي: عربي + إنجليزي
 * ويختار النتيجة الأطول (الأكثر دقة)
 */
class OcrProcessor {

    // ✅ معالج النصوص العربية
    private val arabicRecognizer: TextRecognizer =
        TextRecognition.getClient(ArabicTextRecognizerOptions.Builder().build())

    // ✅ معالج النصوص اللاتينية (الإنجليزية)
    private val latinRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    /**
     * معالجة الصورة واستخراج النص
     * يشغّل كلا المعالجين بالتوازي ويختار النتيجة الأفضل
     */
    suspend fun processImage(bitmap: Bitmap): String = coroutineScope {
        try {
            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)
            val image = InputImage.fromBitmap(compressed, 0)

            // تشغيل كلا المعالجين بالتوازي
            val arabicDeferred = async(Dispatchers.Default) {
                tryRecognize(arabicRecognizer, image)
            }
            val latinDeferred = async(Dispatchers.Default) {
                tryRecognize(latinRecognizer, image)
            }

            val arabicResult = arabicDeferred.await()
            val latinResult = latinDeferred.await()

            // تحرير الذاكرة
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled) compressed.recycle()

            // اختيار النتيجة الأطول
            when {
                arabicResult.length > latinResult.length -> arabicResult
                latinResult.isNotBlank() -> latinResult
                arabicResult.isNotBlank() -> arabicResult
                else -> ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * محاولة التعرف على النص باستخدام معالج محدد
     */
    private suspend fun tryRecognize(
        recognizer: TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { cont ->
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
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            1.4f, 0f, 0f, 0f, -20f,
                            0f, 1.4f, 0f, 0f, -20f,
                            0f, 0f, 1.4f, 0f, -20f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
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

    /**
     * تحرير الموارد
     */
    fun close() {
        try { arabicRecognizer.close() } catch (_: Exception) {}
        try { latinRecognizer.close() } catch (_: Exception) {}
    }
}
