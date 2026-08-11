package com.ocrscreencapture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * معالج التعرف الضوئي على الحروف
 * يستخدم ML Kit (Google Play Services) للتعرف على النصوص العربية والإنجليزية
 */
class OcrProcessor {

    // معالج النصوص العربية
    private val arabicRecognizer: TextRecognizer =
        TextRecognition.getClient(ArabicTextRecognizerOptions.Builder().build())

    // معالج النصوص اللاتينية (الإنجليزية)
    private val latinRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * معالجة الصورة واستخراج النص
     * يشغّل كلا المعالجين بالتوازي ويختار النتيجة الأفضل
     */
    suspend fun processImage(bitmap: Bitmap): String = coroutineScope {
        val compressed = compressBitmap(bitmap)
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

        // تحرير الصورة المضغوطة إذا كانت مختلفة عن الأصلية
        if (compressed !== bitmap && !compressed.isRecycled) {
            compressed.recycle()
        }

        // اختيار النتيجة الأطول (عادة الأكثر دقة)
        when {
            arabicResult.length > latinResult.length -> arabicResult
            latinResult.isNotBlank() -> latinResult
            arabicResult.isNotBlank() -> arabicResult
            else -> ""
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
            .addOnFailureListener {
                if (cont.isActive) cont.resume("")
            }
    }

    /**
     * ضغط الصورة لتقليل وقت المعالجة
     */
    private fun compressBitmap(bitmap: Bitmap, maxDim: Int = 1920): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    fun close() {
        arabicRecognizer.close()
        latinRecognizer.close()
    }
}
