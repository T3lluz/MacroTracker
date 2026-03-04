import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput, Pressable } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AnimatedCard from '../components/AnimatedCard';
import AnimatedButton from '../components/AnimatedButton';
import ScreenTransitionView from '../components/ScreenTransitionView';
import { colors } from '../theme/colors';
import { getGoals, saveGoals } from '../store/storage';
import { hapticLight, hapticSuccess } from '../utils/haptics';

const GOAL_PRESETS = [
    { label: 'Cut', calories: 1800, protein: 140 },
    { label: 'Maintain', calories: 2200, protein: 160 },
    { label: 'Build', calories: 2600, protein: 190 },
];

const StatsScreen = ({ navigation }: any) => {
    const insets = useSafeAreaInsets();
    const [calGoalStr, setCalGoalStr] = useState('2000');
    const [protGoalStr, setProtGoalStr] = useState('150');
    const [selectedPreset, setSelectedPreset] = useState<string | null>(null);
    const [goalFeedback, setGoalFeedback] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

    const loadData = async () => {
        const goals = await getGoals();
        if (goals) {
            setCalGoalStr(goals.calories.toString());
            setProtGoalStr(goals.protein.toString());
            const matchedPreset = GOAL_PRESETS.find(
                (preset) => preset.calories === goals.calories && preset.protein === goals.protein,
            );
            setSelectedPreset(matchedPreset?.label ?? null);
        }
    };

    useFocusEffect(
        useCallback(() => {
            loadData();
        }, []),
    );

    const handleSaveGoals = async () => {
        const caloriesGoal = parseInt(calGoalStr, 10) || 2000;
        const proteinGoal = parseInt(protGoalStr, 10) || 150;
        await saveGoals(caloriesGoal, proteinGoal);
        const matchedPreset = GOAL_PRESETS.find(
            (preset) => preset.calories === caloriesGoal && preset.protein === proteinGoal,
        );
        setSelectedPreset(matchedPreset?.label ?? null);
        setGoalFeedback({ text: 'Goals saved.', type: 'success' });
        hapticSuccess();
        loadData();
    };

    const applyPreset = async (preset: (typeof GOAL_PRESETS)[number]) => {
        hapticLight();
        setCalGoalStr(String(preset.calories));
        setProtGoalStr(String(preset.protein));
        setSelectedPreset(preset.label);
        await saveGoals(preset.calories, preset.protein);
        setGoalFeedback({ text: `${preset.label} preset applied.`, type: 'success' });
    };

    return (
        <View style={styles.container}>
            <ScreenTransitionView style={styles.container}>
                <ScrollView
                    contentContainerStyle={styles.scrollContent}
                    keyboardShouldPersistTaps="handled"
                    removeClippedSubviews
                >
                    <View style={styles.contentWrap}>
                        <View style={[styles.header, { marginTop: insets.top + 8 }]}>
                            <Text style={styles.headerTitle}>More</Text>
                        </View>

                    <AnimatedCard delay={80}>
                        <View style={styles.titleRow}>
                            <Ionicons name="settings-outline" size={18} color={colors.text} />
                            <Text style={styles.cardTitle}>Daily Goals</Text>
                        </View>

                        <View style={styles.row}>
                            <View style={[styles.inputContainer, { marginRight: 8 }]}>
                                <Text style={styles.label}>Calories</Text>
                                <TextInput
                                    style={styles.input}
                                    keyboardType="numeric"
                                    value={calGoalStr}
                                    onChangeText={(value) => {
                                        setCalGoalStr(value);
                                        setSelectedPreset(null);
                                    }}
                                    placeholderTextColor={colors.textSecondary}
                                />
                            </View>
                            <View style={[styles.inputContainer, { marginLeft: 8 }]}>
                                <Text style={styles.label}>Protein (g)</Text>
                                <TextInput
                                    style={styles.input}
                                    keyboardType="numeric"
                                    value={protGoalStr}
                                    onChangeText={(value) => {
                                        setProtGoalStr(value);
                                        setSelectedPreset(null);
                                    }}
                                    placeholderTextColor={colors.textSecondary}
                                />
                            </View>
                        </View>

                        <View style={styles.presetRow}>
                            {GOAL_PRESETS.map((preset) => (
                                <Pressable
                                    key={preset.label}
                                    onPress={() => applyPreset(preset)}
                                    style={({ pressed }) => [
                                        styles.presetChip,
                                        selectedPreset === preset.label && styles.presetChipSelected,
                                        pressed && styles.presetChipPressed,
                                    ]}
                                >
                                    <Text
                                        style={[
                                            styles.presetText,
                                            selectedPreset === preset.label && styles.presetTextSelected,
                                        ]}
                                    >
                                        {preset.label}
                                    </Text>
                                </Pressable>
                            ))}
                        </View>

                        <AnimatedButton title="Save Goals" onPress={handleSaveGoals} style={{ marginTop: 8 }} />
                        {!!goalFeedback && (
                            <Text
                                style={[
                                    styles.feedbackText,
                                    goalFeedback.type === 'success' ? styles.feedbackSuccess : styles.feedbackError,
                                ]}
                            >
                                {goalFeedback.text}
                            </Text>
                        )}
                    </AnimatedCard>

                        <AnimatedCard delay={120}>
                            <View style={styles.titleRow}>
                                <Ionicons name="stats-chart-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Analytics</Text>
                            </View>
                            <Text style={styles.helperText}>Open interactive graphs and complete history.</Text>
                            <AnimatedButton title="Open History" onPress={() => navigation.navigate('History')} />
                        </AnimatedCard>
                    </View>
                </ScrollView>
            </ScreenTransitionView>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: colors.background,
    },
    scrollContent: {
        padding: 16,
        paddingBottom: 120,
    },
    contentWrap: {
        width: '100%',
        maxWidth: 460,
        alignSelf: 'center',
    },
    header: {
        marginBottom: 12,
        paddingHorizontal: 2,
    },
    headerTitle: {
        fontSize: 30,
        fontWeight: '700',
        color: colors.text,
    },
    titleRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        marginBottom: 10,
    },
    cardTitle: {
        fontSize: 18,
        fontWeight: '700',
        color: colors.text,
    },
    row: {
        flexDirection: 'row',
    },
    inputContainer: {
        flex: 1,
    },
    label: {
        color: colors.textSecondary,
        marginBottom: 6,
        fontSize: 14,
    },
    helperText: {
        color: colors.textSecondary,
        marginBottom: 10,
        fontSize: 13,
    },
    input: {
        backgroundColor: colors.background,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 12,
        paddingHorizontal: 12,
        paddingVertical: 11,
        color: colors.text,
        fontSize: 15,
        marginBottom: 10,
    },
    presetRow: {
        flexDirection: 'row',
        gap: 8,
        marginBottom: 4,
    },
    presetChip: {
        flex: 1,
        borderRadius: 999,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.background,
        paddingVertical: 8,
        alignItems: 'center',
    },
    presetChipSelected: {
        borderColor: colors.primary,
        backgroundColor: colors.surface,
    },
    presetChipPressed: {
        opacity: 0.85,
    },
    presetText: {
        fontSize: 12,
        fontWeight: '600',
        color: colors.textSecondary,
    },
    presetTextSelected: {
        color: colors.text,
    },
    feedbackText: {
        marginTop: 4,
        fontSize: 13,
        fontWeight: '600',
    },
    feedbackSuccess: {
        color: colors.secondary,
    },
    feedbackError: {
        color: colors.error,
    },
});

export default StatsScreen;
