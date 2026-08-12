package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import cz.adaptech.tesseract4android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OcrProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OcrProcessor"
    }

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false
    private var initError: String = ""

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                initTesseract()
            }

            val api = tessApi
            if (api == null || !isInitialized) {
                Log.e(TAG, "NOT INITIALIZED! Error: $initError")
                return@withContext ""
            }

            Log.d(TAG, "Processing: ${bitmap.width}x${bitmap.height}")

            val safe = ensureArgb8888(bitmap)
            val enhanced = lightEnhance(safe)

            api.setImage(enhanced)
            val result = api.utF8Text ?: ""
            api.clear()

            if (enhanced !== safe && enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
            if (safe !== bitmap && !safe.isRecycled) safe.recycle()

            val trimmed = result.trim()
            Log.d(TAG, "Result: ${trimmed.length} chars = '${trimmed.take(100)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR exception", e)
            ""
        }
    }

    private fun initTesseract() {
        try {
            Log.d(TAG, "=== INIT TESSERACT ===")

            val dataPath = context.filesDir.absolutePath
            val tessDir = File(dataPath, "tessdata")

            // نسخ من Assets
            if (!tessDir.exists() || !File(tessDir, "ara.traineddata").exists()) {
                Log.d(TAG, "Copying from assets...")
                tessDir.mkdirs()
                copyAsset("tessdata/ara.traineddata", File(tessDir, "ara.traineddata"))
                copyAsset("tessdata/eng.traineddata", File(tessDir, "eng.traineddata"))
            }

            val araFile = File(tessDir, "ara.traineddata")
            val engFile = File(tessDir, "eng.traineddata")

            Log.d(TAG, "ara: exists=${araFile.exists()} size=${araFile.length()}")
            Log.d(TAG, "eng: exists=${engFile.exists()} size=${engFile.length()}")

            // التحقق من صحة الملفات
            if (!araFile.exists() || araFile.length() < 10000) {
                initError = "ملف ara.traineddata غير موجود أو صغير جداً (${araFile.length()} bytes)"
                Log.e(TAG, initError)
                return
            }
            if (!engFile.exists() || engFile.length() < 10000) {
                initError = "ملف eng.traineddata غير موجود أو صغير جداً (${engFile.length()} bytes)"
                Log.e(TAG, initError)
                return
            }

            // التحقق من أن الملف ليس HTML
            try {
                val firstBytes = ByteArray(20)
                araFile.inputStream().use { it.read(firstBytes) }
                val header = String(firstBytes).take(10)
                Log.d(TAG, "ara header bytes: $header")
                if (header.contains("<!DOCTYPE") || header.contains("<html")) {
                    initError = "ara.traineddata هو صفحة HTML وليس ملف نموذج!"
                    Log.e(TAG, initError)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read header", e)
            }

            // تهيئة Tesseract
            Log.d(TAG, "Creating TessBaseAPI...")
            tessApi = TessBaseAPI()

            Log.d(TAG, "Calling init(dataPath, 'ara+eng')...")
            Log.d(TAG, "dataPath = $dataPath")

            val success = try {
                tessApi!!.init(dataPath, "ara+eng")
            } catch (e: Exception) {
                Log.e(TAG, "init() threw exception!", e)
                false
            }

            Log.d(TAG, "init() returned: $success")

            if (success) {
                tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                isInitialized = true
                Log.d(TAG, "READY with ara+eng!")
            } else {
                Log.w(TAG, "ara+eng FAILED, trying eng only...")
                try { tessApi!!.close() } catch (_: Exception) {}
                tessApi = TessBaseAPI()
                val s2 = try { tessApi!!.init(dataPath, "eng") } catch (e: Exception) { false }
                Log.d(TAG, "init(eng) = $s2")

                if (s2) {
                    tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    isInitialized = true
                    Log.d(TAG, "READY with eng!")
                } else {
                    initError = "init() فشل مع eng أيضاً"
                    Log.e(TAG, initError)
                    try { tessApi!!.close() } catch (_: Exception) {}
                    tessApi = null
                }
            }

        } catch (e: Exception) {
            initError = "استثناء: ${e.message}"
            Log.e(TAG, "Init exception!", e)
            tessApi = null
        }
    }

    private fun copyAsset(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $assetPath → ${destFile.name} ($bytes bytes)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: $assetPath", e)
        }
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    private fun lightEnhance(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, -10f,
                    0f, 0f, 1.2f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )))
            })
            result
        } catch (_: Exception) { bitmap }
    }

    fun getInitError(): String = initError

    fun close() {
        try { tessApi?.close() } catch (_: Exception) {}
        tessApi = null
        isInitialized = false
    }
}
