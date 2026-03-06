import {
    buildGeminiUrl,
    GEMINI_MODELS,
    hasGeminiApiKey,
    geminiApiKeySetupHint,
    getGeminiApiErrorMessage,
    isGeminiApiKeyInvalidError,
} from '../config/ai';

interface NutritionEstimate {
    foodName: string;
    servingDescription: string;
    calories: number;
    protein: number;
    confidence: 'low' | 'medium' | 'high';
    notes: string;
}

const toRoundedNumber = (value: unknown, fallback = 0): number => {
    const parsedValue = Number(value);
    return Number.isFinite(parsedValue) ? Math.round(parsedValue) : fallback;
};

const normalizeConfidence = (value: unknown): NutritionEstimate['confidence'] => {
    if (typeof value === 'number') {
        if (value >= 0.8) return 'high';
        if (value >= 0.5) return 'medium';
        return 'low';
    }

    if (typeof value !== 'string') return 'medium';
    const normalized = value.trim().toLowerCase();
    if (normalized === 'low' || normalized === 'medium' || normalized === 'high') {
        return normalized;
    }
    return 'medium';
};

const extractJsonObject = (value: string) => {
    const start = value.indexOf('{');
    const end = value.lastIndexOf('}');
    if (start === -1 || end === -1 || end <= start) return value;
    return value.slice(start, end + 1);
};

const createGenerationConfig = (structuredOutput: boolean) => {
    if (!structuredOutput) {
        return {
            temperature: 0.2,
            maxOutputTokens: 1024,
        };
    }

    return {
        temperature: 0.2,
        maxOutputTokens: 1024,
        responseMimeType: 'application/json',
        responseSchema: {
            type: 'OBJECT',
            properties: {
                foodName: { type: 'STRING' },
                servingDescription: { type: 'STRING' },
                calories: { type: 'NUMBER' },
                protein: { type: 'NUMBER' },
                confidence: { type: 'STRING' },
                notes: { type: 'STRING' },
            },
            required: ['foodName', 'servingDescription', 'calories', 'protein', 'confidence', 'notes'],
        },
    };
};

const extractTextFromGeminiResponse = (data: any): string => {
    const parts = data?.candidates?.[0]?.content?.parts;
    if (!Array.isArray(parts)) return '';
    return parts.map((part: any) => String(part?.text ?? '')).join('\n').trim();
};

const promptForFood = (foodQuery: string) => `Estimate nutrition values for this food query: "${foodQuery}".

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
- Keep notes under 120 characters.`;

export const estimateNutritionWithAI = async (foodQuery: string): Promise<NutritionEstimate> => {
    if (!foodQuery.trim()) {
        throw new Error('Enter a food to estimate first.');
    }

    if (!hasGeminiApiKey) {
        throw new Error(`AI API key missing. ${geminiApiKeySetupHint}`);
    }

    const requestParts = [{ text: promptForFood(foodQuery.trim()) }];
    let responseData: any = null;
    let errorText = '';

    for (const model of GEMINI_MODELS) {
        for (const structuredOutput of [true, false]) {
            const response = await fetch(buildGeminiUrl(model), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    contents: [{ parts: requestParts }],
                    generationConfig: createGenerationConfig(structuredOutput),
                }),
            });

            if (response.ok) {
                responseData = await response.json();
                break;
            }

            errorText = await response.text();

            if (isGeminiApiKeyInvalidError(errorText)) {
                throw new Error(getGeminiApiErrorMessage(response.status, errorText, 'AI request failed'));
            }

            if (response.status === 404) {
                break;
            }

            if (response.status === 400 && structuredOutput) {
                continue;
            }

            if (response.status === 401 || response.status === 403) {
                throw new Error(getGeminiApiErrorMessage(response.status, errorText, 'AI request failed'));
            }

            if (response.status === 429) {
                throw new Error(getGeminiApiErrorMessage(response.status, errorText, 'AI request failed'));
            }

            throw new Error(getGeminiApiErrorMessage(response.status, errorText, 'AI request failed'));
        }

        if (responseData) break;
    }

    if (!responseData) {
        throw new Error(getGeminiApiErrorMessage(undefined, errorText, 'AI request failed') || 'No supported model responded.');
    }

    const rawText = extractTextFromGeminiResponse(responseData);
    const cleaned = rawText.replace(/```json|```/g, '').trim();
    const finishReason = String(responseData?.candidates?.[0]?.finishReason || '');

    if (finishReason === 'MAX_TOKENS') {
        throw new Error('AI response was truncated. Try a shorter food description and retry.');
    }

    if (!cleaned) {
        throw new Error('AI returned an empty response.');
    }

    let parsed: Record<string, unknown>;
    try {
        parsed = JSON.parse(extractJsonObject(cleaned));
    } catch {
        throw new Error('Could not parse the AI estimate. Please try again.');
    }

    return {
        foodName: String(parsed.foodName || foodQuery.trim()),
        servingDescription: String(parsed.servingDescription || '1 serving'),
        calories: Math.max(0, toRoundedNumber(parsed.calories, 0)),
        protein: Math.max(0, toRoundedNumber(parsed.protein, 0)),
        confidence: normalizeConfidence(parsed.confidence),
        notes: String(parsed.notes || 'Estimate only. Verify with package label when possible.'),
    };
};

export type { NutritionEstimate };

