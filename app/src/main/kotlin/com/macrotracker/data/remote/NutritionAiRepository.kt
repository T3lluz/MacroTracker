package com.macrotracker.data.remote

import com.macrotracker.BuildConfig
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
    private val api: GeminiApiService,
) {
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private val ESTIMATE_MODELS = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-flash-latest")
        private val SCAN_MODELS = listOf("gemini-2.0-flash-lite", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-flash-latest")

        const val API_KEY_HINT = "Set GEMINI_API_KEY in local.properties and rebuild."

        private val NUTRITION_SCHEMA = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "foodName" to SchemaProperty("STRING"),
                "servingDescription" to SchemaProperty("STRING"),
                "calories" to SchemaProperty("NUMBER"),
                "protein" to SchemaProperty("NUMBER"),
                "confidence" to SchemaProperty("STRING"),
                "notes" to SchemaProperty("STRING"),
            ),
            required = listOf("foodName", "servingDescription", "calories", "protein", "confidence", "notes"),
        )

        private val SCAN_SCHEMA = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "foodName" to SchemaProperty("STRING"),
                "caloriesPerServing" to SchemaProperty("NUMBER"),
                "proteinPerServing" to SchemaProperty("NUMBER"),
                "servingsPerContainer" to SchemaProperty("NUMBER"),
                "servingSizeGrams" to SchemaProperty("NUMBER"),
                "packageWeightGrams" to SchemaProperty("NUMBER"),
            ),
            required = listOf("foodName", "caloriesPerServing", "proteinPerServing", "servingsPerContainer", "servingSizeGrams", "packageWeightGrams"),
        )
    }

    // ─── Estimate nutrition from text ─────────────────────────────────────────
    suspend fun estimateNutritionWithAI(foodQuery: String): NutritionEstimate {
        if (foodQuery.isBlank()) throw Exception("Enter a food to estimate first.")
        if (!hasApiKey) throw Exception("AI API key missing. $API_KEY_HINT")

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

        val parts = listOf(Part(text = prompt))
        val responseText = callGeminiWithFallback(ESTIMATE_MODELS, parts, NUTRITION_SCHEMA)
        return parseNutritionEstimate(responseText, foodQuery)
    }

    // ─── Scan image for nutrition label ───────────────────────────────────────
    suspend fun analyzeImageWithGemini(base64Image: String): ScanResult {
        if (!hasApiKey) throw Exception("AI API key missing. $API_KEY_HINT")

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

        val parts = listOf(
            Part(text = prompt),
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)),
        )

        val responseText = callGeminiWithFallback(SCAN_MODELS, parts, SCAN_SCHEMA)
        return parseScanResult(responseText)
    }

    // ─── Common Gemini call with model fallback ──────────────────────────────
    private suspend fun callGeminiWithFallback(
        models: List<String>,
        parts: List<Part>,
        schema: ResponseSchema,
    ): String {
        var lastErrorText = ""

        for (model in models) {
            for (useStructured in listOf(true, false)) {
                val config = if (useStructured) {
                    GenerationConfig(
                        temperature = 0.2,
                        maxOutputTokens = 1024,
                        responseMimeType = "application/json",
                        responseSchema = schema,
                    )
                } else {
                    GenerationConfig(temperature = 0.2, maxOutputTokens = 1024)
                }

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = parts)),
                    generationConfig = config,
                )

                try {
                    val response = api.generateContent(model, apiKey, request)

                    if (response.isSuccessful) {
                        val body = response.body()
                        val finishReason = body?.candidates?.firstOrNull()?.finishReason ?: ""
                        if (finishReason == "MAX_TOKENS") {
                            throw Exception("AI response was truncated. Try a shorter description or retake photo.")
                        }

                        val text = body?.candidates?.firstOrNull()
                            ?.content?.parts
                            ?.mapNotNull { it.text }
                            ?.joinToString("\n")
                            ?.trim() ?: ""

                        if (text.isNotBlank()) return text
                    }

                    val errorBody = response.errorBody()?.string() ?: ""
                    lastErrorText = errorBody
                    val code = response.code()

                    if (isApiKeyInvalid(errorBody) || code == 401 || code == 403) {
                        throw Exception("Gemini API key is invalid. $API_KEY_HINT")
                    }
                    if (code == 429) {
                        throw Exception("Gemini rate limit hit. Wait a moment and try again.")
                    }
                    if (code == 404) break // model not found, try next
                    if (code == 400 && useStructured) continue // structured output not supported, try unstructured
                } catch (e: Exception) {
                    if (e.message?.contains("API key") == true || e.message?.contains("rate limit") == true || e.message?.contains("truncated") == true) {
                        throw e
                    }
                    lastErrorText = e.message ?: "Unknown error"
                }
            }
        }

        throw Exception("AI request failed: ${lastErrorText.take(200)}")
    }

    private fun isApiKeyInvalid(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("api_key_invalid") || lower.contains("api key invalid") || lower.contains("api key not valid")
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────
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

