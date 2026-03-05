import React, { useState, useCallback } from 'react';
import {
    View,
    Text,
    StyleSheet,
    TextInput,
    ScrollView,
    KeyboardAvoidingView,
    Platform,
    ActivityIndicator,
    Pressable,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { format } from 'date-fns';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AnimatedCard from '../components/AnimatedCard';
import ProgressBar from '../components/ProgressBar';
import AnimatedButton from '../components/AnimatedButton';
import ScreenTransitionView from '../components/ScreenTransitionView';
import { colors } from '../theme/colors';
import { saveLog, getDailySummary } from '../store/storage';
import { DailySummary, DailyMacroLog } from '../types';
import { hapticError, hapticLight, hapticSuccess } from '../utils/haptics';

const HomeScreen = ({ navigation, route }: any) => {
    const insets = useSafeAreaInsets();
    const [foodName, setFoodName] = useState('');
    const [calories, setCalories] = useState('');
    const [protein, setProtein] = useState('');
    const [feedback, setFeedback] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
    const [showAdvancedEstimator, setShowAdvancedEstimator] = useState(false);
    const [estimatorFoodName, setEstimatorFoodName] = useState('');
    const [estimatorCaloriesPer100, setEstimatorCaloriesPer100] = useState('');
    const [estimatorProteinPer100, setEstimatorProteinPer100] = useState('');
    const [estimatorWeight, setEstimatorWeight] = useState('');
    const [estimatorFeedback, setEstimatorFeedback] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

    const [summary, setSummary] = useState<DailySummary | null>(null);

    const today = format(new Date(), 'yyyy-MM-dd');

    const loadData = async () => {
        const dailySummary = await getDailySummary(today);
        setSummary(dailySummary);
    };

    useFocusEffect(
        useCallback(() => {
            loadData();

            if (route?.params?.scannedFood) {
                const scannedFood = route.params.scannedFood;
                setFoodName(scannedFood.foodName ?? 'Scanned Food');
                setCalories(String(scannedFood.calories ?? ''));
                setProtein(String(scannedFood.protein ?? ''));
                setFeedback({ text: 'Scanned values added. Review and save.', type: 'success' });
                navigation.setParams({ scannedFood: undefined });
            }
        }, [navigation, route?.params?.scannedFood]),
    );

    const handleAddLog = async () => {
        if (!calories || !protein) {
            setFeedback({ text: 'Enter calories and protein before saving.', type: 'error' });
            hapticError();
            return;
        }

        const newLog: DailyMacroLog = {
            id: Date.now().toString(),
            date: today,
            foodName: foodName || 'Quick Add',
            calories: Math.round(parseFloat(calories) || 0),
            protein: Math.round(parseFloat(protein) || 0),
        };

        await saveLog(newLog);

        // Reset inputs
        setFoodName('');
        setCalories('');
        setProtein('');
        setFeedback({ text: 'Log saved.', type: 'success' });
        hapticSuccess();

        // Reload
        loadData();
    };

    const estimatorCalories = parseFloat(estimatorCaloriesPer100) || 0;
    const estimatorProtein = parseFloat(estimatorProteinPer100) || 0;
    const estimatorWeightValue = parseFloat(estimatorWeight) || 0;
    const estimatedTotalCalories = Math.round((estimatorCalories * estimatorWeightValue) / 100);
    const estimatedTotalProtein = Math.round((estimatorProtein * estimatorWeightValue) / 100);

    const handleAddEstimatedLog = async () => {
        if (estimatorWeightValue <= 0) {
            setEstimatorFeedback({ text: 'Enter a valid weight in grams.', type: 'error' });
            hapticError();
            return;
        }

        if (estimatorCalories <= 0 && estimatorProtein <= 0) {
            setEstimatorFeedback({ text: 'Add calories or protein per 100g first.', type: 'error' });
            hapticError();
            return;
        }

        const newLog: DailyMacroLog = {
            id: Date.now().toString(),
            date: today,
            foodName: estimatorFoodName.trim() || 'Estimated Food',
            calories: estimatedTotalCalories,
            protein: estimatedTotalProtein,
        };

        await saveLog(newLog);
        setEstimatorFoodName('');
        setEstimatorWeight('');
        setEstimatorFeedback({ text: 'Estimated log added.', type: 'success' });
        hapticSuccess();
        loadData();
    };

    if (!summary) {
        return (
            <View style={[styles.container, styles.loadingContainer]}>
                <ActivityIndicator size="small" color={colors.primary} />
                <Text style={styles.loadingText}>Loading today…</Text>
            </View>
        );
    }

    const calProgress = summary.calorieGoal > 0 ? summary.totalCalories / summary.calorieGoal : 0;
    const protProgress = summary.proteinGoal > 0 ? summary.totalProtein / summary.proteinGoal : 0;

    return (
        <View style={styles.container}>
            <ScreenTransitionView style={styles.contentContainer}>
                <KeyboardAvoidingView
                    style={styles.contentContainer}
                    behavior={Platform.OS === 'ios' ? 'padding' : undefined}
                >
                    <ScrollView
                        contentContainerStyle={styles.scrollContent}
                        keyboardShouldPersistTaps="handled"
                        removeClippedSubviews
                    >
                        <View style={styles.contentWrap}>
                            <View style={[styles.header, { marginTop: insets.top + 8 }]}>
                                <Text style={styles.headerTitle}>Quick Log</Text>
                                <Text style={styles.headerDate}>{format(new Date(), 'EEEE, MMM do')}</Text>
                            </View>

                            <AnimatedCard delay={80}>
                                <View style={styles.titleRow}>
                                    <Ionicons name="flag-outline" size={18} color={colors.text} />
                                    <Text style={styles.cardTitle}>Daily Progress</Text>
                                </View>
                                <ProgressBar
                                    progress={calProgress}
                                    label={`${summary.totalCalories} / ${summary.calorieGoal} kcal`}
                                    color={calProgress > 1 ? colors.error : colors.primary}
                                />
                                <ProgressBar
                                    progress={protProgress}
                                    label={`${summary.totalProtein} / ${summary.proteinGoal} g protein`}
                                    color={colors.secondary}
                                />
                            </AnimatedCard>

                            <AnimatedCard delay={120}>
                                <View style={styles.titleRow}>
                                    <Ionicons name="add-circle-outline" size={18} color={colors.text} />
                                    <Text style={styles.cardTitle}>Add Entry</Text>
                                </View>
                                <TextInput
                                    style={styles.input}
                                    placeholder="Food name"
                                    placeholderTextColor={colors.textSecondary}
                                    value={foodName}
                                    onChangeText={setFoodName}
                                />
                                <View style={styles.row}>
                                    <TextInput
                                        style={[styles.input, styles.halfInput]}
                                        placeholder="Calories"
                                        placeholderTextColor={colors.textSecondary}
                                        keyboardType="numeric"
                                        value={calories}
                                        onChangeText={setCalories}
                                    />
                                    <TextInput
                                        style={[styles.input, styles.halfInput]}
                                        placeholder="Protein (g)"
                                        placeholderTextColor={colors.textSecondary}
                                        keyboardType="numeric"
                                        value={protein}
                                        onChangeText={setProtein}
                                    />
                                </View>
                                <AnimatedButton
                                    title="Add Log"
                                    onPress={handleAddLog}
                                    style={{ marginTop: 4 }}
                                />

                                <Pressable
                                    onPress={() => {
                                        hapticLight();
                                        setShowAdvancedEstimator((prev) => !prev);
                                    }}
                                    style={({ pressed }) => [styles.advancedToggle, pressed && styles.advancedTogglePressed]}
                                >
                                    <View style={styles.advancedToggleLeft}>
                                        <Ionicons name="flask-outline" size={16} color={colors.text} />
                                        <Text style={styles.advancedToggleText}>Advanced Macro Estimator</Text>
                                    </View>
                                    <Ionicons
                                        name={showAdvancedEstimator ? 'chevron-up' : 'chevron-down'}
                                        size={16}
                                        color={colors.textSecondary}
                                    />
                                </Pressable>

                                {showAdvancedEstimator ? (
                                    <View style={styles.advancedPanel}>
                                        <Text style={styles.helperText}>Estimate totals from per-100g label values.</Text>
                                        <TextInput
                                            style={styles.input}
                                            value={estimatorFoodName}
                                            onChangeText={setEstimatorFoodName}
                                            placeholder="Food name (optional)"
                                            placeholderTextColor={colors.textSecondary}
                                        />

                                        <View style={styles.row}>
                                            <TextInput
                                                style={[styles.input, styles.halfInput]}
                                                keyboardType="numeric"
                                                value={estimatorCaloriesPer100}
                                                onChangeText={setEstimatorCaloriesPer100}
                                                placeholder="Calories / 100g"
                                                placeholderTextColor={colors.textSecondary}
                                            />
                                            <TextInput
                                                style={[styles.input, styles.halfInput]}
                                                keyboardType="numeric"
                                                value={estimatorProteinPer100}
                                                onChangeText={setEstimatorProteinPer100}
                                                placeholder="Protein / 100g"
                                                placeholderTextColor={colors.textSecondary}
                                            />
                                        </View>

                                        <TextInput
                                            style={styles.input}
                                            keyboardType="numeric"
                                            value={estimatorWeight}
                                            onChangeText={setEstimatorWeight}
                                            placeholder="Weight eaten (g)"
                                            placeholderTextColor={colors.textSecondary}
                                        />

                                        <View style={styles.estimateResultBox}>
                                            <Text style={styles.estimateResultText}>{estimatedTotalCalories} kcal</Text>
                                            <Text style={styles.estimateResultText}>{estimatedTotalProtein}g protein</Text>
                                        </View>

                                        <AnimatedButton
                                            title="Add Estimated Log"
                                            onPress={handleAddEstimatedLog}
                                            variant="secondary"
                                            haptic="medium"
                                        />
                                        {!!estimatorFeedback && (
                                            <Text
                                                style={[
                                                    styles.feedbackText,
                                                    estimatorFeedback.type === 'success'
                                                        ? styles.feedbackSuccess
                                                        : styles.feedbackError,
                                                ]}
                                            >
                                                {estimatorFeedback.text}
                                            </Text>
                                        )}
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
                        </View>
                    </ScrollView>
                </KeyboardAvoidingView>
            </ScreenTransitionView>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: colors.background,
    },
    contentContainer: {
        flex: 1,
    },
    loadingContainer: {
        alignItems: 'center',
        justifyContent: 'center',
    },
    loadingText: {
        marginTop: 8,
        color: colors.textSecondary,
        fontSize: 13,
    },
    contentWrap: {
        width: '100%',
        maxWidth: 460,
        alignSelf: 'center',
    },
    scrollContent: {
        padding: 16,
        paddingBottom: 120,
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
    headerDate: {
        fontSize: 14,
        color: colors.textSecondary,
        marginTop: 4,
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
        paddingVertical: 12,
        color: colors.text,
        marginBottom: 12,
        fontSize: 15,
    },
    row: {
        flexDirection: 'row',
        gap: 10,
    },
    halfInput: {
        flex: 1,
    },
    advancedToggle: {
        marginTop: 8,
        marginBottom: 6,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 12,
        backgroundColor: colors.background,
        paddingHorizontal: 12,
        paddingVertical: 10,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    advancedTogglePressed: {
        opacity: 0.85,
    },
    advancedToggleLeft: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
    },
    advancedToggleText: {
        color: colors.text,
        fontSize: 14,
        fontWeight: '600',
    },
    advancedPanel: {
        borderRadius: 12,
        borderWidth: 1,
        borderColor: colors.border,
        padding: 10,
        backgroundColor: colors.surface,
    },
    estimateResultBox: {
        backgroundColor: colors.background,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 12,
        padding: 12,
        marginBottom: 8,
    },
    estimateResultText: {
        color: colors.text,
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 2,
    },
    feedbackText: {
        fontSize: 13,
        marginTop: 2,
        fontWeight: '600',
    },
    feedbackSuccess: {
        color: colors.secondary,
    },
    feedbackError: {
        color: colors.error,
    },
});

export default HomeScreen;
