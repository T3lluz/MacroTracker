package com.macrotracker.data.remote

import java.util.Locale

/**
 * Curated OpenRouter models for DailyDash workloads (short JSON estimates,
 * nutrition-label vision, weather tips, widget blurbs). Sorted cheapest-first.
 *
 * Prices are OpenRouter list rates (USD per 1M tokens) as of mid-2026 and may drift;
 * shown in Settings so users can pick a cheap-but-capable model.
 */
data class OpenRouterModelOption(
    val id: String,
    val displayName: String,
    val inputPerMillionUsd: Double,
    val outputPerMillionUsd: Double,
    val supportsVision: Boolean,
    val blurb: String,
    val recommended: Boolean = false,
) {
    /** "$0.10 in / $0.40 out per 1M" */
    val priceLabel: String
        get() = "$${formatUsd(inputPerMillionUsd)} in / $${formatUsd(outputPerMillionUsd)} out per 1M"

    /**
     * Rough cost for a typical in-app call (~800 input + ~120 output tokens,
     * including a modest vision surcharge on label scans).
     */
    val approxRequestCostLabel: String
        get() {
            val usd = approxRequestUsd()
            return when {
                usd < 0.00005 -> "< $0.0001 / request"
                usd < 0.001 -> "~$${String.format(Locale.US, "%.4f", usd)} / request"
                else -> "~$${String.format(Locale.US, "%.3f", usd)} / request"
            }
        }

    fun approxRequestUsd(
        inputTokens: Int = 800,
        outputTokens: Int = 120,
    ): Double =
        (inputTokens / 1_000_000.0) * inputPerMillionUsd +
            (outputTokens / 1_000_000.0) * outputPerMillionUsd

    private fun formatUsd(value: Double): String =
        if (value >= 1.0) String.format(Locale.US, "%.2f", value) else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

object OpenRouterModels {
    val DEFAULT_ID = "google/gemini-2.5-flash-lite"

    val options: List<OpenRouterModelOption> = listOf(
        OpenRouterModelOption(
            id = "google/gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            inputPerMillionUsd = 0.10,
            outputPerMillionUsd = 0.40,
            supportsVision = true,
            blurb = "Cheapest solid pick for food estimates & label scans",
            recommended = true,
        ),
        OpenRouterModelOption(
            id = "openai/gpt-4o-mini",
            displayName = "GPT-4o mini",
            inputPerMillionUsd = 0.15,
            outputPerMillionUsd = 0.60,
            supportsVision = true,
            blurb = "Reliable all-rounder; strong vision for packaging labels",
        ),
        OpenRouterModelOption(
            id = "google/gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            inputPerMillionUsd = 0.30,
            outputPerMillionUsd = 2.50,
            supportsVision = true,
            blurb = "Better quality; still cheap for short DailyDash replies",
        ),
        OpenRouterModelOption(
            id = "anthropic/claude-haiku-4.5",
            displayName = "Claude Haiku 4.5",
            inputPerMillionUsd = 1.00,
            outputPerMillionUsd = 5.00,
            supportsVision = true,
            blurb = "Higher quality ceiling; pricier — fine if you prefer Claude",
        ),
    )

    fun find(id: String?): OpenRouterModelOption =
        options.find { it.id == id } ?: options.first { it.id == DEFAULT_ID }

    fun resolveId(stored: String?): String = find(stored).id
}
