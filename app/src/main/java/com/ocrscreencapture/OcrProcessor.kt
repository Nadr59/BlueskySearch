package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * معالج التعرف الضوئي على الحروف باستخدام Tesseract 4
 * يدعم: العربية ✅ + الإنجليزية ✅ + 100 لغة أخرى
 */
class OcrProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OcrProcessor"
    }

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * معالجة الصورة واستخراج النص
     * يُنشئ Tesseract عند أول استدعاء (lazy init)
     */
    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            // تهيئة Tesseract إذا لم يتم بعد
            if (!isInitialized) {
                initTesseract()
            }

            val api = tessApi
            if (api == null || !isInitialized) {
                Log.e(TAG, "Tesseract not initialized!")
                return@withContext ""
            }

            Log.d(TAG, "Processing: ${bitmap.width}x${bitmap.height}")

            // تحسين الصورة
            val enhanced = enhanceContrast(bitmap)
            val compressed = compressBitmap(enhanced)

            // ✅ التعرف على النص
            api.setImage(compressed)
            val result = api.getUTF8Text() ?: ""
            api.clear()

            // تحرير الذاكرة
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (compressed !== enhanced && compressed !== bitmap && !compressed.isRecycled) {
                compressed.recycle()
            }

            val trimmed = result.trim()
            Log.d(TAG, "Result: ${trimmed.length} chars = '${trimmed.take(80)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    /**
     * تهيئة Tesseract — نسخ بيانات اللغة من Assets إلى التخزين الداخلي
     */
    private fun initTesseract() {
        try {
            val dataPath = context.filesDir.absolutePath
            val tessDir = File(dataPath, "tessdata")

            // نسخ ملفات اللغة إذا لم تكن موجودة
            if (!tessDir.exists() || !File(tessDir, "ara.traineddata").exists()) {
                Log.d(TAG, "Copying trained data from assets...")
                tessDir.mkdirs()
                copyAsset("tessdata/ara.traineddata", File(tessDir, "ara.traineddata"))
                copyAsset("tessdata/eng.traineddata", File(tessDir, "eng.traineddata"))
            }

            // التحقق من وجود الملفات
            val araFile = File(tessDir, "ara.traineddata")
            val engFile = File(tessDir, "eng.traineddata")

            if (!araFile.exists()) {
                Log.e(TAG, "ara.traineddata NOT FOUND at: ${araFile.absolutePath}")
                return
            }
            if (!engFile.exists()) {
                Log.e(TAG, "eng.traineddata NOT FOUND at: ${engFile.absolutePath}")
                return
            }

            Log.d(TAG, "ara.traineddata: ${araFile.length()} bytes")
            Log.d(TAG, "eng.traineddata: ${engFile.length()} bytes")

            // تهيئة Tesseract
            tessApi = TessBaseAPI()
            val success = tessApi!!.init(dataPath, "ara+eng")

            if (success) {
                isInitialized = true
                Log.d(TAG, "Tesseract initialized successfully! Language: ara+eng")
            } else {
                Log.e(TAG, "TessBaseAPI.init() returned false!")
                tessApi?.end()
                tessApi = null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init failed", e)
            tessApi = null
        }
    }

    /**
     * نسخ ملف من Assets إلى التخزين الداخلي
     */
    private fun copyAsset(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $assetPath → ${destFile.absolutePath} ($bytes bytes)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
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

    /**
     * تحرير الموارد
     */
    fun close() {
    try {
        tessApi?.end()
    } catch (_: Exception) {}
    tessApi = null
    isInitialized = false
    }
}
