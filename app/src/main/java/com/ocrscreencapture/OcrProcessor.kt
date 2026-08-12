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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

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

    // ✅ سجل التشخيص المرئي
    var debugLog = StringBuilder()
        private set

    private fun log(msg: String) {
        Log.d(TAG, msg)
        debugLog.appendLine(msg)
    }

    fun clearDebugLog() {
        debugLog.clear()
    }

    // ═══════════════ API Key ═══════════════

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun hasApiKey(): Boolean {
        val key = getApiKey()
        log("API Key: '${key.take(8)}...' (length=${key.length})")
        return key.isNotBlank()
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        log("Online: $online")
        return online
    }

    // ═══════════════ المعالجة الرئيسية ═══════════════

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        debugLog.clear()
        try {
            log("═══ بدء المعالجة ═══")
            log("الأبعاد: ${bitmap.width}x${bitmap.height}")

            val keyExists = hasApiKey()
            val online = isOnline()

            val result = if (keyExists && online) {
                log("→ استخدام OCR.space (عربي + إنجليزي)")
                val r = ocrSpaceRecognize(bitmap)
                if (r.isBlank()) {
                    log("← OCR.space فارغ — استخدام ML Kit")
                    mlKitRecognize(bitmap)
                } else {
                    r
                }
            } else {
                if (!keyExists) log("← لا يوجد API Key — ML Kit فقط")
                if (!online) log("← لا يوجد إنترنت — ML Kit فقط")
                log("→ استخدام ML Kit (إنجليزي فقط)")
                mlKitRecognize(bitmap)
            }

            val trimmed = result.trim()
            log("═══ النتيجة: ${trimmed.length} حرف ═══")
            log("'${trimmed.take(100)}'")
            trimmed

        } catch (e: Exception) {
            log("❌ خطأ: ${e.message}")
            Log.e(TAG, "OCR error", e)
            ""
        }
    }

    // ═══════════════ ML Kit (إنجليزي) ═══════════════

    private suspend fun mlKitRecognize(bitmap: Bitmap): String {
        return try {
            log("ML Kit: معالجة...")
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = suspendCancellableCoroutine { cont ->
                mlKitRecognizer.process(image)
                    .addOnSuccessListener { t ->
                        if (cont.isActive) cont.resume(t.text ?: "")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit fail", e)
                        if (cont.isActive) cont.resume("")
                    }
            }
            log("ML Kit: ${text.length} حرف")
            text
        } catch (e: Exception) {
            log("ML Kit خطأ: ${e.message}")
            ""
        }
    }

    // ═══════════════ OCR.space API ═══════════════

    private suspend fun ocrSpaceRecognize(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            try {
                log("OCR.space: تحويل الصورة...")
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val imageBytes = stream.toByteArray()
                val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                log("OCR.space: حجم base64 = ${base64.length}")

                // ✅ استخدام URL-encoded form data (أبسط وأكثر موثوقية)
                val postData = buildString {
                    append("base64Image=")
                    append(URLEncoder.encode("data:image/jpeg;base64,$base64", "UTF-8"))
                    append("&language=ara")
                    append("&OCREngine=2")
                    append("&isOverlayRequired=false")
                    append("&scale=true")
                    append("&detectOrientation=true")
                }
                log("OCR.space: حجم الطلب = ${postData.length}")

                val apiKey = getApiKey()
                log("OCR.space: إرسال الطلب...")

                val url = URL(OCR_SPACE_URL)
                val conn = url.openConnection() as HttpURLConnection

                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("apikey", apiKey)
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 60000
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val code = conn.responseCode
                log("OCR.space: رد الخادم = $code")

                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    log("OCR.space: طول الرد = ${response.length}")
                    log("OCR.space: الرد = ${response.take(300)}")
                    parseOcrSpaceResponse(response)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    log("❌ OCR.space خطأ $code")
                    log("❌ ${err.take(200)}")
                    ""
                }
            } catch (e: Exception) {
                log("❌ OCR.space استثناء: ${e.message}")
                Log.e(TAG, "OCR.space error", e)
                ""
            }
        }

    private fun parseOcrSpaceResponse(response: String): String {
        return try {
            val json = org.json.JSONObject(response)
            log("Parse: keys=${json.keys().asSequence().toList()}")

            if (json.has("IsErroredOnProcessing")) {
                val errored = json.getBoolean("IsErroredOnProcessing")
                if (errored) {
                    val errMsg = if (json.has("ErrorMessage")) {
                        json.getString("ErrorMessage")
                    } else {
                        "Unknown error"
                    }
                    log("❌ OCR.space معالجة فاشلة: $errMsg")
                    return ""
                }
            }

            if (json.has("ParsedResults")) {
                val results = json.getJSONArray("ParsedResults")
                log("Parse: ${results.length()} نتيجة")
                if (results.length() > 0) {
                    val first = results.getJSONObject(0)
                    if (first.has("ParsedText")) {
                        val text = first.getString("ParsedText")
                        log("Parse: نص = ${text.take(100)}")
                        return text
                    }
                    if (first.has("ErrorMessage")) {
                        log("❌ خطأ نتيجة: ${first.getString("ErrorMessage")}")
                    }
                }
            }

            log("❌ لا يوجد ParsedResults")
            ""
        } catch (e: Exception) {
            log("❌ خطأ تحليل JSON: ${e.message}")
            Log.e(TAG, "Parse error", e)
            ""
        }
    }

    // ═══════════════ Cleanup ═══════════════

    fun close() {
        try { mlKitRecognizer.close() } catch (_: Exception) {}
    }
}
