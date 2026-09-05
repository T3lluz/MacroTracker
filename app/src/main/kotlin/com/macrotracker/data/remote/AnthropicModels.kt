package com.macrotracker.data.remote

import java.util.Locale

/**
 * Claude models offered in Settings, sorted most-capable-first (unlike the
 * OpenRouter list, which is cheapest-first — here the default *is* the top one).
 *
 * Prices are Anthropic list rates (USD per 1M tokens) and may drift; they are
 * shown in Settings so the cost of a chat turn is never a surprise.
 */
data class AnthropicModelOption(
    val id: String,
    val displayName: String,
    val inputPerMillionUsd: Double,
    val outputPerMillionUsd: Double,
    val blurb: String,
    val recommended: Boolean = false,
) {
    /** "$5 in / $25 out per 1M" */
    val priceLabel: String
        get() = "$${formatUsd(inputPerMillionUsd)} in / $${formatUsd(outputPerMillionUsd)} out per 1M"

    /**
     * Rough cost of one chat turn (~1.5k input including the system prompt and any
     * server context, ~300 output). Chat turns are far heavier than the one-shot
     * nutrition calls, so this is deliberately not the OpenRouter estimate.
     */
    val approxTurnCostLabel: String
        get() {
            val usd = approxTurnUsd()
            return when {
                usd < 0.001 -> "~$${String.format(Locale.US, "%.4f", usd)} / message"
                else -> "~$${String.format(Locale.US, "%.3f", usd)} / message"
            }
        }

    fun approxTurnUsd(inputTokens: Int = 1500, outputTokens: Int = 300): Double =
        (inputTokens / 1_000_000.0) * inputPerMillionUsd +
            (outputTokens / 1_000_000.0) * outputPerMillionUsd

    private fun formatUsd(value: Double): String =
        if (value >= 1.0) {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        } else {
            String.format(Locale.US, "%.2f", value)
        }
}

object AnthropicModels {
    const val DEFAULT_ID = "claude-opus-5"

    val options: List<AnthropicModelOption> = listOf(
        AnthropicModelOption(
            id = "claude-opus-5",
            displayName = "Claude Opus 5",
            inputPerMillionUsd = 5.00,
            outputPerMillionUsd = 25.00,
            blurb = "Best reasoning — the one to use for server troubleshooting",
            recommended = true,
        ),
        AnthropicModelOption(
            id = "claude-sonnet-5",
            displayName = "Claude Sonnet 5",
            inputPerMillionUsd = 2.00,
            outputPerMillionUsd = 10.00,
            blurb = "Most of the quality at a fraction of the cost",
        ),
        AnthropicModelOption(
            id = "claude-haiku-4-5",
            displayName = "Claude Haiku 4.5",
            inputPerMillionUsd = 1.00,
            outputPerMillionUsd = 5.00,
            blurb = "Fastest and cheapest; fine for macro estimates",
        ),
    )

    fun find(id: String?): AnthropicModelOption =
        options.find { it.id == id } ?: options.first { it.id == DEFAULT_ID }

    fun resolveId(stored: String?): String = find(stored).id

    /**
     * Sampling parameters were removed on Opus 5 and Sonnet 5 — sending
     * `temperature` is a 400, not a warning. Haiku 4.5 still accepts them, so the
     * request builder asks here rather than hardcoding the omission.
     */
    fun acceptsSamplingParams(modelId: String): Boolean =
        modelId.startsWith("claude-haiku")

    /**
     * `output_config.effort` is an Opus/Sonnet-5-era control; older models reject it.
     */
    fun acceptsEffort(modelId: String): Boolean = !modelId.startsWith("claude-haiku")
}
