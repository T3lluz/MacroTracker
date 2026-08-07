package com.macrotracker.data.remote

import android.util.Log
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import okhttp3.OkHttpClient
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
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }

    // In-memory cache: summary + clothing together
    private var cachedResult: WeatherAiResult? = null
    private var cachedTimestamp: Long = 0L
    private var cachedSymbolCode: String? = null

    /** Epoch-ms of when the AI result was last generated (0 if never). */
    val aiLastFetchTimeMs: Long get() = cachedTimestamp

    fun getCachedResult(symbolCode: String): WeatherAiResult? {
        val now = System.currentTimeMillis()
        return if (cachedResult != null &&
            cachedSymbolCode == symbolCode &&
            (now - cachedTimestamp) < CACHE_TTL_MS
        ) cachedResult else null
    }

    private val provider: AiProvider
        get() = settings.getAiProvider()

    private val apiKey: String
        get() {
            val selected = provider
            val stored = settings.getApiKeyForProvider(selected).trim()
            if (stored.isNotBlank()) return stored
            return when (selected) {
                AiProvider.GEMINI -> BuildConfig.GEMINI_API_KEY.trim()
                AiProvider.OPENAI -> BuildConfig.OPENAI_API_KEY.trim()
                AiProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY.trim()
            }
        }

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    // ── Public entry point ──────────────────────────────────────────────────
    /**
     * Produces a [WeatherAiResult] containing:
     *  • An AI-generated weather summary (plain text).
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
            val text = AiApiClient.generate(
                httpClient = httpClient,
                provider = provider,
                apiKey = apiKey,
                params = AiApiClient.GenerateParams(
                    prompt = prompt,
                    temperature = 0.7,
                    maxOutputTokens = 512,
                    jsonMode = false,
                    openRouterModelId = settings.getOpenRouterModelId(),
                ),
            )
            cleanSummaryText(text).takeIf { it.isNotBlank() }
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
