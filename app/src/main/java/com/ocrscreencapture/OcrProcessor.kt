package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * محرك OCR مزدوج:
 * - ML Kit: مجاني، بدون إنترنت، للإنجليزي
 * - OCR.space: مجاني، يدعم العربي + الإنجليزي
 */
class OcrProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OcrProcessor"
        private const val PREF_NAME = "ocr_prefs"
        private const val KEY_API_KEY = "ocr_space_api_key"
        private const val OCR_SPACE_URL = "https://api.ocr.space/parse/image"
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

            val result = if (hasApiKey() && isOnline()) {
                Log.d(TAG, "→ Using OCR.space (Arabic + English)")
                val r = ocrSpaceRecognize(bitmap)
                if (r.isBlank()) {
                    Log.w(TAG, "OCR.space empty, falling back to ML Kit")
                    mlKitRecognize(bitmap)
                } else {
                    r
                }
            } else {
                Log.d(TAG, "→ Using ML Kit (English only)")
                mlKitRecognize(bitmap)
            }

            val trimmed = result.trim()
            Log.d(TAG, "═══ Result: ${trimmed.length} chars ═══")
            Log.d(TAG, "'${trimmed.take(200)}'")
            trimmed

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    // ═══════════════ ML Kit (إنجليزي) ═══════════════

    private suspend fun mlKitRecognize(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                mlKitRecognizer.process(image)
                    .addOnSuccessListener { text ->
                        if (cont.isActive) cont.resume(text.text ?: "")
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

    // ═══════════════ OCR.space API (عربي + إنجليزي) ═══════════════

    private suspend fun ocrSpaceRecognize(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Calling OCR.space API...")

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                Log.d(TAG, "Base64 length: ${base64.length}")

                val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
                val body = buildString {
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"base64Image\"\r\n\r\n")
                    append("data:image/jpeg;base64,$base64\r\n")

                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
                    append("ara\r\n")

                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"OCREngine\"\r\n\r\n")
                    append("2\r\n")

                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"isOverlayRequired\"\r\n\r\n")
                    append("false\r\n")

                    append("--$boundary--\r\n")
                }

                val apiKey = getApiKey()
                val url = URL(OCR_SPACE_URL)
                val conn = url.openConnection() as HttpURLConnection

                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("apikey", apiKey)
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "OCR.space response: $code")

                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    Log.d(TAG, "Response: ${response.take(500)}")
                    parseOcrSpaceResponse(response)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e(TAG, "OCR.space error $code: $err")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR.space error", e)
                ""
            }
        }

    private fun parseOcrSpaceResponse(response: String): String {
        return try {
            val json = org.json.JSONObject(response)

            if (json.has("ParsedResults")) {
                val results = json.getJSONArray("ParsedResults")
                if (results.length() > 0) {
                    val first = results.getJSONObject(0)
                    if (first.has("ParsedText")) {
                        return first.getString("ParsedText")
                    }
                }
            }

            if (json.has("ErrorMessage")) {
                Log.e(TAG, "OCR.space error: ${json.getString("ErrorMessage")}")
            }

            ""
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            ""
        }
    }

    // ═══════════════ Cleanup ═══════════════

    fun close() {
        try { mlKitRecognizer.close() } catch (_: Exception) {}
    }
}
