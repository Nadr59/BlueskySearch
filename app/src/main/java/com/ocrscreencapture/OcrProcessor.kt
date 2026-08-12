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

            // ✅ tess-two يستخدم getUTF8Text()
            val result = api.utF8Text ?: ""

            api.clear()

            if (enhanced !== safe && enhanced !== bitmap && !enhanced.isRecycled) {
                enhanced.recycle()
            }
            if (safe !== bitmap && !safe.isRecycled) {
                safe.recycle()
            }

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

            if (!araFile.exists() || araFile.length() < 100000) {
                initError = "ara.traineddata مفقود أو صغير (${araFile.length()} bytes)"
                Log.e(TAG, initError)
                return
            }
            if (!engFile.exists() || engFile.length() < 100000) {
                initError = "eng.traineddata مفقود أو صغير (${engFile.length()} bytes)"
                Log.e(TAG, initError)
                return
            }

            // التحقق من أن الملف ليس HTML
            try {
                val header = ByteArray(20)
                araFile.inputStream().use { it.read(header) }
                val headerStr = String(header, Charsets.US_ASCII)
                Log.d(TAG, "ara header: $headerStr")
                if (headerStr.contains("<") || headerStr.contains("html") || headerStr.contains("DOCTYPE")) {
                    initError = "ara.traineddata هو صفحة HTML وليس ملف نموذج!"
                    Log.e(TAG, initError)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Header check failed", e)
            }

            // ✅ تهيئة Tesseract
            Log.d(TAG, "Creating TessBaseAPI...")
            tessApi = TessBaseAPI()

            Log.d(TAG, "init(dataPath='$dataPath', lang='ara+eng')")
            val success = try {
                tessApi!!.init(dataPath, "ara+eng")
            } catch (e: Exception) {
                Log.e(TAG, "init() exception!", e)
                false
            }

            Log.d(TAG, "init() returned: $success")

            if (success) {
                isInitialized = true
                Log.d(TAG, "READY with ara+eng!")
            } else {
                // محاولة إنجليزي فقط
                Log.w(TAG, "ara+eng failed, trying eng only...")
                try { tessApi!!.end() } catch (_: Exception) {}
                tessApi = TessBaseAPI()
                val s2 = try {
                    tessApi!!.init(dataPath, "eng")
                } catch (e: Exception) {
                    Log.e(TAG, "init(eng) exception!", e)
                    false
                }
                if (s2) {
                    isInitialized = true
                    Log.d(TAG, "READY with eng only!")
                } else {
                    initError = "init() فشل مع كل اللغات"
                    Log.e(TAG, initError)
                    try { tessApi!!.end() } catch (_: Exception) {}
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
        try { tessApi?.end() } catch (_: Exception) {}
        tessApi = null
        isInitialized = false
    }
}
