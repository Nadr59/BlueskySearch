package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
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

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                initTesseract()
            }

            val api = tessApi
            if (api == null || !isInitialized) {
                Log.e(TAG, "❌ Tesseract not initialized!")
                return@withContext "خطأ: Tesseract غير مهيأ"
            }

            Log.d(TAG, "═══ بدء المعالجة ═══")
            Log.d(TAG, "الأبعاد الأصلية: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "Config: ${bitmap.config}")

            // ✅ تحويل الصورة لـ ARGB_8888 إذا لم تكن كذلك
            val safeBitmap = ensureArgb8888(bitmap)
            Log.d(TAG, "الأبعاد الآمنة: ${safeBitmap.width}x${safeBitmap.height} Config: ${safeBitmap.config}")

            // ✅ تحسين بسيط فقط (لا تبالغ!)
            val enhanced = lightEnhance(safeBitmap)
            Log.d(TAG, "بعد التحسين: ${enhanced.width}x${enhanced.height}")

            // ✅ التعرف على النص
            api.setImage(enhanced)
            Log.d(TAG, "setImage نجح")

            val result = api.utF8Text ?: ""
            api.clear()
            Log.d(TAG, "═══ النتيجة ═══")
            Log.d(TAG, "طول النص: ${result.length}")
            Log.d(TAG, "النص: '${result.take(200)}'")

            // تحرير الذاكرة
            if (enhanced !== safeBitmap && enhanced !== bitmap && !enhanced.isRecycled) {
                enhanced.recycle()
            }
            if (safeBitmap !== bitmap && !safeBitmap.isRecycled) {
                safeBitmap.recycle()
            }

            result.trim()

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في OCR", e)
            "خطأ: ${e.message}"
        }
    }

    // ═══════════════ التهيئة ═══════════════

    private fun initTesseract() {
        try {
            Log.d(TAG, "═══ تهيئة Tesseract ═══")

            val dataPath = context.filesDir.absolutePath
            val tessDir = File(dataPath, "tessdata")
            Log.d(TAG, "مسار البيانات: $dataPath")
            Log.d(TAG, "مجلد tessdata: ${tessDir.absolutePath}")

            // ✅ نسخ بيانات اللغة
            if (!tessDir.exists() || !File(tessDir, "ara.traineddata").exists()) {
                Log.d(TAG, "نسخ بيانات اللغة من Assets...")
                tessDir.mkdirs()
                copyAsset("tessdata/ara.traineddata", File(tessDir, "ara.traineddata"))
                copyAsset("tessdata/eng.traineddata", File(tessDir, "eng.traineddata"))
            }

            // ✅ التحقق من الملفات
            val araFile = File(tessDir, "ara.traineddata")
            val engFile = File(tessDir, "eng.traineddata")

            Log.d(TAG, "ara.traineddata موجود: ${araFile.exists()} حجم: ${araFile.length()}")
            Log.d(TAG, "eng.traineddata موجود: ${engFile.exists()} حجم: ${engFile.length()}")

            if (!araFile.exists() || araFile.length() < 1000) {
                Log.e(TAG, "❌ ملف اللغة العربية غير موجود أو فارغ!")
                return
            }

            // ✅ تهيئة — جرّب "ara+eng" ثم "ara" ثم "eng"
            tessApi = TessBaseAPI()

            // محاولة 1: عربي + إنجليزي
            var success = tessApi!!.init(dataPath, "ara+eng")
            Log.d(TAG, "init(ara+eng) = $success")

            if (!success) {
                // محاولة 2: عربي فقط
                Log.w(TAG, "فشل ara+eng، جاري المحاولة مع ara فقط...")
                tessApi!!.end()
                tessApi = TessBaseAPI()
                success = tessApi!!.init(dataPath, "ara")
                Log.d(TAG, "init(ara) = $success")
            }

            if (!success) {
                // محاولة 3: إنجليزي فقط
                Log.w(TAG, "فشل ara، جاري المحاولة مع eng فقط...")
                tessApi!!.end()
                tessApi = TessBaseAPI()
                success = tessApi!!.init(dataPath, "eng")
                Log.d(TAG, "init(eng) = $success")
            }

            if (success) {
                // ✅ إعدادات لتحسين الدقة
                tessApi!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                isInitialized = true
                Log.d(TAG, "✅ Tesseract جاهز!")
            } else {
                Log.e(TAG, "❌ فشل التهيئة بكل اللغات!")
                tessApi?.end()
                tessApi = null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في التهيئة", e)
            tessApi = null
        }
    }

    private fun copyAsset(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "نسخ: $assetPath → ${destFile.name} ($bytes بايت)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل نسخ: $assetPath", e)
        }
    }

    // ═══════════════ معالجة الصورة ═══════════════

    /**
     * ✅ تأكد أن الصورة ARGB_8888 (مطلوب لـ Tesseract)
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        Log.w(TAG, "تحويل من ${bitmap.config} إلى ARGB_8888")
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    /**
     * ✅ تحسين خفيف — لا تبالغ بالتباين!
     */
    private fun lightEnhance(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            // ✅ تحسين خفيف: تباين 1.2x فقط (ليس 1.5x!)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        1.2f, 0f, 0f, 0f, -10f,
                        0f, 1.2f, 0f, 0f, -10f,
                        0f, 0f, 1.2f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
                isFilterBitmap = true
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            Log.w(TAG, "فشل التحسين، استخدام الأصلية", e)
            bitmap
        }
    }

    // ═══════════════ التنظيف ═══════════════

    fun close() {
        try { tessApi?.end() } catch (_: Exception) {}
        tessApi = null
        isInitialized = false
        Log.d(TAG, "تم إغلاق Tesseract")
    }
}
