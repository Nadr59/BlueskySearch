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
    private var activeLang: String = ""

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

            Log.d(TAG, "═══ Processing ═══")
            Log.d(TAG, "Input: ${bitmap.width}x${bitmap.height} config=${bitmap.config}")
            Log.d(TAG, "Active language: $activeLang")

            // 1) تحويل لـ ARGB_8888
            val safe = ensureArgb8888(bitmap)
            Log.d(TAG, "Safe: ${safe.width}x${safe.height}")

            // 2) ✅ لا نُحسّن الصورة — الأصلية أفضل للعربي
            // التباين الزائد يقتل النقط والتشكيل

            // 3) ✅ تكبير الصورة الصغيرة (Tesseract يحتاج دقة عالية)
            val scaled = scaleUpIfNeeded(safe)
            Log.d(TAG, "Scaled: ${scaled.width}x${scaled.height}")

            // 4) التعرف
            api.setImage(scaled)
            val result = api.utF8Text ?: ""
            api.clear()

            // 5) تنظيف
            if (scaled !== safe && scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            if (safe !== bitmap && !safe.isRecycled) {
                safe.recycle()
            }

            val trimmed = result.trim()
            Log.d(TAG, "═══ Result ═══")
            Log.d(TAG, "Length: ${trimmed.length}")
            Log.d(TAG, "Text: '${trimmed.take(200)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR exception", e)
            ""
        }
    }

    // ═══════════════ التهيئة ═══════════════

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

            Log.d(TAG, "ara: ${araFile.exists()} ${araFile.length()} bytes")
            Log.d(TAG, "eng: ${engFile.exists()} ${engFile.length()} bytes")

            if (!araFile.exists() || araFile.length() < 100000) {
                initError = "ara.traineddata مفقود (${araFile.length()} bytes)"
                Log.e(TAG, initError)
                return
            }

            // التحقق من أن الملف ليس HTML
            try {
                val header = ByteArray(20)
                araFile.inputStream().use { it.read(header) }
                val h = String(header, Charsets.US_ASCII)
                if (h.contains("<")) {
                    initError = "ara.traineddata ملف تالف (HTML)"
                    Log.e(TAG, initError)
                    return
                }
            } catch (_: Exception) {}

            // ✅ محاولة: عربي + إنجليزي
            tessApi = TessBaseAPI()
            var success = try {
                tessApi!!.init(dataPath, "ara+eng")
            } catch (e: Exception) {
                Log.e(TAG, "init(ara+eng) threw", e)
                false
            }

            if (success) {
                activeLang = "ara+eng"
                configureApi(tessApi!!)
                isInitialized = true
                Log.d(TAG, "READY: ara+eng")
                return
            }

            // محاولة: عربي فقط
            Log.w(TAG, "ara+eng failed, trying ara...")
            try { tessApi!!.end() } catch (_: Exception) {}
            tessApi = TessBaseAPI()
            success = try {
                tessApi!!.init(dataPath, "ara")
            } catch (e: Exception) { false }

            if (success) {
                activeLang = "ara"
                configureApi(tessApi!!)
                isInitialized = true
                Log.d(TAG, "READY: ara")
                return
            }

            // محاولة: إنجليزي فقط
            Log.w(TAG, "ara failed, trying eng...")
            try { tessApi!!.end() } catch (_: Exception) {}
            tessApi = TessBaseAPI()
            success = try {
                tessApi!!.init(dataPath, "eng")
            } catch (e: Exception) { false }

            if (success) {
                activeLang = "eng"
                configureApi(tessApi!!)
                isInitialized = true
                Log.d(TAG, "READY: eng (Arabic not supported!)")
                return
            }

            initError = "init() فشل مع كل اللغات"
            Log.e(TAG, initError)
            try { tessApi!!.end() } catch (_: Exception) {}
            tessApi = null

        } catch (e: Exception) {
            initError = "استثناء: ${e.message}"
            Log.e(TAG, "Init exception!", e)
            tessApi = null
        }
    }

    /**
     * ✅ إعدادات Tesseract لتحسين دقة العربية
     */
    private fun configureApi(api: TessBaseAPI) {
        // PSM_AUTO: اكتشاف تلقائي للتخطيط
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)

        // ✅ إضافة أحرف عربية إضافية إذا كان المعالج يدعمها
        try {
            // بعض إصدارات tess-two تدعم setVariable
            // نحاول فقط — إذا فشل لا مشكلة
            val method = api.javaClass.getMethod("setVariable", String::class.java, String::class.java)
            // أحرف إضافية ممكنة
            method.invoke(api, "tessedit_char_blacklist", "|\\{}[]<>")
            Log.d(TAG, "setVariable success")
        } catch (e: Exception) {
            Log.d(TAG, "setVariable not available (OK)")
        }
    }

    private fun copyAsset(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $assetPath ($bytes bytes)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: $assetPath", e)
        }
    }

    // ═══════════════ معالجة الصورة ═══════════════

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    /**
     * ✅ تكبير الصورة إذا كانت صغيرة
     * Tesseract يعمل أفضل مع دقة 300 DPI
     * صورة بعرض 500px على شاشة 1080px = نص صغير جداً
     */
    private fun scaleUpIfNeeded(bitmap: Bitmap, minDimension: Int = 800): Bitmap {
        val minSide = minOf(bitmap.width, bitmap.height)
        if (minSide >= minDimension) {
            Log.d(TAG, "No scaling needed (min=$minSide >= $minDimension)")
            return bitmap
        }

        val scale = minDimension.toFloat() / minSide
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()

        Log.d(TAG, "Scaling up: ${bitmap.width}x${bitmap.height} → ${newW}x${newH} (scale=$scale)")

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    // ═══════════════ التنظيف ═══════════════

    fun getInitError(): String = initError
    fun getActiveLanguage(): String = activeLang

    fun close() {
        try { tessApi?.end() } catch (_: Exception) {}
        tessApi = null
        isInitialized = false
    }
}
