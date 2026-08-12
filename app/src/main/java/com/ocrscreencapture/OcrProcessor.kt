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

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                initTesseract()
            }

            val api = tessApi
            if (api == null || !isInitialized) {
                Log.e(TAG, "Tesseract NOT initialized!")
                return@withContext ""
            }

            Log.d(TAG, "Processing: ${bitmap.width}x${bitmap.height}")

            // تأكد من الصيغة
            val safeBitmap = ensureArgb8888(bitmap)

            // تحسين خفيف
            val enhanced = lightEnhance(safeBitmap)

            // ✅ التعرف
            api.setImage(enhanced)
            val result = api.utF8Text ?: ""
            api.clear()

            // تنظيف الذاكرة
            if (enhanced !== safeBitmap && enhanced !== bitmap && !enhanced.isRecycled) {
                enhanced.recycle()
            }
            if (safeBitmap !== bitmap && !safeBitmap.isRecycled) {
                safeBitmap.recycle()
            }

            val trimmed = result.trim()
            Log.d(TAG, "Result: ${trimmed.length} chars")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    private fun initTesseract() {
        try {
            Log.d(TAG, "=== Initializing Tesseract ===")

            val dataPath = context.filesDir.absolutePath
            val tessDir = File(dataPath, "tessdata")

            // نسخ البيانات
            if (!tessDir.exists() || !File(tessDir, "ara.traineddata").exists()) {
                Log.d(TAG, "Copying trained data...")
                tessDir.mkdirs()
                copyAsset("tessdata/ara.traineddata", File(tessDir, "ara.traineddata"))
                copyAsset("tessdata/eng.traineddata", File(tessDir, "eng.traineddata"))
            }

            val araFile = File(tessDir, "ara.traineddata")
            val engFile = File(tessDir, "eng.traineddata")

            Log.d(TAG, "ara exists=${araFile.exists()} size=${araFile.length()}")
            Log.d(TAG, "eng exists=${engFile.exists()} size=${engFile.length()}")

            if (!araFile.exists() || araFile.length() < 1000) {
                Log.e(TAG, "Arabic data missing or too small!")
                return
            }

            // ✅ تهيئة
            tessApi = TessBaseAPI()
            val success = tessApi!!.init(dataPath, "ara+eng")

            if (success) {
                tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                isInitialized = true
                Log.d(TAG, "Tesseract READY! lang=ara+eng")
            } else {
                Log.e(TAG, "init(ara+eng) FAILED, trying ara only...")
                tessApi!!.close()
                tessApi = TessBaseAPI()
                val s2 = tessApi!!.init(dataPath, "ara")
                if (s2) {
                    tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    isInitialized = true
                    Log.d(TAG, "Tesseract READY! lang=ara")
                } else {
                    Log.e(TAG, "init(ara) FAILED, trying eng only...")
                    tessApi!!.close()
                    tessApi = TessBaseAPI()
                    val s3 = tessApi!!.init(dataPath, "eng")
                    if (s3) {
                        tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                        isInitialized = true
                        Log.d(TAG, "Tesseract READY! lang=eng")
                    } else {
                        Log.e(TAG, "ALL init attempts FAILED!")
                        tessApi?.close()
                        tessApi = null
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Init exception!", e)
            tessApi = null
        }
    }

    private fun copyAsset(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $assetPath (${bytes} bytes)")
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
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        1.2f, 0f, 0f, 0f, -10f,
                        0f, 1.2f, 0f, 0f, -10f,
                        0f, 0f, 1.2f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            })
            result
        } catch (_: Exception) { bitmap }
    }

    fun close() {
        try { tessApi?.close() } catch (_: Exception) {}
        tessApi = null
        isInitialized = false
    }
}
