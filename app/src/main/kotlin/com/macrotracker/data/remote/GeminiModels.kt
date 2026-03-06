package com.macrotracker.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Request models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    @Json(name = "inline_data") val inlineData: InlineData? = null,
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mime_type") val mimeType: String,
    val data: String,
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 1024,
    val responseMimeType: String? = null,
    val responseSchema: ResponseSchema? = null,
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String,
    val properties: Map<String, SchemaProperty>,
    val required: List<String>,
)

@JsonClass(generateAdapter = true)
data class SchemaProperty(
    val type: String,
)

// --- Response models ---

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: CandidateContent? = null,
    val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class CandidateContent(
    val parts: List<CandidatePart>? = null,
)

@JsonClass(generateAdapter = true)
data class CandidatePart(
    val text: String? = null,
)

