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
            Log.d(TAG, "API Key: ${if (hasApiKey()) "yes" else "no"}")
            Log.d(TAG, "Online: ${isOnline()}")

            val safe = ensureArgb8888(bitmap)
            val scaled = scaleForOcr(safe)

            val result: String = if (hasApiKey() && isOnline()) {
                Log.d(TAG, "Using Cloud Vision API")
                val cloudResult = cloudVisionRecognize(scaled)
                if (cloudResult.isNotBlank()) {
                    cloudResult
                } else {
                    Log.w(TAG, "Cloud Vision empty, falling back to ML Kit")
                    mlKitRecognize(scaled)
                }
            } else {
                Log.d(TAG, "Using ML Kit (offline or no API key)")
                mlKitRecognize(scaled)
            }

            if (scaled !== safe && scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            if (safe !== bitmap && !safe.isRecycled) safe.recycle()

            val trimmed = result.trim()
            Log.d(TAG, "═══ Result: ${trimmed.length} chars ═══")
            Log.d(TAG, "'${trimmed.take(200)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    // ═══════════════ ML Kit ═══════════════

    private suspend fun mlKitRecognize(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
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
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit error", e)
            ""
        }
    }

    // ═══════════════ Cloud Vision API ═══════════════

    private suspend fun cloudVisionRecognize(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Calling Cloud Vision API...")

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                Log.d(TAG, "Base64 length: ${base64.length}")

                val request = JSONObject().apply {
                    put("requests", JSONArray().apply {
                        put(JSONObject().apply {
                            put("image", JSONObject().apply {
                                put("content", base64)
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

                val apiKey = getApiKey()
                val url = URL("$VISION_API_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                conn.outputStream.use { it.write(request.toString().toByteArray()) }

                val code = conn.responseCode
                Log.d(TAG, "Response: $code")

                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    parseVisionResponse(body)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e(TAG, "Error $code: $err")
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

            val first = responses.getJSONObject(0)

            if (first.has("fullTextAnnotation")) {
                return first.getJSONObject("fullTextAnnotation").getString("text")
            }
            if (first.has("textAnnotations")) {
                val ann = first.getJSONArray("textAnnotations")
                if (ann.length() > 0) {
                    return ann.getJSONObject(0).getString("description")
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            ""
        }
    }

    // ═══════════════ مساعدات ═══════════════

    private fun ensureArgb8888(b: Bitmap): Bitmap {
        if (b.config == Bitmap.Config.ARGB_8888) return b
        return b.copy(Bitmap.Config.ARGB_8888, false) ?: b
    }

    private fun scaleForOcr(b: Bitmap, max: Int = 1600): Bitmap {
        if (b.width <= max && b.height <= max) return b
        val r = minOf(max.toFloat() / b.width, max.toFloat() / b.height)
        return Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true)
    }

    fun close() {
        try { mlKitRecognizer.close() } catch (_: Exception) {}
    }
}
