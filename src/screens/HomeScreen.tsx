import React, { useState, useCallback } from 'react';
import {
    View,
    Text,
    StyleSheet,
    TextInput,
    ScrollView,
    KeyboardAvoidingView,
    Platform,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { format } from 'date-fns';
import AnimatedCard from '../components/AnimatedCard';
import ProgressBar from '../components/ProgressBar';
import AnimatedButton from '../components/AnimatedButton';
import MacroLogItem from '../components/MacroLogItem';
import { colors } from '../theme/colors';
import { saveLog, getDailySummary, getLogsForDate, deleteLog } from '../store/storage';
import { DailySummary, DailyMacroLog } from '../types';

const HomeScreen = ({ navigation }: any) => {
    const [foodName, setFoodName] = useState('');
    const [calories, setCalories] = useState('');
    const [protein, setProtein] = useState('');

    const [summary, setSummary] = useState<DailySummary | null>(null);
    const [logs, setLogs] = useState<DailyMacroLog[]>([]);

    const today = format(new Date(), 'yyyy-MM-dd');

    const loadData = async () => {
        const dailySummary = await getDailySummary(today);
        const dailyLogs = await getLogsForDate(today);
        setSummary(dailySummary);
        setLogs(dailyLogs);
    };

    useFocusEffect(
        useCallback(() => {
            loadData();
        }, [])
    );

    const handleAddLog = async () => {
        if (!calories || !protein) return;

        const newLog: DailyMacroLog = {
            id: Date.now().toString(),
            date: today,
            foodName: foodName || 'Quick Add',
            calories: parseInt(calories, 10) || 0,
            protein: parseInt(protein, 10) || 0,
        };

        await saveLog(newLog);

        // Reset inputs
        setFoodName('');
        setCalories('');
        setProtein('');

        // Reload
        loadData();
    };

    const handleDeleteLog = async (id: string) => {
        await deleteLog(id);
        loadData();
    };

    if (!summary) return <View style={styles.container} />;

    const calProgress = summary.calorieGoal > 0 ? summary.totalCalories / summary.calorieGoal : 0;
    const protProgress = summary.proteinGoal > 0 ? summary.totalProtein / summary.proteinGoal : 0;

    return (
        <KeyboardAvoidingView
            style={styles.container}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
            <ScrollView contentContainerStyle={styles.scrollContent}>

                <View style={styles.header}>
                    <Text style={styles.headerTitle}>Today</Text>
                    <Text style={styles.headerDate}>{format(new Date(), 'EEEE, MMM do')}</Text>
                </View>

                <AnimatedCard delay={100}>
                    <Text style={styles.cardTitle}>Daily Summary</Text>
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

                <AnimatedCard delay={200}>
                    <Text style={styles.cardTitle}>Add Entry</Text>
                    <TextInput
                        style={styles.input}
                        placeholder="Food Name (optional)"
                        placeholderTextColor={colors.textSecondary}
                        value={foodName}
                        onChangeText={setFoodName}
                    />
                    <View style={styles.row}>
                        <TextInput
                            style={[styles.input, { flex: 1, marginRight: 8 }]}
                            placeholder="Calories"
                            placeholderTextColor={colors.textSecondary}
                            keyboardType="numeric"
                            value={calories}
                            onChangeText={setCalories}
                        />
                        <TextInput
                            style={[styles.input, { flex: 1, marginLeft: 8 }]}
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
                        style={{ marginTop: 8 }}
                    />
                </AnimatedCard>

                <AnimatedCard delay={300} style={{ padding: 0, backgroundColor: 'transparent', shadowOpacity: 0, elevation: 0 }}>
                    <Text style={[styles.cardTitle, { paddingHorizontal: 4 }]}>Recent Logs</Text>
                    {logs.length === 0 ? (
                        <Text style={styles.emptyText}>No logs yet today.</Text>
                    ) : (
                        logs.slice().reverse().map((log, index) => (
                            <MacroLogItem
                                key={log.id}
                                item={log}
                                onDelete={handleDeleteLog}
                                index={index}
                            />
                        ))
                    )}
                </AnimatedCard>

            </ScrollView>

            <View style={styles.footer}>
                <AnimatedButton
                    variant="secondary"
                    title="View Stats & History"
                    onPress={() => navigation.navigate('Stats')}
                />
            </View>
        </KeyboardAvoidingView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: colors.background,
    },
    scrollContent: {
        padding: 16,
        paddingBottom: 100, // Space for footer
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
    headerDate: {
        fontSize: 16,
        color: colors.textSecondary,
        marginTop: 4,
    },
    cardTitle: {
        fontSize: 20,
        fontWeight: 'bold',
        color: colors.text,
        marginBottom: 16,
    },
    input: {
        backgroundColor: colors.background,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: 10,
        padding: 14,
        color: colors.text,
        marginBottom: 12,
        fontSize: 16,
    },
    row: {
        flexDirection: 'row',
    },
    emptyText: {
        color: colors.textSecondary,
        fontStyle: 'italic',
        textAlign: 'center',
        marginTop: 20,
    },
    footer: {
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        padding: 16,
        paddingBottom: Platform.OS === 'ios' ? 32 : 16,
        backgroundColor: colors.surface,
        borderTopWidth: 1,
        borderTopColor: colors.border,
    }
});

export default HomeScreen;
