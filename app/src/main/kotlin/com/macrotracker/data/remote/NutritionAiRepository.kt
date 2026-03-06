package com.macrotracker.data.remote

import android.util.Log
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class NutritionEstimate(
    val foodName: String,
    val servingDescription: String,
    val calories: Int,
    val protein: Int,
    val confidence: String,   // "low", "medium", "high"
    val notes: String,
)

data class ScanResult(
    val foodName: String,
    val caloriesPerServing: Int,
    val proteinPerServing: Int,
    val servingsPerContainer: Double,
    val servingSizeGrams: Int,
    val packageWeightGrams: Int,
    val totalCalories: Int,
    val totalProtein: Int,
)

@Singleton
class NutritionAiRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val apiKey: String
        get() {
            // Always prefer the key saved in the app's Settings UI
            val stored = settings.getGeminiApiKey().trim()
            if (stored.isNotBlank()) {
                Log.d(TAG, "Using Gemini key from app Settings (${stored.take(8)}…)")
                return stored
            }
            // Fall back to build-time key from local.properties (usually empty)
            val buildKey = BuildConfig.GEMINI_API_KEY.trim()
            if (buildKey.isNotBlank()) {
                Log.d(TAG, "Using Gemini key from BuildConfig (${buildKey.take(8)}…)")
                return buildKey
            }
            Log.w(TAG, "No Gemini API key configured")
            return ""
        }

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val TAG = "NutritionAI"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        private val MODELS = listOf("gemini-2.0-flash", "gemini-2.5-flash")
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // ─── Estimate nutrition from text ─────────────────────────────────────────
    suspend fun estimateNutritionWithAI(foodQuery: String): NutritionEstimate {
        if (foodQuery.isBlank()) throw Exception("Enter a food to estimate first.")
        requireApiKey()

        val prompt = """
            Estimate nutrition values for this food query: "$foodQuery".
            Use common nutrition databases and practical serving assumptions.
            Return ONLY a JSON object with this exact shape:
            {
              "foodName": "string",
              "servingDescription": "string",
              "calories": number,
              "protein": number,
              "confidence": "low" | "medium" | "high",
              "notes": "brief caveat"
            }
            Rules:
            - Calories and protein must be non-negative numbers.
            - If uncertain, provide best estimate and set confidence accordingly.
            - Keep notes under 120 characters.
        """.trimIndent()

        val partsArray = JSONArray().put(JSONObject().put("text", prompt))
        val responseText = callGeminiWithFallback(partsArray)
        return parseNutritionEstimate(responseText, foodQuery)
    }

    // ─── Scan image for nutrition label ───────────────────────────────────────
    suspend fun analyzeImageWithGemini(base64Image: String): ScanResult {
        requireApiKey()

        val prompt = """
            Read the nutrition facts label in this image.
            Return ONLY JSON with these keys:
            {
              "foodName": string,
              "caloriesPerServing": number,
              "proteinPerServing": number,
              "servingsPerContainer": number,
              "servingSizeGrams": number,
              "packageWeightGrams": number
            }
            Use 0 for missing numbers. No markdown. No explanation.
        """.trimIndent()

        val partsArray = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Image),
                ),
            )

        val responseText = callGeminiWithFallback(partsArray)
        return parseScanResult(responseText)
    }

    // ─── Core Gemini call — plain OkHttp + JSONObject, no Moshi/Retrofit ─────
    private suspend fun callGeminiWithFallback(partsArray: JSONArray): String {
        val key = apiKey
        var lastError = ""
        var lastCode = 0

        for (model in MODELS) {
            // Try unstructured first (more widely supported), then structured JSON
            for (structured in listOf(false, true)) {
                for (attempt in 0..1) {
                    if (attempt > 0) delay(2000) // back off before retry

                    val url = "$BASE_URL/$model:generateContent?key=$key"

                    val generationConfig = JSONObject()
                        .put("temperature", 0.2)
                        .put("maxOutputTokens", 1024)

                    if (structured) {
                        generationConfig.put("responseMimeType", "application/json")
                    }

                    val body = JSONObject()
                        .put(
                            "contents",
                            JSONArray().put(
                                JSONObject().put("parts", partsArray),
                            ),
                        )
                        .put("generationConfig", generationConfig)

                    Log.d(TAG, "→ $model | structured=$structured | attempt=$attempt")

                    try {
                        val (code, responseBody) = doPost(url, body.toString())

                        Log.d(TAG, "← $model | $code | ${responseBody.take(200)}")

                        if (code in 200..299) {
                            val text = extractTextFromResponse(responseBody)
                            if (text.isNotBlank()) return text
                            Log.w(TAG, "Empty response body from $model")
                        }

                        lastCode = code
                        lastError = responseBody

                        // Fatal key errors — throw immediately
                        if (isApiKeyError(code, responseBody)) {
                            throw Exception("Gemini API key is invalid or unauthorized. Check Stats → Settings → Gemini API Key.")
                        }

                        // Rate limit — retry once, then move to next model
                        if (code == 429 || isRateLimitError(responseBody)) {
                            if (attempt == 0) continue
                            break
                        }

                        // Model not found — skip to next model
                        if (code == 404) break

                        // Structured not supported on this model — try unstructured
                        if (code == 400 && structured) break

                    } catch (e: Exception) {
                        // Re-throw user-facing errors
                        if (e.message?.contains("API key") == true) throw e
                        Log.e(TAG, "Exception calling $model: ${e.message}")
                        lastError = e.message ?: "Unknown error"
                    }
                }
            }
            // Small delay before next model
            delay(300)
        }

        // All models exhausted
        if (isRateLimitError(lastError) || lastCode == 429) {
            throw Exception("Gemini rate limit reached — wait ~60 seconds and try again.")
        }
        val snippet = lastError.replace(Regex("<[^>]+>"), "").take(200).trim()
        throw Exception("AI request failed ($lastCode): $snippet".ifBlank { "AI request failed. Check your API key and internet connection." })
    }

    // ─── HTTP helper — runs on IO dispatcher ─────────────────────────────────
    private suspend fun doPost(url: String, jsonBody: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(request).execute().use { response ->
                Pair(response.code, response.body?.string() ?: "")
            }
        }

    // ─── Response parsing ────────────────────────────────────────────────────
    private fun extractTextFromResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)

            // Check for truncation
            val finishReason = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optString("finishReason", "") ?: ""
            if (finishReason == "MAX_TOKENS") {
                throw Exception("AI response was truncated. Try a shorter description.")
            }

            val parts = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")

            if (parts == null || parts.length() == 0) return ""

            buildString {
                for (i in 0 until parts.length()) {
                    val text = parts.optJSONObject(i)?.optString("text", "") ?: ""
                    if (text.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }.trim()
        } catch (e: Exception) {
            if (e.message?.contains("truncated") == true) throw e
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
            ""
        }
    }

    // ─── Error classification ────────────────────────────────────────────────
    private fun isApiKeyError(code: Int, body: String): Boolean {
        if (code == 401 || code == 403) return true
        val lower = body.lowercase()
        return lower.contains("api_key_invalid") ||
                lower.contains("api key invalid") ||
                lower.contains("api key not valid") ||
                lower.contains("api key expired")
    }

    private fun isRateLimitError(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("resource_exhausted") ||
                lower.contains("quota") ||
                lower.contains("rate limit") ||
                lower.contains("too many requests")
    }

    private fun requireApiKey() {
        if (!hasApiKey) {
            throw Exception(
                "No Gemini API key set. Go to Stats → Settings and paste your free key from aistudio.google.com.",
            )
        }
    }

    // ─── JSON Parsing helpers ────────────────────────────────────────────────
    private fun parseNutritionEstimate(rawText: String, fallbackName: String): NutritionEstimate {
        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
        val json = extractJsonObject(cleaned)
        return try {
            val obj = JSONObject(json)
            NutritionEstimate(
                foodName = obj.optString("foodName", fallbackName),
                servingDescription = obj.optString("servingDescription", "1 serving"),
                calories = maxOf(0, obj.optDouble("calories", 0.0).toInt()),
                protein = maxOf(0, obj.optDouble("protein", 0.0).toInt()),
                confidence = normalizeConfidence(obj.optString("confidence", "medium")),
                notes = obj.optString("notes", "Estimate only. Verify with package label when possible."),
            )
        } catch (_: Exception) {
            throw Exception("Could not parse the AI estimate. Please try again.")
        }
    }

    private fun parseScanResult(rawText: String): ScanResult {
        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
        val json = extractJsonObject(cleaned)
        return try {
            val obj = JSONObject(json)
            maybeComputeTotals(
                foodName = obj.optString("foodName", "Scanned Food"),
                caloriesPerServing = maxOf(0, obj.optDouble("caloriesPerServing", 0.0).toInt()),
                proteinPerServing = maxOf(0, obj.optDouble("proteinPerServing", 0.0).toInt()),
                servingsPerContainer = maxOf(0.0, obj.optDouble("servingsPerContainer", 0.0)),
                servingSizeGrams = maxOf(0, obj.optDouble("servingSizeGrams", 0.0).toInt()),
                packageWeightGrams = maxOf(0, obj.optDouble("packageWeightGrams", 0.0).toInt()),
            )
        } catch (_: Exception) {
            throw Exception("Could not parse AI response. Please retake the photo and try again.")
        }
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) text.substring(start, end + 1) else text
    }

    private fun normalizeConfidence(value: String): String {
        return when (value.trim().lowercase()) {
            "low", "medium", "high" -> value.trim().lowercase()
            else -> "medium"
        }
    }
}

fun maybeComputeTotals(
    foodName: String,
    caloriesPerServing: Int,
    proteinPerServing: Int,
    servingsPerContainer: Double,
    servingSizeGrams: Int,
    packageWeightGrams: Int,
): ScanResult {
    var servings = servingsPerContainer
    var servingSize = servingSizeGrams
    var packageWeight = packageWeightGrams

    if (servings <= 0 && servingSize > 0 && packageWeight > 0) {
        servings = packageWeight.toDouble() / servingSize
    }
    if (servingSize <= 0 && servings > 0 && packageWeight > 0) {
        servingSize = (packageWeight / servings).toInt()
    }
    if (packageWeight <= 0 && servings > 0 && servingSize > 0) {
        packageWeight = (servings * servingSize).toInt()
    }

    return ScanResult(
        foodName = foodName.ifBlank { "Scanned Food" },
        caloriesPerServing = caloriesPerServing,
        proteinPerServing = proteinPerServing,
        servingsPerContainer = servings,
        servingSizeGrams = servingSize,
        packageWeightGrams = packageWeight,
        totalCalories = (caloriesPerServing * servings).toInt(),
        totalProtein = (proteinPerServing * servings).toInt(),
    )
}

