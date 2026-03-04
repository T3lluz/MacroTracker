import AsyncStorage from '@react-native-async-storage/async-storage';
import { DailyMacroLog, DailySummary } from '../types';

const LOGS_KEY = '@macro_logs';
const GOALS_KEY = '@macro_goals';

export const saveLog = async (log: DailyMacroLog) => {
    try {
        const existingLogsJson = await AsyncStorage.getItem(LOGS_KEY);
        const existingLogs: DailyMacroLog[] = existingLogsJson ? JSON.parse(existingLogsJson) : [];
        existingLogs.push(log);
        await AsyncStorage.setItem(LOGS_KEY, JSON.stringify(existingLogs));
    } catch (error) {
        console.error('Error saving log:', error);
    }
};

export const getLogs = async (): Promise<DailyMacroLog[]> => {
    try {
        const logsJson = await AsyncStorage.getItem(LOGS_KEY);
        return logsJson ? JSON.parse(logsJson) : [];
    } catch (error) {
        console.error('Error getting logs:', error);
        return [];
    }
};

export const deleteLog = async (id: string) => {
    try {
        const existingLogsJson = await AsyncStorage.getItem(LOGS_KEY);
        const existingLogs: DailyMacroLog[] = existingLogsJson ? JSON.parse(existingLogsJson) : [];
        const filtered = existingLogs.filter(log => log.id !== id);
        await AsyncStorage.setItem(LOGS_KEY, JSON.stringify(filtered));
    } catch (e) {
        console.error('Error deleting log', e);
    }
}

export const getLogsForDate = async (dateStr: string): Promise<DailyMacroLog[]> => {
    const logs = await getLogs();
    return logs.filter(log => log.date === dateStr);
};

export const getDailySummary = async (dateStr: string): Promise<DailySummary> => {
    const logs = await getLogsForDate(dateStr);
    const totalCalories = logs.reduce((sum, log) => sum + log.calories, 0);
    const totalProtein = logs.reduce((sum, log) => sum + log.protein, 0);

    const goals = await getGoals();

    return {
        date: dateStr,
        totalCalories,
        totalProtein,
        calorieGoal: goals?.calories || 2000,
        proteinGoal: goals?.protein || 150
    }
}

export const saveGoals = async (calories: number, protein: number) => {
    try {
        await AsyncStorage.setItem(GOALS_KEY, JSON.stringify({ calories, protein }));
    } catch (e) {
        console.error('Error saving goals:', e);
    }
}

export const getGoals = async (): Promise<{ calories: number, protein: number } | null> => {
    try {
        const json = await AsyncStorage.getItem(GOALS_KEY);
        return json ? JSON.parse(json) : { calories: 2000, protein: 150 };
    } catch (e) {
        return { calories: 2000, protein: 150 };
    }
}
