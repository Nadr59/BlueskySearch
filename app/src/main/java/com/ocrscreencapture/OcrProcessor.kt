package com.ocrscreencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * معالج التعرف الضوئي على الحروف
 * يستخدم ML Kit المضمّن — لا يحتاج استيرادات إضافية
 */
class OcrProcessor {

    // ✅ أبسط طريقة — لا تحتاج أي import إضافي
    private val recognizer: TextRecognizer = TextRecognition.getClient()

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)
            val image = InputImage.fromBitmap(compressed, 0)

            val result = tryRecognize(image)

            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled) {
                compressed.recycle()
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private suspend fun tryRecognize(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    if (cont.isActive) cont.resume("")
                }
        }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(
                bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
            )
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
        } catch (e: Exception) {
            bitmap
        }
    }

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
