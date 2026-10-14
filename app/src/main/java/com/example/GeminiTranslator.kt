package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class TranslationResult {
    data class Success(
        val translatedSrt: String,
        val translatedItemCount: Int,
        val modelUsed: String = "gemini-3.5-flash"
    ) : TranslationResult()

    data class Error(
        val message: String,
        val isApiKeyMissing: Boolean = false
    ) : TranslationResult()
}

object GeminiTranslator {
    private const val TAG = "GeminiTranslator"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val CANDIDATE_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.5-flash"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank()) {
            return customKey.trim()
        }
        val buildConfigKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (e: Throwable) {
            ""
        }
        return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey.trim()
        } else {
            ""
        }
    }

    suspend fun translateSrtToHindi(
        rawSrt: String,
        customApiKey: String? = null
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            return@withContext TranslationResult.Error(
                message = "Gemini API Key not found. Please configure GEMINI_API_KEY in AI Studio Secrets or enter your key in settings.",
                isApiKeyMissing = true
            )
        }
        if (rawSrt.isBlank()) {
            return@withContext TranslationResult.Error(
                message = "No subtitle content found to translate."
            )
        }

        val prompt = buildTranslationPrompt(rawSrt)
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(
                        JSONObject().put(
                            "text",
                            "You are an expert subtitle localization engineer and translator specializing in converting video subtitles into natural, idiomatic Hindi written exclusively in standard Devanagari script for Text-to-Speech (TTS) dubbing. Always preserve the exact SRT format (indices, timestamps '00:00:00,000 --> 00:00:00,000', blank line separators) and any speaker identifiers (e.g., [Speaker Name]: or (Narrator):). Output ONLY the translated SRT text without markdown fences, explanation, conversational commentary, or greetings."
                        )
                    )
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)

            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.95)
            }
            put("generationConfig", genConfig)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        var lastErrorMessage = "Gemini translation failed."

        for ((index, model) in CANDIDATE_MODELS.withIndex()) {
            try {
                val endpointUrl = "$BASE_URL/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(endpointUrl)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBodyString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val code = response.code
                    Log.w(TAG, "Gemini API ($model) returned HTTP $code: $responseBodyString")
                    val errorMsg = parseErrorMessage(responseBodyString, code)
                    lastErrorMessage = errorMsg
                    if ((code == 429 || code == 503 || code == 500 || code == 404 || code == 504) && index < CANDIDATE_MODELS.size - 1) {
                        delay(600L)
                        continue
                    }
                    if (index < CANDIDATE_MODELS.size - 1) {
                        continue
                    }
                    return@withContext TranslationResult.Error(errorMsg)
                }

                val translatedText = parseGeminiResponseText(responseBodyString)
                if (translatedText.isBlank()) {
                    if (index < CANDIDATE_MODELS.size - 1) {
                        delay(500L)
                        continue
                    }
                    return@withContext TranslationResult.Error("No translation response received from Gemini API.")
                }

                val cleanedSrt = cleanSrtMarkdown(translatedText)
                val parsedCount = SrtParser.parse(cleanedSrt).size
                return@withContext TranslationResult.Success(
                    translatedSrt = cleanedSrt,
                    translatedItemCount = parsedCount,
                    modelUsed = model
                )
            } catch (e: Exception) {
                lastErrorMessage = "Gemini translation failed: ${e.localizedMessage ?: e.message}"
                if (index < CANDIDATE_MODELS.size - 1) {
                    delay(500L)
                    continue
                }
            }
        }
        TranslationResult.Error(lastErrorMessage)
    }

    private fun buildTranslationPrompt(rawSrt: String): String {
        return """
Translate the following SubRip (.SRT) subtitles into high quality, natural, conversational Hindi (Devanagari script).
CRITICAL RULES FOR TTS DUBBING:
1. Maintain the EXACT same SRT structure, sequence numbers, and timestamps:
   Example:
   1
   00:00:00,800 --> 00:00:04,500
   [Rahul]: Welcome to today's meeting team.
2. Translate all spoken dialogue into natural, fluent, grammatically correct Hindi in Devanagari script. Do NOT leave English text unless it is an untranslatable proper name or brand.
3. If speaker names are in brackets or parentheses like [Narrator]: or [David]:, keep speaker tags so diarization continues to work.
4. Output ONLY valid SRT content with clean lines and standard blank-line separators between items. Do NOT wrap in ```srt or ``` markdown code blocks.

INPUT SRT TO TRANSLATE:
$rawSrt
""".trimIndent()
    }

    private fun cleanSrtMarkdown(text: String): String {
        var clean = text.trim()
        val srtFenceRegex = Regex("^```(?:srt|SRT)?\\s*", RegexOption.IGNORE_CASE)
        clean = srtFenceRegex.replace(clean, "").trim()
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3).trim()
        }
        return clean
    }

    private fun parseGeminiResponseText(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val part = parts.getJSONObject(0)
                    part.optString("text", "")
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response JSON", e)
            ""
        }
    }

    private fun parseErrorMessage(jsonStr: String, statusCode: Int): String {
        return try {
            val root = JSONObject(jsonStr)
            val error = root.optJSONObject("error")
            val message = error?.optString("message") ?: ""
            when {
                statusCode == 400 && message.contains("API key not valid", ignoreCase = true) ->
                    "Invalid Gemini API Key. Please verify and enter a valid key."
                statusCode == 429 ->
                    "Gemini API rate limit exceeded. Please try again after a few moments."
                statusCode == 503 || message.contains("high demand", ignoreCase = true) ->
                    "Gemini AI is currently experiencing temporary high demand. Please try again in a few moments."
                message.isNotBlank() ->
                    "Gemini API Error ($statusCode): $message"
                else ->
                    "Gemini API request failed (HTTP $statusCode)"
            }
        } catch (e: Exception) {
            if (statusCode == 503) {
                "Gemini AI is currently experiencing high demand (503). Please try again in a moment."
            } else {
                "Gemini API server error (HTTP $statusCode)"
            }
        }
    }
}
