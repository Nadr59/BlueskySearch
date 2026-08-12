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
 * مساعد ذكي متعدد المزودين
 * يجرب المزودين بالترتيب — إذا فشل أحدهم ينتقل للتالي
 */
class AiAssistant(private val context: Context) {

    companion object {
        private const val TAG = "AiAssistant"
        private const val PREF_NAME = "ocr_prefs"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ═══════════════ معلومات المزودين ═══════════════

    data class Provider(
        val id: String,
        val name: String,
        val nameAr: String,
        val url: String,
        val model: String,
        val freeNote: String
    )

    val providers = listOf(
        Provider(
            id = "groq",
            name = "Groq",
            nameAr = "Groq (سريع جداً)",
            url = "https://api.groq.com/openai/v1/chat/completions",
            model = "llama-3.3-70b-versatile",
            freeNote = "مجاني — سجل في console.groq.com"
        ),
        Provider(
            id = "gemini",
            name = "Gemini",
            nameAr = "Google Gemini",
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            model = "gemini-2.5-flash",
            freeNote = "مجاني — aistudio.google.com/app/apikey"
        ),
        Provider(
            id = "openrouter",
            name = "OpenRouter",
            nameAr = "OpenRouter (نماذج مجانية)",
            url = "https://openrouter.ai/api/v1/chat/completions",
            model = "meta-llama/llama-3.3-70b-instruct:free",
            freeNote = "مجاني — openrouter.ai/keys"
        ),
        Provider(
            id = "mistral",
            name = "Mistral",
            nameAr = "Mistral AI",
            url = "https://api.mistral.ai/v1/chat/completions",
            model = "mistral-small-latest",
            freeNote = "مجاني — console.mistral.ai"
        )
    )

    // ═══════════════ إدارة المفاتيح ═══════════════

    fun getKey(providerId: String): String {
        return prefs.getString("ai_key_$providerId", "") ?: ""
    }

    fun setKey(providerId: String, key: String) {
        prefs.edit().putString("ai_key_$providerId", key).apply()
    }

    fun hasAnyKey(): Boolean {
        return providers.any { getKey(it.id).isNotBlank() }
    }

    fun getAvailableProviders(): List<Provider> {
        return providers.filter { getKey(it.id).isNotBlank() }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ═══════════════ الأوامر ═══════════════

    suspend fun explain(text: String): String {
        val prompt = "اشرح هذا النص بوضوح وبأسلوب مبسط باللغة العربية. استخدم نقاطاً مرتبة:\n\n$text"
        return callWithFallback(prompt)
    }

    suspend fun translate(text: String): String {
        val hasArabic = text.any {
            it in '\u0600'..'\u06FF' ||
            it in '\u0750'..'\u077F' ||
            it in '\uFB50'..'\uFDFF' ||
            it in '\uFE70'..'\uFEFF'
        }

        val prompt = if (hasArabic) {
            "ترجم هذا النص من العربية إلى الإنجليزية. اكتب الترجمة فقط:\n\n$text"
        } else {
            "ترجم هذا النص من الإنجليزية إلى العربية. اكتب الترجمة فقط:\n\n$text"
        }
        return callWithFallback(prompt)
    }

    suspend fun expand(text: String): String {
        val prompt = "أعطني معلومات إضافية ومفيدة حول هذا الموضوع. " +
                "اكتب بأسلوب واضح ومرتب باللغة العربية:\n\n$text"
        return callWithFallback(prompt)
    }

    suspend fun ask(text: String, question: String): String {
        val prompt = "بناءً على النص التالي:\n\n$text\n\nالسؤال: $question\n\nأجب باللغة العربية:"
        return callWithFallback(prompt)
    }

    // ═══════════════ التبديل التلقائي ═══════════════

    private suspend fun callWithFallback(prompt: String): String {
        if (!isOnline()) {
            return "لا يوجد اتصال بالإنترنت"
        }

        val errors = mutableListOf<String>()

        for (provider in providers) {
            val key = getKey(provider.id)
            if (key.isBlank()) {
                continue
            }

            Log.d(TAG, "Trying ${provider.name}...")
            val result = tryProvider(provider, key, prompt)

            if (result != null && !result.startsWith("ERROR:")) {
                Log.d(TAG, "${provider.name} succeeded!")
                return result
            }

            val errorMsg = result ?: "Unknown error"
            Log.w(TAG, "${provider.name} failed: $errorMsg")
            errors.add("${provider.name}: $errorMsg")
        }

        return if (errors.isEmpty()) {
            "لم يتم إدخال أي API Key\nأضف مفتاحاً من إعدادات المساعد الذكي"
        } else {
            "فشلت كل المحاولات:\n${errors.joinToString("\n")}"
        }
    }

    /**
     * محاولة مزود واحد
     */
    private suspend fun tryProvider(
        provider: Provider,
        apiKey: String,
        prompt: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (provider.id == "gemini") {
                    callGemini(provider, apiKey, prompt)
                } else {
                    callOpenAICompatible(provider, apiKey, prompt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "${provider.id} exception: ${e.message}")
                "ERROR: ${e.message}"
            }
        }
    }

    // ═══════════════ OpenAI Compatible (Groq, OpenRouter, Mistral) ═══════════════

    private fun callOpenAICompatible(
        provider: Provider,
        apiKey: String,
        prompt: String
    ): String? {
        val requestBody = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }

        val url = URL(provider.url)
        val conn = url.openConnection() as HttpURLConnection

        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val code = conn.responseCode
        Log.d(TAG, "${provider.name} response: $code")

        return if (code == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            parseOpenAIResponse(response)
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.e(TAG, "${provider.name} error $code: ${err.take(200)}")
            "ERROR: $code — ${parseErrorMessage(err)}"
        }
    }

    private fun parseOpenAIResponse(response: String): String? {
        return try {
            val json = JSONObject(response)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.getString("content")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse OpenAI error", e)
            "ERROR: Parse failed"
        }
    }

    // ═══════════════ Gemini API ═══════════════

    private fun callGemini(
        provider: Provider,
        apiKey: String,
        prompt: String
    ): String? {
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

        val url = URL("${provider.url}?key=$apiKey")
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
        Log.d(TAG, "Gemini response: $code")

        return if (code == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            parseGeminiResponse(response)
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.e(TAG, "Gemini error $code: ${err.take(200)}")
            "ERROR: $code — ${parseErrorMessage(err)}"
        }
    }

    private fun parseGeminiResponse(response: String): String? {
        return try {
            val json = JSONObject(response)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Parse Gemini error", e)
            "ERROR: Parse failed"
        }
    }

    // ═══════════════ مساعدات ═══════════════

    private fun parseErrorMessage(error: String): String {
        return try {
            val json = JSONObject(error)
            when {
                json.has("error") -> {
                    val errObj = json.getJSONObject("error")
                    errObj.optString("message", "Unknown")
                }
                json.has("message") -> json.getString("message")
                else -> error.take(100)
            }
        } catch (_: Exception) {
            error.take(100)
        }
    }
}
