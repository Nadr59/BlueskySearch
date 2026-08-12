package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * محرك OCR مزدوج:
 * - ML Kit: سريع، بدون إنترنت، للإنجليزي
 * - Google Cloud Vision API: دقيق، يدعم العربي + الإنجليزي + كل اللغات
 */
class OcrProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OcrProcessor"
        private const val PREF_NAME = "ocr_prefs"
        private const val KEY_API_KEY = "cloud_vision_api_key"
        private const val VISION_API_URL =
            "https://vision.googleapis.com/v1/images:annotate"
    }

    private val mlKitRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ═══════════════ API Key ═══════════════

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ═══════════════ المعالجة الرئيسية ═══════════════

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "═══ Processing ═══")
            Log.d(TAG, "Input: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "API Key: ${if (hasApiKey()) "موجود" else "غير موجود"}")
            Log.d(TAG, "Online: ${isOnline()}")

            val safe = ensureArgb8888(bitmap)
            val scaled = scaleForOcr(safe)

            val result: String

            if (hasApiKey() && isOnline()) {
                // ✅ Cloud Vision API — يدعم العربي + الإنجليزي
                Log.d(TAG, "Using Cloud Vision API")
                result = tryCloudVision(scaled)
                if (result.isBlank()) {
                    Log.w(TAG, "Cloud Vision empty, falling back to ML Kit")
                    result.tryMlKit(scaled)
                }
            } else {
                // ✅ ML Kit — للإنجليزي فقط
                Log.d(TAG, "Using ML Kit (offline/no API key)")
                result = tryMlKit(scaled)
            }

            if (scaled !== safe && scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            if (safe !== bitmap && !safe.isRecycled) safe.recycle()

            val trimmed = result.trim()
            Log.d(TAG, "═══ Result ═══")
            Log.d(TAG, "Length: ${trimmed.length}")
            Log.d(TAG, "Text: '${trimmed.take(200)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    // ═══════════════ ML Kit (إنجليزي) ═══════════════

    private suspend fun tryMlKit(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizeMlKit(image)
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit error", e)
            ""
        }
    }

    private suspend fun recognizeMlKit(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            mlKitRecognizer.process(image)
                .addOnSuccessListener { text ->
                    if (cont.isActive) cont.resume(text.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit fail", e)
                    if (cont.isActive) cont.resume("")
                }
        }

    // ═══════════════ Cloud Vision API (عربي + إنجليزي) ═══════════════

    private suspend fun tryCloudVision(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calling Cloud Vision API...")

            // تحويل الصورة إلى base64
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            Log.d(TAG, "Image base64 length: ${base64Image.length}")

            // بناء الطلب
            val requestJson = JSONObject().apply {
                put("requests", JSONArray().apply {
                    put(JSONObject().apply {
                        put("image", JSONObject().apply {
                            put("content", base64Image)
                        })
                        put("features", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "TEXT_DETECTION")
                                put("maxResults", 1)
                            })
                        })
                    })
                })
            }

            // إرسال الطلب
            val apiKey = getApiKey()
            val url = URL("$VISION_API_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection

            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray())
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "Vision API response: $responseCode")

            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                parseVisionResponse(response)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Vision API error $responseCode: $error")
                ""
            }

        } catch (e: Exception) {
            Log.e(TAG, "Cloud Vision error", e)
            ""
        }
    }

    private fun parseVisionResponse(response: String): String {
        return try {
            val json = JSONObject(response)
            val responses = json.getJSONArray("responses")
            if (responses.length() == 0) return ""

            val firstResponse = responses.getJSONObject(0)

            // محاولة 1: fullTextAnnotation (الأفضل)
            if (firstResponse.has("fullTextAnnotation")) {
                val fullText = firstResponse.getJSONObject("fullTextAnnotation")
                return fullText.getString("text")
            }

            // محاولة 2: textAnnotations
            if (firstResponse.has("textAnnotations")) {
                val annotations = firstResponse.getJSONArray("textAnnotations")
                if (annotations.length() > 0) {
                    return annotations.getJSONObject(0).getString("description")
                }
            }

            ""
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            ""
        }
    }

    // ═══════════════ مساعدات ═══════════════

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    private fun scaleForOcr(bitmap: Bitmap, maxDim: Int = 1600): Bitmap {
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
        try { mlKitRecognizer.close() } catch (_: Exception) {}
    }
}
