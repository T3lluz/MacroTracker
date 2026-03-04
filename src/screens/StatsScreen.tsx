import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { format, subDays } from 'date-fns';
import AnimatedCard from '../components/AnimatedCard';
import ProgressBar from '../components/ProgressBar';
import AnimatedButton from '../components/AnimatedButton';
import { colors } from '../theme/colors';
import { getDailySummary, getGoals, saveGoals } from '../store/storage';
import { DailySummary } from '../types';

const StatsScreen = ({ navigation }: any) => {
    const [history, setHistory] = useState<DailySummary[]>([]);
    const [calGoalStr, setCalGoalStr] = useState('2000');
    const [protGoalStr, setProtGoalStr] = useState('150');

    const loadData = async () => {
        // Load last 7 days including today
        const days = Array.from({ length: 7 }, (_, i) => {
            const d = subDays(new Date(), i);
            return format(d, 'yyyy-MM-dd');
        });

        const histories = await Promise.all(
            days.map(d => getDailySummary(d))
        );

        setHistory(histories);

        const goals = await getGoals();
        if (goals) {
            setCalGoalStr(goals.calories.toString());
            setProtGoalStr(goals.protein.toString());
        }
    };

    useFocusEffect(
        useCallback(() => {
            loadData();
        }, [])
    );

    const handleSaveGoals = async () => {
        const cal = parseInt(calGoalStr, 10) || 2000;
        const prot = parseInt(protGoalStr, 10) || 150;
        await saveGoals(cal, prot);
        loadData();
    }

    return (
        <View style={styles.container}>
            <ScrollView contentContainerStyle={styles.scrollContent}>
                <View style={styles.header}>
                    <Text style={styles.headerTitle}>Stats & Settings</Text>
                </View>

                <AnimatedCard delay={100}>
                    <Text style={styles.cardTitle}>Daily Goals</Text>
                    <View style={styles.row}>
                        <View style={[styles.inputContainer, { marginRight: 8 }]}>
                            <Text style={styles.label}>Calories</Text>
                            <TextInput
                                style={styles.input}
                                keyboardType="numeric"
                                value={calGoalStr}
                                onChangeText={setCalGoalStr}
                                placeholderTextColor={colors.textSecondary}
                            />
                        </View>
                        <View style={[styles.inputContainer, { marginLeft: 8 }]}>
                            <Text style={styles.label}>Protein (g)</Text>
                            <TextInput
                                style={styles.input}
                                keyboardType="numeric"
                                value={protGoalStr}
                                onChangeText={setProtGoalStr}
                                placeholderTextColor={colors.textSecondary}
                            />
                        </View>
                    </View>
                    <AnimatedButton title="Save Goals" onPress={handleSaveGoals} style={{ marginTop: 12 }} />
                </AnimatedCard>

                <AnimatedCard delay={200}>
                    <Text style={styles.cardTitle}>Last 7 Days</Text>
                    {history.map((day, index) => {
                        const isValidDate = day.totalCalories > 0 || day.totalProtein > 0;
                        if (!isValidDate && index !== 0) return null; // Only show today if empty, otherwise hide empty days for cleaner history

                        const isToday = index === 0;
                        const dayName = isToday ? 'Today' : format(new Date(day.date), 'EEE, MMM d');

                        const calProgress = day.calorieGoal > 0 ? day.totalCalories / day.calorieGoal : 0;
                        const protProgress = day.proteinGoal > 0 ? day.totalProtein / day.proteinGoal : 0;

                        return (
                            <View key={day.date} style={styles.historyItem}>
                                <Text style={styles.historyDate}>{dayName}</Text>
                                <ProgressBar
                                    progress={calProgress}
                                    height={6}
                                    color={calProgress > 1 ? colors.error : colors.primary}
                                />
                                <ProgressBar
                                    progress={protProgress}
                                    height={6}
                                    color={colors.secondary}
                                />
                                <View style={styles.historyTextRow}>
                                    <Text style={styles.historyTextDetail}>{day.totalCalories} kcal</Text>
                                    <Text style={styles.historyTextDetail}>{day.totalProtein}g pro</Text>
                                </View>
                            </View>
                        )
                    })}
                </AnimatedCard>

            </ScrollView>
            <View style={styles.footer}>
                <AnimatedButton
                    variant="secondary"
                    title="Back to Home"
                    onPress={() => navigation.goBack()}
                />
            </View>
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
        paddingBottom: 100,
    },
    header: {
        marginTop: 40,
        marginBottom: 20,
        paddingHorizontal: 8,
    },
    headerTitle: {
        fontSize: 32,
        fontWeight: 'bold',
        color: colors.primary,
    },
    cardTitle: {
        fontSize: 20,
        fontWeight: 'bold',
        color: colors.text,
        marginBottom: 16,
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
    input: {
        backgroundColor: colors.background,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 10,
        padding: 14,
        color: colors.text,
        fontSize: 16,
    },
    historyItem: {
        marginBottom: 16,
        backgroundColor: colors.background,
        padding: 12,
        borderRadius: 8,
    },
    historyDate: {
        color: colors.text,
        fontWeight: 'bold',
        marginBottom: 8,
    },
    historyTextRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginTop: 4,
    },
    historyTextDetail: {
        color: colors.textSecondary,
        fontSize: 12,
    },
    footer: {
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        padding: 16,
        paddingBottom: 32,
        backgroundColor: colors.surface,
        borderTopWidth: 1,
        borderTopColor: colors.border,
    }
});

export default StatsScreen;
