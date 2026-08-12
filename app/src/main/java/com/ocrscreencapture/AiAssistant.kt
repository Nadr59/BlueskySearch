package com.ocrscreencapture

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * مساعد ذكي يستخدم Google Gemini API
 * مجاني: 15 طلب/دقيقة — أكثر من كافي
 */
class AiAssistant(private val context: Context) {

    companion object {
        private const val TAG = "AiAssistant"
        private const val PREF_NAME = "ocr_prefs"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ═══════════════ API Key ═══════════════

    fun getApiKey(): String = prefs.getString(KEY_GEMINI, "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ═══════════════ الأوامر ═══════════════

    /**
     * شرح النص
     */
    suspend fun explain(text: String): String {
        val prompt = "اشرح هذا النص بوضوح وبأسلوب مبسط باللغة العربية:\n\n$text"
        return callGemini(prompt)
    }

    /**
     * ترجمة النص
     */
    suspend fun translate(text: String): String {
        val hasArabic = text.any {
            it in '\u0600'..'\u06FF' ||
            it in '\u0750'..'\u077F' ||
            it in '\uFB50'..'\uFDFF' ||
            it in '\uFE70'..'\uFEFF'
        }

        val prompt = if (hasArabic) {
            "ترجم هذا النص من العربية إلى الإنجليزية. اكتب الترجمة فقط بدون شرح:\n\n$text"
        } else {
            "ترجم هذا النص من الإنجليزية إلى العربية. اكتب الترجمة فقط بدون شرح:\n\n$text"
        }
        return callGemini(prompt)
    }

    /**
     * التوسع في النص
     */
    suspend fun expand(text: String): String {
        val prompt = "أعطني معلومات إضافية ومفيدة حول هذا الموضوع. " +
                "اكتب بأسلوب واضح ومرتب باللغة العربية:\n\n$text"
        return callGemini(prompt)
    }

    /**
     * سؤال حر
     */
    suspend fun ask(text: String, question: String): String {
        val prompt = "بناءً على النص التالي:\n\n$text\n\nالسؤال: $question\n\nأجب باللغة العربية:"
        return callGemini(prompt)
    }

    // ═══════════════ Gemini API ═══════════════

    private suspend fun callGemini(prompt: String): String =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) {
                    return@withContext "لم يتم إدخال Gemini API Key"
                }

                Log.d(TAG, "Calling Gemini API...")
                Log.d(TAG, "Prompt: ${prompt.take(100)}...")

                // بناء الطلب
                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 2048)
                    })
                }

                val url = URL("$GEMINI_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection

                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 60000
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                Log.d(TAG, "Response: $code")

                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    parseGeminiResponse(response)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e(TAG, "Error $code: $err")
                    parseError(code, err)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Gemini error", e)
                "خطأ: ${e.message}"
            }
        }

    private fun parseGeminiResponse(response: String): String {
        return try {
            val json = JSONObject(response)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0)
                    .getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
            "لم يتم الحصول على رد"
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            "خطأ في تحليل الرد: ${e.message}"
        }
    }

    private fun parseError(code: Int, error: String): String {
        return try {
            val json = JSONObject(error)
            if (json.has("error")) {
                val errObj = json.getJSONObject("error")
                val message = errObj.optString("message", "Unknown")
                when (code) {
                    400 -> "خطأ في الطلب: $message"
                    403 -> "مفتاح API غير صالح — تأكد من المفتاح"
                    429 -> "تم تجاوز الحد المسموح — انتظر قليلاً"
                    500 -> "خطأ في خادم Google — حاول لاحقاً"
                    else -> "خطأ $code: $message"
                }
            } else {
                "خطأ $code"
            }
        } catch (e: Exception) {
            "خطأ $code"
        }
    }
}
