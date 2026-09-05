package com.macrotracker.data.remote

import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weather-side AI helpers. Clothing advice is deterministic (no network).
 * Kept for API-key checks shared with the home dashboard.
 */
@Singleton
class WeatherAiRepository @Inject constructor(
    private val settings: SettingsRepository,
) {
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
                AiProvider.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY.trim()
            }
        }

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    fun clothingAdvice(weather: WeatherInfo): ClothingAdvice = ClothingAdvisor.advise(weather)
}
