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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OcrProcessor {

    companion object { private const val TAG = "OcrProcessor" }

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)
            val image = InputImage.fromBitmap(compressed, 0)
            val result = tryRecognize(image)
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled) compressed.recycle()
            result
        } catch (e: Exception) { Log.e(TAG, "Error", e); "" }
    }

    private suspend fun tryRecognize(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { e -> Log.e(TAG, "Fail", e); if (cont.isActive) cont.resume("") }
        }

    private fun enhanceContrast(b: Bitmap): Bitmap = try {
        val r = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(r).drawBitmap(b, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1.5f,0f,0f,0f,-25f, 0f,1.5f,0f,0f,-25f, 0f,0f,1.5f,0f,-25f, 0f,0f,0f,1f,0f)))
        }); r
    } catch (_: Exception) { b }

    private fun compressBitmap(b: Bitmap, max: Int = 1920): Bitmap {
        if (b.width <= max && b.height <= max) return b
        val ratio = minOf(max.toFloat() / b.width, max.toFloat() / b.height)
        return Bitmap.createScaledBitmap(b, (b.width * ratio).toInt(), (b.height * ratio).toInt(), true)
    }

    fun close() { try { recognizer.close() } catch (_: Exception) {} }
}
