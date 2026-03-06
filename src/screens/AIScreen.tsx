import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { format } from 'date-fns';
import { Ionicons } from '@expo/vector-icons';
import AnimatedCard from '../components/AnimatedCard';
import AnimatedButton from '../components/AnimatedButton';
import ScreenTransitionView from '../components/ScreenTransitionView';
import { colors } from '../theme/colors';
import { estimateNutritionWithAI, NutritionEstimate } from '../utils/nutritionAI';
import { saveLog } from '../store/storage';
import { hapticError, hapticSuccess } from '../utils/haptics';

const AIScreen = ({ navigation }: any) => {
    const insets = useSafeAreaInsets();
    const [foodQuery, setFoodQuery] = useState('');
    const [loading, setLoading] = useState(false);
    const [estimate, setEstimate] = useState<NutritionEstimate | null>(null);
    const [feedback, setFeedback] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

    const confidenceColor = useMemo(() => {
        if (!estimate) return colors.textSecondary;
        if (estimate.confidence === 'high') return colors.secondary;
        if (estimate.confidence === 'low') return colors.error;
        return colors.primary;
    }, [estimate]);

    const handleEstimate = async () => {
        if (!foodQuery.trim()) {
            setFeedback({ text: 'Enter a food name or description first.', type: 'error' });
            hapticError();
            return;
        }

        setLoading(true);
        setFeedback(null);

        try {
            const nextEstimate = await estimateNutritionWithAI(foodQuery);
            setEstimate(nextEstimate);
            setFeedback({ text: 'AI estimate ready.', type: 'success' });
            hapticSuccess();
        } catch (error: any) {
            setEstimate(null);
            setFeedback({ text: error?.message || 'Failed to estimate macros.', type: 'error' });
            hapticError();
        } finally {
            setLoading(false);
        }
    };

    const handleLogEstimate = async () => {
        if (!estimate) return;

        await saveLog({
            id: Date.now().toString(),
            date: format(new Date(), 'yyyy-MM-dd'),
            foodName: `${estimate.foodName} (AI)`,
            calories: estimate.calories,
            protein: estimate.protein,
        });

        setFeedback({ text: 'AI estimate logged to today.', type: 'success' });
        hapticSuccess();
    };

    return (
        <View style={styles.container}>
            <ScreenTransitionView style={styles.container}>
                <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
                    <View style={styles.contentWrap}>
                        <View style={[styles.header, { marginTop: insets.top + 8 }]}>
                            <Text style={styles.headerTitle}>AI Nutrition</Text>
                            <Text style={styles.headerSubTitle}>Ask for macro estimates when labels are unavailable.</Text>
                        </View>

                        <AnimatedCard delay={60}>
                            <View style={styles.titleRow}>
                                <Ionicons name="camera-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>AI Camera Scan</Text>
                            </View>
                            <Text style={styles.metaText}>
                                Scan nutrition labels and let AI ask for any missing details automatically.
                            </Text>
                            <AnimatedButton
                                title="Open Camera Label Scan"
                                onPress={() => navigation.navigate('CameraScan')}
                                haptic="medium"
                            />
                        </AnimatedCard>

                        <AnimatedCard delay={80}>
                            <View style={styles.titleRow}>
                                <Ionicons name="sparkles-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Estimate Food Macros</Text>
                            </View>

                            <TextInput
                                style={styles.input}
                                placeholder="e.g. 1 medium avocado"
                                placeholderTextColor={colors.textSecondary}
                                value={foodQuery}
                                onChangeText={setFoodQuery}
                                multiline
                            />

                            <AnimatedButton
                                title={loading ? 'Estimating...' : 'Estimate with AI'}
                                onPress={handleEstimate}
                                haptic="medium"
                            />

                            {loading ? (
                                <View style={styles.loadingRow}>
                                    <ActivityIndicator size="small" color={colors.primary} />
                                    <Text style={styles.loadingText}>Crunching an estimate…</Text>
                                </View>
                            ) : null}

                            {!!feedback && (
                                <Text
                                    style={[
                                        styles.feedbackText,
                                        feedback.type === 'success' ? styles.feedbackSuccess : styles.feedbackError,
                                    ]}
                                >
                                    {feedback.text}
                                </Text>
                            )}
                        </AnimatedCard>

                        {estimate ? (
                            <AnimatedCard delay={120}>
                                <View style={styles.titleRow}>
                                    <Ionicons name="nutrition-outline" size={18} color={colors.text} />
                                    <Text style={styles.cardTitle}>Estimate Result</Text>
                                </View>

                                <Text style={styles.foodName}>{estimate.foodName}</Text>
                                <Text style={styles.metaText}>{estimate.servingDescription}</Text>

                                <View style={styles.resultRow}>
                                    <View style={styles.resultPill}>
                                        <Text style={styles.resultValue}>{estimate.calories}</Text>
                                        <Text style={styles.resultLabel}>kcal</Text>
                                    </View>
                                    <View style={styles.resultPill}>
                                        <Text style={styles.resultValue}>{estimate.protein}g</Text>
                                        <Text style={styles.resultLabel}>protein</Text>
                                    </View>
                                </View>

                                <Text style={[styles.metaText, { color: confidenceColor }]}>Confidence: {estimate.confidence}</Text>
                                <Text style={styles.noteText}>{estimate.notes}</Text>

                                <AnimatedButton
                                    title="Log This Estimate"
                                    onPress={handleLogEstimate}
                                    variant="secondary"
                                />
                            </AnimatedCard>
                        ) : null}
                    </View>
                </ScrollView>
            </ScreenTransitionView>
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    scrollContent: { padding: 16, paddingBottom: 120 },
    contentWrap: { width: '100%', maxWidth: 460, alignSelf: 'center' },
    header: { marginBottom: 12, paddingHorizontal: 2 },
    headerTitle: { fontSize: 30, fontWeight: '700', color: colors.text },
    headerSubTitle: { marginTop: 4, color: colors.textSecondary, fontSize: 14 },
    titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
    cardTitle: { fontSize: 18, fontWeight: '700', color: colors.text },
    input: {
        backgroundColor: colors.background,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 12,
        paddingHorizontal: 12,
        paddingVertical: 12,
        color: colors.text,
        marginBottom: 10,
        fontSize: 15,
        minHeight: 70,
        textAlignVertical: 'top',
    },
    loadingRow: { marginTop: 10, flexDirection: 'row', alignItems: 'center', gap: 8 },
    loadingText: { color: colors.textSecondary, fontSize: 13 },
    feedbackText: { marginTop: 8, fontSize: 13, fontWeight: '600' },
    feedbackSuccess: { color: colors.secondary },
    feedbackError: { color: colors.error },
    foodName: { color: colors.text, fontSize: 18, fontWeight: '700', marginBottom: 4 },
    metaText: { color: colors.textSecondary, fontSize: 13, marginBottom: 8 },
    resultRow: { flexDirection: 'row', gap: 10, marginBottom: 8 },
    resultPill: { flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: 12, paddingVertical: 10, alignItems: 'center', backgroundColor: colors.background },
    resultValue: { color: colors.text, fontSize: 20, fontWeight: '700' },
    resultLabel: { color: colors.textSecondary, fontSize: 12, marginTop: 2 },
    noteText: { color: colors.textSecondary, fontSize: 12, marginBottom: 10 },
});

export default AIScreen;

