import Constants from 'expo-constants';

const HARDCODED_GEMINI_API_KEY = 'AIzaSyCGWHG77glS_8mqsxofjbPkrxJPvZZGq_M';
const MISSING_KEY_SENTINEL = 'YOUR_GEMINI_API_KEY_HERE';

declare const process:
    | {
        env?: Record<string, string | undefined>;
    }
    | undefined;

const envApiKey =
    (typeof process !== 'undefined' ? process?.env?.EXPO_PUBLIC_GEMINI_API_KEY : undefined) ||
    (typeof globalThis !== 'undefined'
        ? (globalThis as any)?.process?.env?.EXPO_PUBLIC_GEMINI_API_KEY
        : undefined);

const expoExtraApiKey =
    (Constants.expoConfig?.extra as { geminiApiKey?: string } | undefined)?.geminiApiKey ||
    (Constants.manifest as any)?.extra?.geminiApiKey;

export const GEMINI_API_VERSION = 'v1beta';
export const GEMINI_MODELS = ['gemini-flash-latest', 'gemini-2.5-flash', 'gemini-1.5-flash-latest'];
export const GEMINI_SCAN_MODELS = [
    'gemini-flash-lite-latest',
    'gemini-2.0-flash-lite',
    'gemini-2.0-flash-lite-001',
    'gemini-2.0-flash',
    'gemini-flash-latest',
];

export const GEMINI_API_KEY = String(envApiKey || expoExtraApiKey || HARDCODED_GEMINI_API_KEY || '').trim();

export const hasGeminiApiKey =
    GEMINI_API_KEY.length > 0 && GEMINI_API_KEY !== MISSING_KEY_SENTINEL;

export const buildGeminiUrl = (model: string) =>
    `https://generativelanguage.googleapis.com/${GEMINI_API_VERSION}/models/${model}:generateContent?key=${GEMINI_API_KEY}`;

export const geminiApiKeySetupHint =
    'Set EXPO_PUBLIC_GEMINI_API_KEY in your environment or update src/config/ai.ts with a valid key.';
