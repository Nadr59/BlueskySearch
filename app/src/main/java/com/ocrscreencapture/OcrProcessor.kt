package com.ocrscreencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OcrProcessor {

    companion object {
        private const val TAG = "OcrProcessor"
    }

    // ✅ معالج عربي
    private val arabicRecognizer: TextRecognizer =
        TextRecognition.getClient(ArabicTextRecognizerOptions.Builder().build())

    // ✅ معالج إنجليزي
    private val latinRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): String = coroutineScope {
        try {
            Log.d(TAG, "Processing: ${bitmap.width}x${bitmap.height}")

            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)
            val image = InputImage.fromBitmap(compressed, 0)

            // ✅ تشغيل كلا المعالجين بالتوازي
            val arabicJob = async(Dispatchers.Default) {
                recognize(arabicRecognizer, image)
            }
            val latinJob = async(Dispatchers.Default) {
                recognize(latinRecognizer, image)
            }

            val arabicResult = arabicJob.await()
            val latinResult = latinJob.await()

            Log.d(TAG, "Arabic: ${arabicResult.length} chars = '${arabicResult.take(50)}'")
            Log.d(TAG, "Latin: ${latinResult.length} chars = '${latinResult.take(50)}'")

            // تحرير الذاكرة
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled) {
                compressed.recycle()
            }

            // ✅ اختيار النتيجة الأفضل
            val best = chooseBest(arabicResult, latinResult)
            Log.d(TAG, "Best result: ${best.length} chars")
            best

        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            ""
        }
    }

    /**
     * ✅ اختيار النتيجة الأفضل
     * إذا كانت العربية تحتوي أحرف عربية → اخترها
     * وإلا اختر الأطول
     */
    private fun chooseBest(arabic: String, latin: String): String {
        val arabicHasArabicChars = arabic.any { it in '\u0600'..'\u06FF' || it in '\u0750'..\u077F' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }

        return when {
            // العربية تحتوي أحرف عربية حقيقية
            arabicHasArabicChars && arabic.isNotBlank() -> arabic
            // اللاتينية تحتوي نص
            latin.isNotBlank() -> latin
            // العربية تحتوي أي شيء
            arabic.isNotBlank() -> arabic
            // لا شيء
            else -> ""
        }
    }

    /**
     * ✅ التحقق: هل النص يحتوي أحرف عربية؟
     */
    private fun String.hasArabic(): Boolean {
        return any { char ->
            char in '\u0600'..'\u06FF' ||    // Arabic
            char in '\u0750'..'\u077F' ||    // Arabic Supplement
            char in '\uFB50'..'\uFDFF' ||    // Arabic Presentation Forms-A
            char in '\uFE70'..'\uFEFF'       // Arabic Presentation Forms-B
        }
    }

    private suspend fun recognize(
        recognizer: TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { text ->
                if (cont.isActive) cont.resume(text.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Recognition failed", e)
                if (cont.isActive) cont.resume("")
            }
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        1.5f, 0f, 0f, 0f, -25f,
                        0f, 1.5f, 0f, 0f, -25f,
                        0f, 0f, 1.5f, 0f, -25f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) { bitmap }
    }

    private fun compressBitmap(bitmap: Bitmap, maxDim: Int = 1920): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
    }

    fun close() {
        try { arabicRecognizer.close() } catch (_: Exception) {}
        try { latinRecognizer.close() } catch (_: Exception) {}
    }
}
