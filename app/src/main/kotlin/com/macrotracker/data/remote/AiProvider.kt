package com.macrotracker.data.remote

/**
 * AI backend used for nutrition estimates, label scanning, weather summaries, and widget tips.
 */
enum class AiProvider(val storageValue: String, val displayName: String) {
    GEMINI("gemini", "Gemini"),
    OPENAI("openai", "OpenAI");

    companion object {
        fun fromStorage(value: String?): AiProvider =
            entries.find { it.storageValue.equals(value, ignoreCase = true) } ?: GEMINI
    }
}
