import React, { useState, useRef, useCallback } from 'react';
import {
    View,
    Text,
    StyleSheet,
    TouchableOpacity,
    ActivityIndicator,
    Alert,
    ScrollView,
    TextInput,
    Platform,
    Image,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { colors } from '../theme/colors';
import AnimatedButton from '../components/AnimatedButton';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSpring,
    withTiming,
    FadeIn,
    FadeOut,
} from 'react-native-reanimated';
import {
    buildGeminiUrl,
    GEMINI_SCAN_MODELS,
    hasGeminiApiKey,
    geminiApiKeySetupHint,
    getGeminiApiErrorMessage,
    isGeminiApiKeyInvalidError,
} from '../config/ai';

// ─── Gemini Vision API ────────────────────────────────────────────────────────
// Uses shared key/model config from src/config/ai.ts

interface ScanResult {
    foodName: string;
    caloriesPerServing: number;
    proteinPerServing: number;
    servingsPerContainer: number;
    servingSizeGrams: number;
    packageWeightGrams: number;
    totalCalories: number;
    totalProtein: number;
}

interface FollowUpPrompt {
    id: 'foodName' | 'calories' | 'protein' | 'servings' | 'packageWeight' | 'servingSize';
    label: string;
    helperText: string;
    required?: boolean;
}

const toRoundedNumber = (value: unknown, fallback = 0): number => {
    const num = Number(value);
    return Number.isFinite(num) ? Math.round(num) : fallback;
};

const toFiniteNumber = (value: unknown, fallback = 0): number => {
    const num = Number(value);
    return Number.isFinite(num) ? num : fallback;
};

const roundTo = (value: number, decimals = 2): number => {
    const factor = 10 ** decimals;
    return Math.round(value * factor) / factor;
};

const formatInputNumber = (value: number): string => {
    if (!Number.isFinite(value) || value <= 0) return '';
    const rounded = roundTo(value, 2);
    return String(rounded);
};

const tryExtractNumber = (source: string, keys: string[]): number | undefined => {
    for (const key of keys) {
        const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const withQuotedKey = new RegExp(`\\"${escaped}\\"\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)`, 'i');
        const withPlainKey = new RegExp(`${escaped}\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)`, 'i');
        const compactKey = key.replace(/\s+/g, '');
        const withSpacesBetweenLetters = compactKey.split('').join('\\s*');
        const fuzzyKey = new RegExp(`${withSpacesBetweenLetters}\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)`, 'i');

        const match = source.match(withQuotedKey) || source.match(withPlainKey) || source.match(fuzzyKey);
        if (match?.[1]) {
            const value = Number(match[1]);
            if (Number.isFinite(value)) return value;
        }
    }
    return undefined;
};

const parseScanResultFallback = (source: string): ScanResult | null => {
    const foodNameMatch =
        source.match(/"foodName"\s*:\s*"([^"]+)"/i) ||
        source.match(/food\s*name\s*[:=]\s*"?([^\n,\r}]+)"?/i) ||
        source.match(/product\s*name\s*[:=]\s*"?([^\n,\r}]+)"?/i);

    const caloriesPerServing = tryExtractNumber(source, [
        'caloriesPerServing',
        'calories per serving',
        'calories_per_serving',
    ]);
    const proteinPerServing = tryExtractNumber(source, [
        'proteinPerServing',
        'protein per serving',
        'protein_per_serving',
    ]);
    const servingsPerContainer = tryExtractNumber(source, [
        'servingsPerContainer',
        'servings per container',
        'servings_per_container',
        'servings per bag',
    ]);
    const servingSizeGrams = tryExtractNumber(source, [
        'servingSizeGrams',
        'serving size grams',
        'serving_size_grams',
        'serving size g',
    ]);
    const packageWeightGrams = tryExtractNumber(source, [
        'packageWeightGrams',
        'package weight grams',
        'package_weight_grams',
        'net weight g',
        'net wt g',
    ]);
    const totalCalories = tryExtractNumber(source, ['totalCalories', 'total calories']);
    const totalProtein = tryExtractNumber(source, ['totalProtein', 'total protein']);

    if (
        caloriesPerServing === undefined &&
        proteinPerServing === undefined &&
        servingsPerContainer === undefined &&
        servingSizeGrams === undefined &&
        packageWeightGrams === undefined &&
        totalCalories === undefined &&
        totalProtein === undefined
    ) {
        return null;
    }

    const safeServingSize = Math.max(0, toRoundedNumber(servingSizeGrams, 0));
    const safePackageWeight = Math.max(0, toRoundedNumber(packageWeightGrams, 0));
    let safeServings = Math.max(0, toFiniteNumber(servingsPerContainer, 0));
    if (safeServings <= 0 && safeServingSize > 0 && safePackageWeight > 0) {
        safeServings = roundTo(safePackageWeight / safeServingSize, 2);
    }
    const safeCaloriesPerServing = toRoundedNumber(caloriesPerServing, 0);
    const safeProteinPerServing = toRoundedNumber(proteinPerServing, 0);

    return {
        foodName: (foodNameMatch?.[1] || 'Scanned Food').trim(),
        caloriesPerServing: safeCaloriesPerServing,
        proteinPerServing: safeProteinPerServing,
        servingsPerContainer: safeServings,
        servingSizeGrams: safeServingSize,
        packageWeightGrams: safePackageWeight,
        totalCalories: toRoundedNumber(totalCalories, safeCaloriesPerServing * safeServings),
        totalProtein: toRoundedNumber(totalProtein, safeProteinPerServing * safeServings),
    };
};

const maybeComputeTotals = (partial: Partial<ScanResult>): ScanResult => {
    const caloriesPerServing = Math.max(0, toRoundedNumber(partial.caloriesPerServing, 0));
    const proteinPerServing = Math.max(0, toRoundedNumber(partial.proteinPerServing, 0));
    const servingSizeGrams = Math.max(0, toRoundedNumber(partial.servingSizeGrams, 0));
    const packageWeightGrams = Math.max(0, toRoundedNumber(partial.packageWeightGrams, 0));

    let servingsPerContainer = Math.max(0, toFiniteNumber(partial.servingsPerContainer, 0));
    if (servingsPerContainer <= 0 && servingSizeGrams > 0 && packageWeightGrams > 0) {
        servingsPerContainer = roundTo(packageWeightGrams / servingSizeGrams, 2);
    }

    return {
        foodName: (partial.foodName || 'Scanned Food').trim(),
        caloriesPerServing,
        proteinPerServing,
        servingsPerContainer,
        servingSizeGrams,
        packageWeightGrams,
        totalCalories: caloriesPerServing * servingsPerContainer,
        totalProtein: proteinPerServing * servingsPerContainer,
    };
};

const fetchWithTimeout = async (url: string, options: RequestInit, timeoutMs: number) => {
    const timeoutController = new AbortController();
    const timeoutId = setTimeout(() => timeoutController.abort(), timeoutMs);

    try {
        return await fetch(url, {
            ...options,
            signal: timeoutController.signal,
        });
    } finally {
        clearTimeout(timeoutId);
    }
};

const createGenerationConfig = (structuredOutput: boolean) => {
    if (!structuredOutput) {
        return {
            temperature: 0.1,
            maxOutputTokens: 220,
        };
    }

    return {
        temperature: 0.1,
        maxOutputTokens: 220,
        responseMimeType: 'application/json',
        responseSchema: {
            type: 'OBJECT',
            properties: {
                foodName: { type: 'STRING' },
                caloriesPerServing: { type: 'NUMBER' },
                proteinPerServing: { type: 'NUMBER' },
                servingsPerContainer: { type: 'NUMBER' },
                servingSizeGrams: { type: 'NUMBER' },
                packageWeightGrams: { type: 'NUMBER' },
            },
            required: [
                'foodName',
                'caloriesPerServing',
                'proteinPerServing',
                'servingsPerContainer',
                'servingSizeGrams',
                'packageWeightGrams',
            ],
        },
    };
};

const PROMPT = `Read the nutrition facts label in this image.
Return ONLY JSON with these keys:
{
  "foodName": string,
  "caloriesPerServing": number,
  "proteinPerServing": number,
    "servingsPerContainer": number,
    "servingSizeGrams": number,
    "packageWeightGrams": number
}
Use 0 for missing numbers. No markdown. No explanation.`;

async function analyzeImageWithGemini(base64Image: string): Promise<ScanResult> {
    const parts = [
        { text: PROMPT },
        {
            inline_data: {
                mime_type: 'image/jpeg',
                data: base64Image,
            },
        },
    ];

    let responseData: any = null;
    let errText = '';

    for (const model of GEMINI_SCAN_MODELS) {
        for (const structuredOutput of [true, false]) {
            let response: Response;

            try {
                response = await fetchWithTimeout(
                    buildGeminiUrl(model),
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            contents: [{ parts }],
                            generationConfig: createGenerationConfig(structuredOutput),
                        }),
                    },
                    16000,
                );
            } catch (error: any) {
                if (error?.name === 'AbortError') {
                    errText = 'timeout';
                    break;
                }
                throw error;
            }

            if (response.ok) {
                responseData = await response.json();
                break;
            }

            errText = await response.text();

            if (isGeminiApiKeyInvalidError(errText)) {
                throw new Error(getGeminiApiErrorMessage(response.status, errText, 'Gemini API error'));
            }

            if (response.status === 404) {
                break;
            }

            if (response.status === 400 && structuredOutput) {
                continue;
            }

            if (response.status === 400) {
                const loweredError = errText.toLowerCase();
                if (/not supported|unsupported|not found|invalid argument/.test(loweredError)) {
                    break;
                }
            }

            if (response.status === 401 || response.status === 403) {
                throw new Error(getGeminiApiErrorMessage(response.status, errText, 'Gemini API error'));
            }

            if (response.status === 429) {
                throw new Error(getGeminiApiErrorMessage(response.status, errText, 'Gemini API error'));
            }

            throw new Error(getGeminiApiErrorMessage(response.status, errText, 'Gemini API error'));
        }

        if (responseData) break;
    }

    if (!responseData) {
        throw new Error(getGeminiApiErrorMessage(undefined, errText, 'Gemini API error'));
    }

    const text: string = responseData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';
    const cleaned = text.replace(/```json|```/g, '').trim();
    const finishReason = String(responseData?.candidates?.[0]?.finishReason || '');

    if (finishReason === 'MAX_TOKENS') {
        throw new Error('AI response was truncated. Retake with clearer label or retry.');
    }

    if (!cleaned) {
        throw new Error('Gemini returned an empty response. Please retake the photo and try again.');
    }

    const jsonStart = cleaned.indexOf('{');
    const jsonEnd = cleaned.lastIndexOf('}');
    const jsonPayload =
        jsonStart !== -1 && jsonEnd !== -1 && jsonEnd > jsonStart
            ? cleaned.slice(jsonStart, jsonEnd + 1)
            : cleaned;

    let parsed: ScanResult;
    try {
        parsed = maybeComputeTotals(JSON.parse(jsonPayload) as Partial<ScanResult>);
    } catch {
        const fallbackParsed = parseScanResultFallback(cleaned);
        if (!fallbackParsed) {
            throw new Error('Could not parse AI response. Please retake the photo and try again.');
        }
        parsed = maybeComputeTotals(fallbackParsed);
    }

    return maybeComputeTotals(parsed);
}

// ─── Component ────────────────────────────────────────────────────────────────
const CameraScanScreen = ({ navigation, route }: any) => {
    const [permission, requestPermission] = useCameraPermissions();
    const [phase, setPhase] = useState<'camera' | 'preview' | 'result'>('camera');
    const [capturedUri, setCapturedUri] = useState<string | null>(null);
    const [capturedBase64, setCapturedBase64] = useState<string | null>(null);
    const [scanning, setScanning] = useState(false);
    const [result, setResult] = useState<ScanResult | null>(null);
    const [foodNameOverride, setFoodNameOverride] = useState('');
    const [caloriesOverride, setCaloriesOverride] = useState('');
    const [proteinOverride, setProteinOverride] = useState('');
    const [servingsOverride, setServingsOverride] = useState('');
    const [servingSizeOverride, setServingSizeOverride] = useState('');
    const [packageWeightOverride, setPackageWeightOverride] = useState('');
    const cameraRef = useRef<CameraView>(null);

    const flashAnim = useSharedValue(0);
    const flashStyle = useAnimatedStyle(() => ({
        opacity: flashAnim.value,
    }));

    // ── Camera shutter ────────────────────────────────────────────────────────
    const takePicture = useCallback(async () => {
        if (!cameraRef.current) return;
        try {
            // Flash effect
            flashAnim.value = withTiming(1, { duration: 80 }, () => {
                flashAnim.value = withTiming(0, { duration: 200 });
            });

            const photo = await cameraRef.current.takePictureAsync({
                quality: 0.45,
                base64: true,
                exif: false,
                skipProcessing: true,
            });

            if (photo) {
                setCapturedUri(photo.uri);
                setCapturedBase64(photo.base64 ?? null);
                setPhase('preview');
            }
        } catch (e) {
            Alert.alert('Camera Error', 'Failed to take picture. Please try again.');
        }
    }, []);

    // ── Gallery picker ────────────────────────────────────────────────────────
    const pickFromGallery = useCallback(async () => {
        const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (status !== 'granted') {
            Alert.alert('Permission needed', 'Allow access to your photo library to pick an image.');
            return;
        }
        const picked = await ImagePicker.launchImageLibraryAsync({
            mediaTypes: ['images'],
            quality: 0.45,
            base64: true,
        });
        if (!picked.canceled && picked.assets[0]) {
            setCapturedUri(picked.assets[0].uri);
            setCapturedBase64(picked.assets[0].base64 ?? null);
            setPhase('preview');
        }
    }, []);

    // ── Analyse image ─────────────────────────────────────────────────────────
    const analyseImage = useCallback(async () => {
        if (!capturedBase64) return;

        if (!hasGeminiApiKey) {
            Alert.alert(
                '⚠️ API Key Required',
                `${geminiApiKeySetupHint}\n\nGet a free key at:\nhttps://aistudio.google.com/app/apikey`,
            );
            return;
        }

        setScanning(true);
        try {
            const scanResult = await analyzeImageWithGemini(capturedBase64);
            setResult(scanResult);
            setFoodNameOverride(scanResult.foodName === 'Scanned Food' ? '' : scanResult.foodName);
            setCaloriesOverride(formatInputNumber(scanResult.caloriesPerServing));
            setProteinOverride(formatInputNumber(scanResult.proteinPerServing));
            setServingsOverride(formatInputNumber(scanResult.servingsPerContainer));
            setServingSizeOverride(formatInputNumber(scanResult.servingSizeGrams));
            setPackageWeightOverride(formatInputNumber(scanResult.packageWeightGrams));
            setPhase('result');
        } catch (e: any) {
            Alert.alert('Scan Failed', `Could not read the nutrition label.\n\n${e.message ?? e}`);
        } finally {
            setScanning(false);
        }
    }, [capturedBase64]);

    // ── Servings adjustment recalculates totals ───────────────────────────────
    const getAdjustedResult = (): ScanResult | null => {
        if (!result) return null;

        const parseNonNegative = (value: string) => {
            const num = Number(value);
            return Number.isFinite(num) && num >= 0 ? num : null;
        };

        const parsePositive = (value: string) => {
            const num = Number(value);
            return Number.isFinite(num) && num > 0 ? num : null;
        };

        const foodName = foodNameOverride.trim() || result.foodName;
        const caloriesPerServing = parsePositive(caloriesOverride) ?? Math.max(0, result.caloriesPerServing);
        const proteinPerServing = parseNonNegative(proteinOverride) ?? Math.max(0, result.proteinPerServing);

        let servingsPerContainer = parsePositive(servingsOverride) ?? Math.max(0, result.servingsPerContainer);
        let servingSizeGrams = parsePositive(servingSizeOverride) ?? Math.max(0, result.servingSizeGrams);
        let packageWeightGrams = parsePositive(packageWeightOverride) ?? Math.max(0, result.packageWeightGrams);

        if (servingsPerContainer <= 0 && servingSizeGrams > 0 && packageWeightGrams > 0) {
            servingsPerContainer = roundTo(packageWeightGrams / servingSizeGrams, 2);
        }
        if (servingSizeGrams <= 0 && servingsPerContainer > 0 && packageWeightGrams > 0) {
            servingSizeGrams = roundTo(packageWeightGrams / servingsPerContainer, 2);
        }
        if (packageWeightGrams <= 0 && servingsPerContainer > 0 && servingSizeGrams > 0) {
            packageWeightGrams = roundTo(servingsPerContainer * servingSizeGrams, 2);
        }

        return maybeComputeTotals({
            ...result,
            foodName,
            caloriesPerServing,
            proteinPerServing,
            servingsPerContainer,
            servingSizeGrams,
            packageWeightGrams,
        });
    };

    const adj = getAdjustedResult();
    const followUpPrompts: FollowUpPrompt[] = [];

    if (adj) {
        const hasNamedFood = adj.foodName.trim().length > 0 && adj.foodName.trim().toLowerCase() !== 'scanned food';
        const hasPackageWeight = adj.packageWeightGrams > 0;
        const hasServingSize = adj.servingSizeGrams > 0;

        if (!hasNamedFood) {
            followUpPrompts.push({
                id: 'foodName',
                label: 'Product Name',
                helperText: 'AI could not read the product name clearly.',
                required: true,
            });
        }
        if (adj.caloriesPerServing <= 0) {
            followUpPrompts.push({
                id: 'calories',
                label: 'Calories Per Serving',
                helperText: 'Needed to calculate total calories correctly.',
                required: true,
            });
        }
        if (adj.proteinPerServing <= 0) {
            followUpPrompts.push({
                id: 'protein',
                label: 'Protein Per Serving (g)',
                helperText: 'Optional, but helpful for accurate macro totals.',
            });
        }
        if (!hasPackageWeight && adj.caloriesPerServing > 0) {
            followUpPrompts.push({
                id: 'packageWeight',
                label: 'Total Product Weight (g)',
                helperText: 'Optional but recommended for better serving calculations.',
            });
        }
        if (hasPackageWeight && !hasServingSize) {
            followUpPrompts.push({
                id: 'servingSize',
                label: 'Serving Size (g)',
                helperText: 'Add this to auto-calculate servings from package weight.',
            });
        }
        if (adj.servingsPerContainer <= 0 && !(hasPackageWeight && hasServingSize)) {
            followUpPrompts.push({
                id: 'servings',
                label: 'Servings In Package',
                helperText: 'Enter servings, or provide both package weight and serving size.',
                required: true,
            });
        }
    }

    const followUpInputProps: Record<FollowUpPrompt['id'], {
        value: string;
        onChangeText: (value: string) => void;
        placeholder: string;
        keyboardType?: 'default' | 'numeric' | 'decimal-pad';
    }> = {
        foodName: {
            value: foodNameOverride,
            onChangeText: setFoodNameOverride,
            placeholder: 'Enter product name',
            keyboardType: 'default',
        },
        calories: {
            value: caloriesOverride,
            onChangeText: setCaloriesOverride,
            placeholder: 'e.g. 180',
            keyboardType: 'decimal-pad',
        },
        protein: {
            value: proteinOverride,
            onChangeText: setProteinOverride,
            placeholder: 'e.g. 12',
            keyboardType: 'decimal-pad',
        },
        servings: {
            value: servingsOverride,
            onChangeText: setServingsOverride,
            placeholder: 'e.g. 4',
            keyboardType: 'decimal-pad',
        },
        packageWeight: {
            value: packageWeightOverride,
            onChangeText: setPackageWeightOverride,
            placeholder: 'e.g. 340',
            keyboardType: 'decimal-pad',
        },
        servingSize: {
            value: servingSizeOverride,
            onChangeText: setServingSizeOverride,
            placeholder: 'e.g. 85',
            keyboardType: 'decimal-pad',
        },
    };

    // ── Log the food ──────────────────────────────────────────────────────────
    const handleLog = useCallback(() => {
        const adj = getAdjustedResult();
        if (!adj) return;

        const missingRequired: string[] = [];
        if (!adj.foodName.trim() || adj.foodName.trim().toLowerCase() === 'scanned food') {
            missingRequired.push('product name');
        }
        if (adj.caloriesPerServing <= 0) {
            missingRequired.push('calories per serving');
        }
        if (adj.servingsPerContainer <= 0) {
            missingRequired.push('servings in package');
        }

        if (missingRequired.length > 0) {
            Alert.alert(
                'Need More Label Details',
                `Please fill: ${missingRequired.join(', ')}.`,
            );
            return;
        }

        // Navigate back passing data to HomeScreen
        navigation.navigate('Home', {
            scannedFood: {
                foodName: adj.foodName,
                calories: adj.totalCalories,
                protein: adj.totalProtein,
            },
        });
    }, [
        result,
        foodNameOverride,
        caloriesOverride,
        proteinOverride,
        servingsOverride,
        servingSizeOverride,
        packageWeightOverride,
        navigation,
    ]);

    // ── Permission gate ───────────────────────────────────────────────────────
    if (!permission) {
        return <View style={styles.container} />;
    }

    if (!permission.granted) {
        return (
            <View style={styles.container}>
                <View style={styles.permissionBox}>
                    <Text style={styles.permissionIcon}>📷</Text>
                    <Text style={styles.permissionTitle}>Camera Access Needed</Text>
                    <Text style={styles.permissionText}>
                        MacroTracker needs camera access to scan nutrition labels on food packaging.
                    </Text>
                    <AnimatedButton title="Grant Camera Access" onPress={requestPermission} style={{ marginTop: 16 }} />
                    <AnimatedButton
                        title="Pick from Gallery Instead"
                        onPress={pickFromGallery}
                        variant="secondary"
                    />
                    <AnimatedButton
                        title="Go Back"
                        onPress={() => navigation.goBack()}
                        variant="secondary"
                    />
                </View>
            </View>
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE: camera
    // ─────────────────────────────────────────────────────────────────────────
    if (phase === 'camera') {
        return (
            <View style={styles.container}>
                <CameraView ref={cameraRef} style={styles.camera} facing="back" />

                {/* Overlay - now sibling to CameraView, positioned absolutely */}
                <View style={[StyleSheet.absoluteFillObject, styles.cameraOverlay]}>
                    <View style={styles.cameraTopBar}>
                        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
                            <Text style={styles.backBtnText}>✕</Text>
                        </TouchableOpacity>
                        <Text style={styles.cameraTitle}>Scan Nutrition Label</Text>
                        <View style={{ width: 40 }} />
                    </View>

                    {/* Viewfinder frame */}
                    <View style={styles.viewfinderWrapper}>
                        <View style={styles.viewfinder}>
                            {/* Corner marks */}
                            <View style={[styles.corner, styles.cornerTL]} />
                            <View style={[styles.corner, styles.cornerTR]} />
                            <View style={[styles.corner, styles.cornerBL]} />
                            <View style={[styles.corner, styles.cornerBR]} />
                        </View>
                        <Text style={styles.viewfinderHint}>
                            Point at the nutrition facts label on the back of the product
                        </Text>
                    </View>

                    <View style={styles.cameraControls}>
                        <TouchableOpacity onPress={pickFromGallery} style={styles.galleryBtn}>
                            <Text style={styles.galleryBtnText}>🖼</Text>
                            <Text style={styles.galleryBtnLabel}>Gallery</Text>
                        </TouchableOpacity>

                        <TouchableOpacity onPress={takePicture} style={styles.shutterBtn} activeOpacity={0.8}>
                            <View style={styles.shutterInner} />
                        </TouchableOpacity>

                        <View style={{ width: 60 }} />
                    </View>
                </View>

                {/* Flash overlay */}
                <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFillObject, styles.flashOverlay, flashStyle]} />
            </View>
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE: preview
    // ─────────────────────────────────────────────────────────────────────────
    if (phase === 'preview') {
        return (
            <View style={styles.container}>
                <Image source={{ uri: capturedUri! }} style={styles.previewImage} resizeMode="contain" />

                <View style={styles.previewOverlay}>
                    <Text style={styles.previewTitle}>Looking good?</Text>
                    <Text style={styles.previewSubtitle}>
                        Make sure the nutrition label is clear and fully visible.
                    </Text>

                    {scanning ? (
                        <View style={styles.scanningBox}>
                            <ActivityIndicator size="large" color={colors.primary} />
                            <Text style={styles.scanningText}>Analysing nutrition label…</Text>
                            <Text style={styles.scanningSubText}>AI is reading the values for you ✨</Text>
                        </View>
                    ) : (
                        <View style={styles.previewButtons}>
                            <AnimatedButton
                                title="🔍  Scan This Photo"
                                onPress={analyseImage}
                                style={{ marginBottom: 8 }}
                            />
                            <AnimatedButton
                                title="Retake"
                                onPress={() => {
                                    setCapturedUri(null);
                                    setCapturedBase64(null);
                                    setPhase('camera');
                                }}
                                variant="secondary"
                            />
                        </View>
                    )}
                </View>
            </View>
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE: result
    // ─────────────────────────────────────────────────────────────────────────
    return (
        <View style={styles.container}>
            <ScrollView contentContainerStyle={styles.resultContent}>
                {/* Thumbnail */}
                <Image source={{ uri: capturedUri! }} style={styles.thumbnail} resizeMode="cover" />

                <Animated.View entering={FadeIn.duration(400)} style={styles.resultCard}>
                    <Text style={styles.resultTitle}>📊 Nutrition Scan Results</Text>
                    <Text style={styles.resultFoodName}>{adj!.foodName}</Text>

                    {/* Per serving */}
                    <View style={styles.resultSection}>
                        <Text style={styles.resultSectionLabel}>Per Serving</Text>
                        <View style={styles.macroRow}>
                            <View style={[styles.macroPill, { backgroundColor: colors.primaryVariant }]}>
                                <Text style={styles.macroPillValue}>{adj!.caloriesPerServing}</Text>
                                <Text style={styles.macroPillLabel}>kcal</Text>
                            </View>
                            <View style={[styles.macroPill, { backgroundColor: '#1a5e5a' }]}>
                                <Text style={styles.macroPillValue}>{adj!.proteinPerServing}g</Text>
                                <Text style={styles.macroPillLabel}>protein</Text>
                            </View>
                        </View>
                    </View>

                    {(adj!.packageWeightGrams > 0 || adj!.servingSizeGrams > 0) && (
                        <View style={styles.packageMetaRow}>
                            {adj!.packageWeightGrams > 0 && (
                                <Text style={styles.packageMetaText}>Package: {adj!.packageWeightGrams}g</Text>
                            )}
                            {adj!.servingSizeGrams > 0 && (
                                <Text style={styles.packageMetaText}>Serving: {adj!.servingSizeGrams}g</Text>
                            )}
                        </View>
                    )}

                    {/* Servings override */}
                    <View style={styles.resultSection}>
                        <Text style={styles.resultSectionLabel}>Servings in Whole Package</Text>
                        <View style={styles.servingsRow}>
                            <TouchableOpacity
                                style={styles.servingsBtn}
                                onPress={() => {
                                    const v = parseFloat(servingsOverride) || 1;
                                    setServingsOverride(String(Math.max(0.5, v - 0.5)));
                                }}
                            >
                                <Text style={styles.servingsBtnText}>−</Text>
                            </TouchableOpacity>
                            <TextInput
                                style={styles.servingsInput}
                                value={servingsOverride}
                                onChangeText={setServingsOverride}
                                keyboardType="numeric"
                                selectTextOnFocus
                            />
                            <TouchableOpacity
                                style={styles.servingsBtn}
                                onPress={() => {
                                    const v = parseFloat(servingsOverride) || 1;
                                    setServingsOverride(String(v + 0.5));
                                }}
                            >
                                <Text style={styles.servingsBtnText}>+</Text>
                            </TouchableOpacity>
                        </View>
                    </View>

                    {followUpPrompts.length > 0 && (
                        <View style={styles.followUpCard}>
                            <Text style={styles.followUpTitle}>Need A Few More Details</Text>
                            {followUpPrompts.map((prompt) => {
                                const inputProps = followUpInputProps[prompt.id];
                                return (
                                    <View key={prompt.id} style={styles.followUpField}>
                                        <Text style={styles.followUpLabel}>
                                            {prompt.label}
                                            {prompt.required ? ' *' : ''}
                                        </Text>
                                        <TextInput
                                            style={styles.followUpInput}
                                            value={inputProps.value}
                                            onChangeText={inputProps.onChangeText}
                                            keyboardType={inputProps.keyboardType}
                                            placeholder={inputProps.placeholder}
                                            placeholderTextColor={colors.textSecondary}
                                        />
                                        <Text style={styles.followUpHint}>{prompt.helperText}</Text>
                                    </View>
                                );
                            })}
                        </View>
                    )}

                    {/* WHOLE BAG TOTAL */}
                    <View style={styles.totalBox}>
                        <Text style={styles.totalLabel}>🛍 Whole Package Totals</Text>
                        <View style={styles.totalRow}>
                            <View style={styles.totalItem}>
                                <Text style={styles.totalValue}>{adj!.totalCalories}</Text>
                                <Text style={styles.totalUnit}>kcal</Text>
                            </View>
                            <View style={styles.totalDivider} />
                            <View style={styles.totalItem}>
                                <Text style={[styles.totalValue, { color: colors.secondary }]}>
                                    {adj!.totalProtein}g
                                </Text>
                                <Text style={styles.totalUnit}>protein</Text>
                            </View>
                        </View>
                        <Text style={styles.totalFormula}>
                            ({adj!.caloriesPerServing} kcal × {adj!.servingsPerContainer} servings)
                        </Text>
                    </View>
                </Animated.View>

                <AnimatedButton
                    title="➕  Log to Today"
                    onPress={handleLog}
                    style={styles.logBtn}
                />
                <AnimatedButton
                    title="Scan Again"
                    onPress={() => {
                        setCapturedUri(null);
                        setCapturedBase64(null);
                        setResult(null);
                        setFoodNameOverride('');
                        setCaloriesOverride('');
                        setProteinOverride('');
                        setServingsOverride('');
                        setServingSizeOverride('');
                        setPackageWeightOverride('');
                        setPhase('camera');
                    }}
                    variant="secondary"
                />
                <AnimatedButton
                    title="Cancel"
                    onPress={() => navigation.goBack()}
                    variant="secondary"
                />
            </ScrollView>
        </View>
    );
};

// ─── Styles ───────────────────────────────────────────────────────────────────
const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: colors.background,
    },

    // ── Permission ──
    permissionBox: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        padding: 32,
    },
    permissionIcon: { fontSize: 64, marginBottom: 16 },
    permissionTitle: {
        fontSize: 24,
        fontWeight: 'bold',
        color: colors.text,
        marginBottom: 12,
        textAlign: 'center',
    },
    permissionText: {
        fontSize: 15,
        color: colors.textSecondary,
        textAlign: 'center',
        lineHeight: 22,
    },

    // ── Camera ──
    camera: { flex: 1 },
    cameraOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.35)',
        justifyContent: 'space-between',
    },
    cameraTopBar: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingTop: Platform.OS === 'ios' ? 60 : 40,
        paddingHorizontal: 20,
        paddingBottom: 16,
    },
    backBtn: {
        width: 40,
        height: 40,
        borderRadius: 20,
        backgroundColor: 'rgba(0,0,0,0.5)',
        alignItems: 'center',
        justifyContent: 'center',
    },
    backBtnText: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
    cameraTitle: {
        color: '#fff',
        fontSize: 17,
        fontWeight: '600',
    },

    // Viewfinder
    viewfinderWrapper: {
        alignItems: 'center',
        paddingHorizontal: 24,
    },
    viewfinder: {
        width: 300,
        height: 200,
        borderRadius: 12,
        position: 'relative',
    },
    corner: {
        position: 'absolute',
        width: 28,
        height: 28,
        borderColor: colors.primary,
        borderWidth: 3,
    },
    cornerTL: { top: 0, left: 0, borderRightWidth: 0, borderBottomWidth: 0, borderTopLeftRadius: 8 },
    cornerTR: { top: 0, right: 0, borderLeftWidth: 0, borderBottomWidth: 0, borderTopRightRadius: 8 },
    cornerBL: { bottom: 0, left: 0, borderRightWidth: 0, borderTopWidth: 0, borderBottomLeftRadius: 8 },
    cornerBR: { bottom: 0, right: 0, borderLeftWidth: 0, borderTopWidth: 0, borderBottomRightRadius: 8 },
    viewfinderHint: {
        color: 'rgba(255,255,255,0.8)',
        fontSize: 13,
        textAlign: 'center',
        marginTop: 16,
        lineHeight: 20,
    },

    // Controls
    cameraControls: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 40,
        paddingBottom: Platform.OS === 'ios' ? 48 : 32,
    },
    galleryBtn: { alignItems: 'center' },
    galleryBtnText: { fontSize: 32 },
    galleryBtnLabel: { color: '#fff', fontSize: 12, marginTop: 4 },
    shutterBtn: {
        width: 76,
        height: 76,
        borderRadius: 38,
        backgroundColor: 'rgba(255,255,255,0.25)',
        borderWidth: 4,
        borderColor: '#fff',
        alignItems: 'center',
        justifyContent: 'center',
    },
    shutterInner: {
        width: 56,
        height: 56,
        borderRadius: 28,
        backgroundColor: '#fff',
    },
    flashOverlay: {
        backgroundColor: '#fff',
    },

    // ── Preview ──
    previewImage: {
        flex: 1,
        backgroundColor: '#000',
    },
    previewOverlay: {
        backgroundColor: colors.surface,
        padding: 24,
        borderTopLeftRadius: 24,
        borderTopRightRadius: 24,
    },
    previewTitle: {
        fontSize: 22,
        fontWeight: 'bold',
        color: colors.text,
        marginBottom: 6,
    },
    previewSubtitle: {
        fontSize: 14,
        color: colors.textSecondary,
        marginBottom: 20,
    },
    scanningBox: {
        alignItems: 'center',
        paddingVertical: 24,
    },
    scanningText: {
        color: colors.text,
        fontSize: 17,
        fontWeight: '600',
        marginTop: 16,
    },
    scanningSubText: {
        color: colors.textSecondary,
        fontSize: 13,
        marginTop: 6,
    },
    previewButtons: {},

    // ── Results ──
    resultContent: {
        paddingBottom: 40,
    },
    thumbnail: {
        width: '100%',
        height: 200,
        backgroundColor: '#000',
    },
    resultCard: {
        backgroundColor: colors.surface,
        borderRadius: 20,
        margin: 16,
        padding: 20,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.4,
        shadowRadius: 8,
        elevation: 10,
    },
    resultTitle: {
        fontSize: 18,
        fontWeight: '700',
        color: colors.textSecondary,
        marginBottom: 4,
    },
    resultFoodName: {
        fontSize: 22,
        fontWeight: 'bold',
        color: colors.primary,
        marginBottom: 20,
    },
    resultSection: {
        marginBottom: 20,
    },
    resultSectionLabel: {
        fontSize: 13,
        color: colors.textSecondary,
        fontWeight: '600',
        textTransform: 'uppercase',
        letterSpacing: 0.8,
        marginBottom: 10,
    },
    macroRow: {
        flexDirection: 'row',
        gap: 12,
    },
    macroPill: {
        flex: 1,
        borderRadius: 12,
        padding: 14,
        alignItems: 'center',
    },
    macroPillValue: {
        fontSize: 24,
        fontWeight: 'bold',
        color: '#fff',
    },
    macroPillLabel: {
        fontSize: 12,
        color: 'rgba(255,255,255,0.7)',
        marginTop: 2,
    },
    servingsRow: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: colors.background,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: colors.border,
        overflow: 'hidden',
    },
    servingsBtn: {
        width: 52,
        alignItems: 'center',
        justifyContent: 'center',
        paddingVertical: 14,
    },
    servingsBtnText: {
        fontSize: 24,
        color: colors.primary,
        fontWeight: '300',
    },
    servingsInput: {
        flex: 1,
        textAlign: 'center',
        fontSize: 22,
        fontWeight: 'bold',
        color: colors.text,
        paddingVertical: 10,
    },
    packageMetaRow: {
        flexDirection: 'row',
        gap: 10,
        marginBottom: 16,
        flexWrap: 'wrap',
    },
    packageMetaText: {
        color: colors.textSecondary,
        fontSize: 13,
        backgroundColor: colors.background,
        borderColor: colors.border,
        borderWidth: 1,
        borderRadius: 999,
        paddingHorizontal: 10,
        paddingVertical: 6,
    },
    followUpCard: {
        backgroundColor: colors.background,
        borderColor: colors.border,
        borderWidth: 1,
        borderRadius: 12,
        padding: 14,
        marginBottom: 16,
    },
    followUpTitle: {
        color: colors.text,
        fontSize: 14,
        fontWeight: '700',
        marginBottom: 10,
    },
    followUpField: {
        marginBottom: 12,
    },
    followUpLabel: {
        color: colors.text,
        fontSize: 13,
        fontWeight: '600',
        marginBottom: 6,
    },
    followUpInput: {
        backgroundColor: colors.surface,
        borderColor: colors.border,
        borderWidth: 1,
        borderRadius: 10,
        color: colors.text,
        fontSize: 16,
        paddingHorizontal: 12,
        paddingVertical: 10,
    },
    followUpHint: {
        color: colors.textSecondary,
        fontSize: 12,
        marginTop: 5,
    },
    totalBox: {
        backgroundColor: `${colors.primary}18`,
        borderRadius: 16,
        borderWidth: 1,
        borderColor: `${colors.primary}40`,
        padding: 18,
        alignItems: 'center',
    },
    totalLabel: {
        fontSize: 14,
        fontWeight: '700',
        color: colors.primary,
        textTransform: 'uppercase',
        letterSpacing: 0.8,
        marginBottom: 16,
    },
    totalRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        width: '100%',
    },
    totalItem: {
        flex: 1,
        alignItems: 'center',
    },
    totalValue: {
        fontSize: 36,
        fontWeight: 'bold',
        color: colors.primary,
    },
    totalUnit: {
        fontSize: 13,
        color: colors.textSecondary,
        marginTop: 2,
    },
    totalDivider: {
        width: 1,
        height: 50,
        backgroundColor: colors.border,
    },
    totalFormula: {
        marginTop: 12,
        fontSize: 12,
        color: colors.textSecondary,
        fontStyle: 'italic',
    },
    logBtn: {
        marginHorizontal: 16,
        marginTop: 8,
    },
});

export default CameraScanScreen;
