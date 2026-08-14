package com.ocrscreencapture

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiAssistant(private val context: Context) {

    companion object {
        private const val TAG = "AiAssistant"
        private const val PREF_NAME = "ocr_prefs"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    data class Provider(
        val id: String,
        val name: String,
        val nameAr: String,
        val url: String,
        val model: String,
        val visionModel: String,
        val freeNote: String
    )

    val providers = listOf(
        Provider(
            id = "hcnsec",
            name = "HCNSec",
            nameAr = "HCNSec (مجاني)",
            url = "https://api.hcnsec.cn/v1/chat/completions",
            model = "auto",
            visionModel = "auto",
            freeNote = "مجاني — hcnsec.cn"
        ),
        Provider(
            id = "groq",
            name = "Groq",
            nameAr = "Groq (سريع جداً)",
            url = "https://api.groq.com/openai/v1/chat/completions",
            model = "llama-3.3-70b-versatile",
            visionModel = "llama-3.2-90b-vision-preview",
            freeNote = "مجاني — console.groq.com"
        ),
        Provider(
            id = "gemini",
            name = "Gemini",
            nameAr = "Google Gemini",
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            model = "gemini-2.5-flash",
            visionModel = "gemini-2.5-flash",
            freeNote = "مجاني — aistudio.google.com/app/apikey"
        ),
        Provider(
            id = "openrouter",
            name = "OpenRouter",
            nameAr = "OpenRouter",
            url = "https://openrouter.ai/api/v1/chat/completions",
            model = "meta-llama/llama-3.3-70b-instruct:free",
            visionModel = "meta-llama/llama-3.2-90b-vision-instruct:free",
            freeNote = "مجاني — openrouter.ai/keys"
        ),
        Provider(
            id = "mistral",
            name = "Mistral",
            nameAr = "Mistral AI",
            url = "https://api.mistral.ai/v1/chat/completions",
            model = "mistral-small-latest",
            visionModel = "pixtral-12b-2409",
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

    // ═══════════════ أوامر نصية ═══════════════

    suspend fun explain(text: String): String {
        return callTextWithFallback(
            "اشرح هذا النص بوضوح وبأسلوب مبسط باللغة العربية:\n\n$text"
        )
    }

    suspend fun translate(text: String): String {
        val hasArabic = text.any {
            it in '\u0600'..'\u06FF' ||
            it in '\u0750'..'\u077F' ||
            it in '\uFB50'..'\uFDFF' ||
            it in '\uFE70'..'\uFEFF'
        }
        return callTextWithFallback(
            if (hasArabic) "ترجم هذا النص إلى الإنجليزية. اكتب الترجمة فقط:\n\n$text"
            else "ترجم هذا النص إلى العربية. اكتب الترجمة فقط:\n\n$text"
        )
    }

    suspend fun expand(text: String): String {
        return callTextWithFallback(
            "أعطني معلومات إضافية مفيدة حول هذا الموضوع بالعربية:\n\n$text"
        )
    }

    // ═══════════════ تحليل الصور (القديم — متوافق) ═══════════════

    data class AnalysisResult(
        val description: String = "",
        val classification: String = "",
        val keywords: List<String> = emptyList(),
        val detectedText: String = "",
        val analysis: String = "",
        val websites: List<Pair<String, String>> = emptyList(),
        val rawResponse: String = ""
    )

    suspend fun analyzeImage(bitmap: Bitmap): AnalysisResult {
        val prompt = """حلل هذه الصورة بالتفصيل. أجب بالضبط بهذا التنسيق:

===الوصف===
وصف مفصل ومطول لمحتوى الصورة بالعربية

===التصنيف===
نوع المحتوى (كتاب، منتج، منظر، شخص، واجهة، علامة تجارية، شعار، مبنى، أكل، حيوان، سيارة، إلخ)

===الكلمات===
كلمة1، كلمة2، كلمة3، كلمة4، كلمة5، كلمة6

===النص===
أي نص مكتوب أو مطبوع في الصورة. إذا لا يوجد نص مرئي اكتب "لا يوجد نص"

===التحليل===
معلومات إضافية ومفيدة ومتوسعة عن المحتوى. أضف حقائق مثيرة للاهتمام، تاريخ، إحصائيات، أو معلومات عامة مفيدة

===المواقع===
اقترح 5 إلى 8 مواقع وخدمات مرتبطة بمحتوى الصورة. أي موقع مفيد ومرتبط يصلح. اكتب بالشكل:
اسم الموقع | https://www.example.com"""

        val rawResult = callVisionWithFallback(bitmap, prompt)
        return parseAnalysisResponse(rawResult)
    }

    private fun parseAnalysisResponse(raw: String): AnalysisResult {
        return try {
            fun extractSection(marker: String, nextMarker: String? = null): String {
                val start = raw.indexOf(marker)
                if (start == -1) return ""
                val contentStart = start + marker.length
                val end = if (nextMarker != null) {
                    val idx = raw.indexOf(nextMarker, contentStart)
                    if (idx == -1) raw.length else idx
                } else {
                    raw.length
                }
                return raw.substring(contentStart, end).trim()
            }

            val description = extractSection("===الوصف===", "===التصنيف===")
            val classification = extractSection("===التصنيف===", "===الكلمات===")
            val keywordsStr = extractSection("===الكلمات===", "===النص===")
            val detectedText = extractSection("===النص===", "===التحليل===")
            val analysis = extractSection("===التحليل===", "===المواقع===")
            val websitesStr = extractSection("===المواقع===")

            val keywords = keywordsStr
                .split("،", ",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val websites = websitesStr.lines()
                .filter { it.contains("|") }
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val url = parts[1].trim()
                        if (name.isNotBlank() && url.isNotBlank()) name to url else null
                    } else null
                }

            if (description.isBlank() && analysis.isBlank()) {
                return AnalysisResult(rawResponse = raw)
            }

            AnalysisResult(
                description = description,
                classification = classification,
                keywords = keywords,
                detectedText = detectedText,
                analysis = analysis,
                websites = websites,
                rawResponse = raw
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse analysis failed", e)
            AnalysisResult(rawResponse = raw)
        }
    }

    // ═══════════════ تحليل متعدد المناهج ═══════════════

    suspend fun analyzeWithMethods(
        bitmap: Bitmap,
        methods: List<AnalysisMethod>
    ): Map<String, String> {
        val prompt = AnalysisMethod.buildCombinedPrompt(methods)
        val rawResult = callVisionWithFallback(bitmap, prompt)
        return AnalysisMethod.parseSections(rawResult, methods)
    }

    // ═══════════════ التبديل التلقائي (نص) ═══════════════

    private suspend fun callTextWithFallback(prompt: String): String {
        if (!isOnline()) return "لا يوجد اتصال بالإنترنت"

        val errors = mutableListOf<String>()
        for (provider in providers) {
            val key = getKey(provider.id)
            if (key.isBlank()) continue

            Log.d(TAG, "Text → Trying ${provider.name}")
            val result = tryTextProvider(provider, key, prompt)
            if (result != null && !result.startsWith("ERROR:")) return result

            errors.add("${provider.name}: ${result ?: "Unknown"}")
        }

        return if (errors.isEmpty()) "لم يتم إدخال أي API Key"
        else "فشلت كل المحاولات:\n${errors.joinToString("\n")}"
    }

    private suspend fun tryTextProvider(
        provider: Provider, apiKey: String, prompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (provider.id == "gemini") callGeminiText(provider, apiKey, prompt)
            else callOpenAIText(provider, apiKey, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "${provider.id} text error", e)
            "ERROR: ${e.message}"
        }
    }

    // ═══════════════ التبديل التلقائي (صور) ═══════════════

    private suspend fun callVisionWithFallback(bitmap: Bitmap, prompt: String): String {
        if (!isOnline()) return "لا يوجد اتصال بالإنترنت"

        val resized = resizeBitmap(bitmap, 1024)
        val errors = mutableListOf<String>()

        for (provider in providers) {
            val key = getKey(provider.id)
            if (key.isBlank()) continue
            if (provider.visionModel.isBlank()) continue

            Log.d(TAG, "Vision → Trying ${provider.name}")
            val result = tryVisionProvider(provider, key, resized, prompt)
            if (result != null && !result.startsWith("ERROR:")) {
                if (resized !== bitmap) resized.recycle()
                return result
            }

            errors.add("${provider.name}: ${result ?: "Unknown"}")
        }

        if (resized !== bitmap) resized.recycle()
        return if (errors.isEmpty()) "لم يتم إدخال أي API Key يدعم تحليل الصور"
        else "فشلت كل المحاولات:\n${errors.joinToString("\n")}"
    }

    private suspend fun tryVisionProvider(
        provider: Provider, apiKey: String, bitmap: Bitmap, prompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (provider.id == "gemini") callGeminiVision(provider, apiKey, bitmap, prompt)
            else callOpenAIVision(provider, apiKey, bitmap, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "${provider.id} vision error", e)
            "ERROR: ${e.message}"
        }
    }

    // ═══════════════ OpenAI Compatible (نص) ═══════════════

    private fun callOpenAIText(provider: Provider, apiKey: String, prompt: String): String? {
        val body = JSONObject().apply {
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
        return callOpenAIApi(provider.url, apiKey, body, provider.model)
    }

    // ═══════════════ OpenAI Compatible (صور) ═══════════════

    private fun callOpenAIVision(
        provider: Provider, apiKey: String, bitmap: Bitmap, prompt: String
    ): String? {
        val base64 = bitmapToBase64(bitmap)

        val body = JSONObject().apply {
            put("model", provider.visionModel)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64")
                            })
                        })
                    })
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }
        return callOpenAIApi(provider.url, apiKey, body, provider.visionModel)
    }

    private fun callOpenAIApi(
        urlStr: String, apiKey: String, body: JSONObject, model: String
    ): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 60000
            readTimeout = 120000
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(body.toString())
            it.flush()
        }

        val code = conn.responseCode
        Log.d(TAG, "OpenAI ($model) response: $code")

        return if (code == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            parseOpenAIResponse(response)
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.e(TAG, "OpenAI error $code: ${err.take(200)}")
            "ERROR: $code — ${extractErrorMessage(err)}"
        }
    }

    private fun parseOpenAIResponse(response: String): String? {
        return try {
            val json = JSONObject(response)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Parse OpenAI error", e)
            "ERROR: Parse failed"
        }
    }

    // ═══════════════ Gemini (نص) ═══════════════

    private fun callGeminiText(
        provider: Provider, apiKey: String, prompt: String
    ): String? {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
        return callGeminiApi(provider.url, apiKey, body)
    }

    // ═══════════════ Gemini (صور) ═══════════════

    private fun callGeminiVision(
        provider: Provider, apiKey: String, bitmap: Bitmap, prompt: String
    ): String? {
        val base64 = bitmapToBase64(bitmap)

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
        return callGeminiApi(provider.url, apiKey, body)
    }

    private fun callGeminiApi(urlStr: String, apiKey: String, body: JSONObject): String? {
        val conn = URL("$urlStr?key=$apiKey").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 60000
            readTimeout = 120000
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(body.toString())
            it.flush()
        }

        val code = conn.responseCode
        Log.d(TAG, "Gemini response: $code")

        return if (code == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            parseGeminiResponse(response)
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.e(TAG, "Gemini error $code: ${err.take(200)}")
            "ERROR: $code — ${extractErrorMessage(err)}"
        }
    }

    private fun parseGeminiResponse(response: String): String? {
        return try {
            val json = JSONObject(response)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Parse Gemini error", e)
            "ERROR: Parse failed"
        }
    }

    // ═══════════════ مساعدات ═══════════════

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    private fun extractErrorMessage(error: String): String {
        return try {
            val json = JSONObject(error)
            when {
                json.has("error") -> json.getJSONObject("error").optString("message", "Unknown")
                json.has("message") -> json.getString("message")
                else -> error.take(100)
            }
        } catch (_: Exception) {
            error.take(100)
        }
    }
}
