package com.macrotracker.data.remote

import android.util.Log
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
import kotlin.math.roundToInt

data class WeatherAiResult(
    val summary: String,
    val clothingRecommendation: String,
)

@Singleton
class WeatherAiRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
) {
    companion object {
        private const val TAG = "WeatherAI"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val MODELS = listOf("gemini-2.0-flash", "gemini-2.5-flash")
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }

    // In-memory cache: summary + clothing together
    private var cachedResult: WeatherAiResult? = null
    private var cachedTimestamp: Long = 0L
    private var cachedSymbolCode: String? = null

    fun getCachedResult(symbolCode: String): WeatherAiResult? {
        val now = System.currentTimeMillis()
        return if (cachedResult != null &&
            cachedSymbolCode == symbolCode &&
            (now - cachedTimestamp) < CACHE_TTL_MS
        ) cachedResult else null
    }

    private val apiKey: String
        get() = settings.getGeminiApiKey().trim()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    // ── Public entry point ──────────────────────────────────────────────────
    /**
     * Produces a [WeatherAiResult] containing:
     *  • An AI-generated weather summary (via Gemini – plain text, no JSON).
     *  • A deterministic clothing recommendation built from the weather data
     *    so it **always** appears even if the AI call fails.
     */
    suspend fun generateWeatherSummary(weather: WeatherInfo): WeatherAiResult? {
        if (!hasApiKey) return null

        val cached = getCachedResult(weather.symbolCode)
        if (cached != null) return cached

        // Clothing is deterministic — compute it first so it never fails
        val clothing = buildClothingRecommendation(weather)

        // AI summary — plain-text prompt, no JSON requirement
        val summary = fetchAiSummary(weather)
            ?: return WeatherAiResult(
                summary = buildFallbackSummary(weather),
                clothingRecommendation = clothing,
            ).also { cacheResult(it, weather.symbolCode) }

        val result = WeatherAiResult(summary = summary, clothingRecommendation = clothing)
        cacheResult(result, weather.symbolCode)
        return result
    }

    private fun cacheResult(result: WeatherAiResult, symbolCode: String) {
        cachedResult = result
        cachedTimestamp = System.currentTimeMillis()
        cachedSymbolCode = symbolCode
    }

    // ── AI summary (plain text only – most reliable) ────────────────────────
    private suspend fun fetchAiSummary(weather: WeatherInfo): String? {
        val hourlyDesc = weather.hourlyForecasts.take(8).joinToString("; ") { h ->
            "${h.time}: ${h.temperature.roundToInt()}°C ${h.description}"
        }

        val prompt = buildString {
            append("Summarize today's weather in 2-3 short sentences. ")
            append("Mention the current temperature, how the day progresses, wind, and whether to bring an umbrella or sunglasses. ")
            append("Use plain text only. No markdown, no bullet points, no emojis, no headings.\n\n")
            append("Current: ${weather.temperature.roundToInt()}°C, ${weather.description}, wind ${weather.windSpeed.roundToInt()} m/s.\n")
            if (hourlyDesc.isNotBlank()) append("Next hours: $hourlyDesc\n")
        }

        return try {
            callGemini(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed: ${e.message}", e)
            null
        }
    }

    // ── Deterministic clothing recommendation ───────────────────────────────
    /**
     * Builds a clothing recommendation purely from weather numbers and
     * conditions so it **always** works, no network/AI required.
     */
    private fun buildClothingRecommendation(weather: WeatherInfo): String {
        val temp = weather.temperature
        val wind = weather.windSpeed
        val desc = weather.description.lowercase()
        val hourlyDescs = weather.hourlyForecasts.take(12).map { it.description.lowercase() }
        val allDescs = listOf(desc) + hourlyDescs

        val hasRain = allDescs.any { it.contains("rain") || it.contains("shower") || it.contains("drizzle") }
        val hasSnow = allDescs.any { it.contains("snow") || it.contains("sleet") }
        val isClear = desc.contains("clear") || desc.contains("fair")
        val isWindy = wind >= 8.0

        val parts = mutableListOf<String>()

        // Core layer advice by temperature band
        when {
            temp <= -10 -> parts.add("Wear a heavy winter coat with insulated layers, thermal underwear, and warm boots.")
            temp <= 0   -> parts.add("A thick winter jacket with a warm sweater underneath and insulated boots are essential.")
            temp <= 5   -> parts.add("Wear a warm winter coat or heavy parka with layered clothing and sturdy boots.")
            temp <= 10  -> parts.add("A warm jacket or insulated coat with a sweater or hoodie underneath is recommended.")
            temp <= 15  -> parts.add("A medium-weight jacket or fleece with a long-sleeve shirt should keep you comfortable.")
            temp <= 20  -> parts.add("A light jacket or cardigan is ideal, with a t-shirt or light long-sleeve underneath.")
            temp <= 25  -> parts.add("Light clothing like a t-shirt and comfortable trousers or shorts will work well.")
            else        -> parts.add("Wear light, breathable clothing such as a t-shirt and shorts to stay cool.")
        }

        // Accessories
        val accessories = mutableListOf<String>()
        if (temp <= 0) {
            accessories.add("a warm hat")
            accessories.add("insulated gloves")
            accessories.add("a scarf")
        } else if (temp <= 8) {
            accessories.add("a beanie or hat")
            accessories.add("gloves")
        }
        if (isWindy && temp <= 15) accessories.add("a windproof outer layer")
        if (isWindy && temp > 15) accessories.add("a light windbreaker")
        if (hasRain) accessories.add("a waterproof jacket and an umbrella")
        if (hasSnow && !accessories.any { it.contains("waterproof") }) accessories.add("waterproof outerwear")
        if (isClear && temp > 15) accessories.add("sunglasses")
        if (isClear && temp > 22) accessories.add("a hat for sun protection")

        if (accessories.isNotEmpty()) {
            parts.add("Bring ${accessories.joinToString(", ")}.")
        }

        // Footwear refinement
        if (hasRain || hasSnow) {
            parts.add("Waterproof footwear is a good idea today.")
        }

        return parts.joinToString(" ")
    }

    // ── Fallback summary when AI is unavailable ─────────────────────────────
    private fun buildFallbackSummary(weather: WeatherInfo): String {
        val temp = weather.temperature.roundToInt()
        val wind = weather.windSpeed.roundToInt()
        val desc = weather.description

        val progression = weather.hourlyForecasts.take(8).let { forecasts ->
            if (forecasts.isEmpty()) "" else {
                val minT = forecasts.minOf { it.temperature }.roundToInt()
                val maxT = forecasts.maxOf { it.temperature }.roundToInt()
                " Temperatures will range from ${minT}°C to ${maxT}°C over the coming hours."
            }
        }
        return "Currently ${temp}°C with ${desc.lowercase()} and winds around ${wind} m/s.$progression"
    }

    // ── Gemini call — plain text, unstructured, with retries ────────────────
    private suspend fun callGemini(prompt: String): String? {
        val key = apiKey
        val partsArray = JSONArray().put(JSONObject().put("text", prompt))

        for (model in MODELS) {
            for (attempt in 0..2) {                           // 3 attempts per model
                if (attempt > 0) delay(1500L * attempt)       // back off

                try {
                    val url = "$BASE_URL/$model:generateContent?key=$key"

                    val generationConfig = JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 512)

                    val body = JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("parts", partsArray)))
                        .put("generationConfig", generationConfig)

                    Log.d(TAG, "→ $model attempt=$attempt")

                    val (code, responseBody) = doPost(url, body.toString())

                    Log.d(TAG, "← $model $code ${responseBody.take(200)}")

                    if (code in 200..299) {
                        val finishReason = try {
                            JSONObject(responseBody)
                                .optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optString("finishReason", "")
                        } catch (_: Exception) { "" }

                        if (finishReason == "MAX_TOKENS") {
                            Log.w(TAG, "$model truncated, retrying…")
                            continue
                        }

                        val text = extractText(responseBody)
                        if (text.isNotBlank()) return cleanSummaryText(text)
                    }

                    // Fatal key errors — stop immediately
                    if (code == 401 || code == 403) {
                        Log.e(TAG, "Bad API key ($code)")
                        return null
                    }
                    // Model not available — try next model
                    if (code == 404) break
                    // Rate limit — retry
                    if (code == 429) {
                        Log.w(TAG, "$model rate limited, backing off…")
                        continue
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Exception $model: ${e.message}")
                }
            }
            delay(300) // brief pause before next model
        }
        return null
    }

    // ── HTTP helper ─────────────────────────────────────────────────────────
    private suspend fun doPost(url: String, jsonBody: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                Pair(resp.code, resp.body?.string() ?: "")
            }
        }

    // ── Response parsing ────────────────────────────────────────────────────
    private fun extractText(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val parts = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return ""

            buildString {
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text", "") ?: ""
                    if (t.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(t)
                    }
                }
            }.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            ""
        }
    }

    /** Strip markdown / code-fence artifacts the model may include. */
    private fun cleanSummaryText(raw: String): String {
        return raw
            .replace("```", "")
            .replace(Regex("^\\s*json\\s*", RegexOption.IGNORE_CASE), "")
            .lines()
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}

