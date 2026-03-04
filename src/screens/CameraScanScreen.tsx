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

// ─── Gemini Vision API ────────────────────────────────────────────────────────
// Set your free Gemini API key here (get one at https://aistudio.google.com/app/apikey)
const GEMINI_API_KEY = 'AIzaSyCGWHG77glS_8mqsxofjbPkrxJPvZZGq_M';

const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=${GEMINI_API_KEY}`;

interface ScanResult {
    foodName: string;
    caloriesPerServing: number;
    proteinPerServing: number;
    servingsPerContainer: number;
    totalCalories: number;
    totalProtein: number;
}

const PROMPT = `You are a nutrition label reader. Analyze this image of a food product's nutrition label.
Extract the following values exactly as printed:
1. Product/food name (from anywhere on the packaging)
2. Calories per serving
3. Protein per serving in grams
4. Servings per container (or servings per bag/package)

Then calculate:
- Total calories = calories per serving × servings per container
- Total protein = protein per serving × servings per container

Respond ONLY with a JSON object in this exact format, no markdown, no explanation:
{
  "foodName": "string",
  "caloriesPerServing": number,
  "proteinPerServing": number,
  "servingsPerContainer": number,
  "totalCalories": number,
  "totalProtein": number
}

If a value cannot be found, use 0. Never return null.`;

async function analyzeImageWithGemini(base64Image: string): Promise<ScanResult> {
    const body = {
        contents: [
            {
                parts: [
                    { text: PROMPT },
                    {
                        inline_data: {
                            mime_type: 'image/jpeg',
                            data: base64Image,
                        },
                    },
                ],
            },
        ],
        generationConfig: {
            temperature: 0.1,
            maxOutputTokens: 512,
        },
    };

    const response = await fetch(GEMINI_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });

    if (!response.ok) {
        const err = await response.text();
        throw new Error(`Gemini API error ${response.status}: ${err}`);
    }

    const data = await response.json();
    const text: string = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';

    // Strip any accidental markdown fences
    const cleaned = text.replace(/```json|```/g, '').trim();
    const parsed = JSON.parse(cleaned) as ScanResult;

    // Validate / sanitise
    return {
        foodName: parsed.foodName || 'Scanned Food',
        caloriesPerServing: Math.round(Number(parsed.caloriesPerServing) || 0),
        proteinPerServing: Math.round(Number(parsed.proteinPerServing) || 0),
        servingsPerContainer: Math.round(Number(parsed.servingsPerContainer) || 1),
        totalCalories: Math.round(Number(parsed.totalCalories) || 0),
        totalProtein: Math.round(Number(parsed.totalProtein) || 0),
    };
}

// ─── Component ────────────────────────────────────────────────────────────────
const CameraScanScreen = ({ navigation, route }: any) => {
    const [permission, requestPermission] = useCameraPermissions();
    const [phase, setPhase] = useState<'camera' | 'preview' | 'result'>('camera');
    const [capturedUri, setCapturedUri] = useState<string | null>(null);
    const [capturedBase64, setCapturedBase64] = useState<string | null>(null);
    const [scanning, setScanning] = useState(false);
    const [result, setResult] = useState<ScanResult | null>(null);
    const [servingsOverride, setServingsOverride] = useState('');
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
                quality: 0.7,
                base64: true,
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
            mediaTypes: ImagePicker.MediaTypeOptions.Images,
            quality: 0.7,
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

        if (GEMINI_API_KEY === 'YOUR_GEMINI_API_KEY_HERE') {
            Alert.alert(
                '⚠️ API Key Required',
                'Please set your Gemini API key in CameraScanScreen.tsx.\n\nGet a free key at:\nhttps://aistudio.google.com/app/apikey',
            );
            return;
        }

        setScanning(true);
        try {
            const scanResult = await analyzeImageWithGemini(capturedBase64);
            setResult(scanResult);
            setServingsOverride(String(scanResult.servingsPerContainer));
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
        const servings = parseFloat(servingsOverride) || result.servingsPerContainer;
        return {
            ...result,
            servingsPerContainer: servings,
            totalCalories: Math.round(result.caloriesPerServing * servings),
            totalProtein: Math.round(result.proteinPerServing * servings),
        };
    };

    // ── Log the food ──────────────────────────────────────────────────────────
    const handleLog = useCallback(() => {
        const adj = getAdjustedResult();
        if (!adj) return;
        // Navigate back passing data to HomeScreen
        navigation.navigate('Home', {
            scannedFood: {
                foodName: adj.foodName,
                calories: adj.totalCalories,
                protein: adj.totalProtein,
            },
        });
    }, [result, servingsOverride, navigation]);

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
    const adj = getAdjustedResult();
    return (
        <View style={styles.container}>
            <ScrollView contentContainerStyle={styles.resultContent}>
                {/* Thumbnail */}
                <Image source={{ uri: capturedUri! }} style={styles.thumbnail} resizeMode="cover" />

                <Animated.View entering={FadeIn.duration(400)} style={styles.resultCard}>
                    <Text style={styles.resultTitle}>📊 Nutrition Scan Results</Text>
                    <Text style={styles.resultFoodName}>{result!.foodName}</Text>

                    {/* Per serving */}
                    <View style={styles.resultSection}>
                        <Text style={styles.resultSectionLabel}>Per Serving</Text>
                        <View style={styles.macroRow}>
                            <View style={[styles.macroPill, { backgroundColor: colors.primaryVariant }]}>
                                <Text style={styles.macroPillValue}>{result!.caloriesPerServing}</Text>
                                <Text style={styles.macroPillLabel}>kcal</Text>
                            </View>
                            <View style={[styles.macroPill, { backgroundColor: '#1a5e5a' }]}>
                                <Text style={styles.macroPillValue}>{result!.proteinPerServing}g</Text>
                                <Text style={styles.macroPillLabel}>protein</Text>
                            </View>
                        </View>
                    </View>

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
                            ({result!.caloriesPerServing} kcal × {parseFloat(servingsOverride) || result!.servingsPerContainer} servings)
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
