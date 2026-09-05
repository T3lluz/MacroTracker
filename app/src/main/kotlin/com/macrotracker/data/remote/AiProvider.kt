package com.macrotracker.data.remote

/**
 * AI backend used for nutrition estimates, label scanning, weather summaries,
 * widget tips, and the two chat bots on the AI tab.
 */
enum class AiProvider(val storageValue: String, val displayName: String) {
    GEMINI("gemini", "Gemini"),
    OPENAI("openai", "OpenAI"),
    OPENROUTER("openrouter", "OpenRouter"),
    ANTHROPIC("anthropic", "Claude");

    companion object {
        fun fromStorage(value: String?): AiProvider =
            entries.find { it.storageValue.equals(value, ignoreCase = true) } ?: GEMINI
    }
}
