import React, { useCallback, useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, ActivityIndicator } from 'react-native';
import { useFocusEffect, useIsFocused } from '@react-navigation/native';
import { format, subDays } from 'date-fns';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, {
    Easing,
    useAnimatedStyle,
    useSharedValue,
    withTiming,
} from 'react-native-reanimated';
import AnimatedCard from '../components/AnimatedCard';
import ScreenTransitionView from '../components/ScreenTransitionView';
import { colors } from '../theme/colors';
import { getLogsForDate, getDailySummariesRange } from '../store/storage';
import { DailyMacroLog, DailySummary } from '../types';
import { hapticLight } from '../utils/haptics';

type MetricType = 'calories' | 'protein';

const RANGE_OPTIONS: Array<7 | 14 | 30> = [7, 14, 30];

const AnimatedBarFill = React.memo(({
    targetHeight,
    color,
    trigger,
}: {
    targetHeight: number;
    color: string;
    trigger: string;
}) => {
    const animatedHeight = useSharedValue(0);

    React.useEffect(() => {
        animatedHeight.value = 0;
        animatedHeight.value = withTiming(targetHeight, {
            duration: 360,
            easing: Easing.out(Easing.cubic),
        });
    }, [targetHeight, trigger]);

    const animatedStyle = useAnimatedStyle(() => ({
        height: animatedHeight.value,
        backgroundColor: color,
    }));

    return <Animated.View style={[styles.barFill, animatedStyle]} />;
});

const metricLabelMap: Record<MetricType, string> = {
    calories: 'Calories',
    protein: 'Protein',
};

const HistoryScreen = () => {
    const insets = useSafeAreaInsets();
    const isFocused = useIsFocused();
    const [rangeDays, setRangeDays] = useState<7 | 14 | 30>(7);
    const [metric, setMetric] = useState<MetricType>('calories');
    const [macroHistory, setMacroHistory] = useState<DailySummary[]>([]);
    const [selectedDate, setSelectedDate] = useState<string>(format(new Date(), 'yyyy-MM-dd'));
    const [selectedLogs, setSelectedLogs] = useState<DailyMacroLog[]>([]);
    const [loading, setLoading] = useState(true);

    const syncSelectedDate = useCallback(async (dateStr: string) => {
        const logs = await getLogsForDate(dateStr);
        setSelectedLogs(logs);
    }, []);

    const loadHistoryData = useCallback(async () => {
        setLoading(true);
        try {
            const summaries = await getDailySummariesRange(rangeDays);
            setMacroHistory(summaries);
            await syncSelectedDate(selectedDate);
        } catch (error) {
            console.error('History load error:', error);
        } finally {
            setLoading(false);
        }
    }, [rangeDays, selectedDate, syncSelectedDate]);

    useFocusEffect(
        useCallback(() => {
            loadHistoryData();
        }, [loadHistoryData]),
    );

    const handleSelectDate = async (date: string) => {
        hapticLight();
        setSelectedDate(date);
        await syncSelectedDate(date);
    };

    const getMetricValue = useCallback((date: string, metricType: MetricType) => {
        const day = macroHistory.find(d => d.date === date);
        return metricType === 'calories' ? (day?.totalCalories ?? 0) : (day?.totalProtein ?? 0);
    }, [macroHistory]);

    const dates = useMemo(() => {
        return Array.from({ length: rangeDays }, (_, i) => {
            return format(subDays(new Date(), rangeDays - 1 - i), 'yyyy-MM-dd');
        });
    }, [rangeDays]);

    const metricValues = dates.map((date) => getMetricValue(date, metric));
    const maxMetricValue = Math.max(...metricValues, 1);

    const selectedMacro = macroHistory.find((day) => day.date === selectedDate);

    return (
        <View style={styles.container}>
            <ScreenTransitionView style={styles.container}>
                <ScrollView contentContainerStyle={styles.scrollContent} removeClippedSubviews>
                    <View style={styles.contentWrap}>
                        <View style={[styles.header, { marginTop: insets.top + 8 }]}>
                            <Text style={styles.headerTitle}>History</Text>
                        </View>

                        <AnimatedCard delay={70}>
                            <View style={styles.titleRow}>
                                <Ionicons name="bar-chart-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Macro Trends</Text>
                            </View>

                            <View style={styles.filterRow}>
                                {RANGE_OPTIONS.map((option) => {
                                    const isActive = option === rangeDays;
                                    return (
                                        <Pressable
                                            key={option}
                                            onPress={() => { hapticLight(); setRangeDays(option); }}
                                            style={[styles.filterChip, isActive && styles.filterChipActive]}
                                        >
                                            <Text style={[styles.filterText, isActive && styles.filterTextActive]}>
                                                {option}d
                                            </Text>
                                        </Pressable>
                                    );
                                })}
                            </View>

                            <View style={styles.metricRow}>
                                {(['calories', 'protein'] as MetricType[]).map((metricOption) => {
                                    const isActive = metric === metricOption;
                                    return (
                                        <Pressable
                                            key={metricOption}
                                            onPress={() => { hapticLight(); setMetric(metricOption); }}
                                            style={[styles.metricChip, isActive && styles.metricChipActive]}
                                        >
                                            <Text style={[styles.metricText, isActive && styles.metricTextActive]}>
                                                {metricLabelMap[metricOption]}
                                            </Text>
                                        </Pressable>
                                    );
                                })}
                            </View>

                            <ScrollView
                                horizontal
                                showsHorizontalScrollIndicator={false}
                                contentContainerStyle={styles.chartScroll}
                            >
                                {dates.map((date) => {
                                    const value = getMetricValue(date, metric);
                                    const height = 14 + (value / maxMetricValue) * 96;
                                    const isSelected = date === selectedDate;
                                    return (
                                        <Pressable
                                            key={date}
                                            onPress={() => handleSelectDate(date)}
                                            style={styles.barItem}
                                        >
                                            <View style={styles.barTrack}>
                                                <AnimatedBarFill
                                                    targetHeight={height}
                                                    color={isSelected ? colors.primary : colors.primaryVariant}
                                                    trigger={`${isFocused}-${metric}-${rangeDays}`}
                                                />
                                            </View>
                                            <Text style={[styles.barLabel, isSelected && styles.barLabelActive]}>
                                                {format(new Date(date), 'EEEEE')}
                                            </Text>
                                        </Pressable>
                                    );
                                })}
                            </ScrollView>
                        </AnimatedCard>

                        <AnimatedCard delay={100}>
                            <View style={styles.titleRow}>
                                <Ionicons name="calendar-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>
                                    {format(new Date(selectedDate), 'EEEE, MMM d')}
                                </Text>
                            </View>

                            <View style={styles.selectedStatsGrid}>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Calories</Text>
                                    <Text style={styles.selectedStatValue}>{selectedMacro?.totalCalories ?? 0} kcal</Text>
                                </View>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Protein</Text>
                                    <Text style={styles.selectedStatValue}>{selectedMacro?.totalProtein ?? 0}g</Text>
                                </View>
                            </View>

                            <View style={styles.logsWrap}>
                                <Text style={styles.subsectionTitle}>Food Logs</Text>
                                {selectedLogs.length === 0 ? (
                                    <Text style={styles.emptyText}>No food logs for this day.</Text>
                                ) : (
                                    selectedLogs.map((log) => (
                                        <View key={log.id} style={styles.logRow}>
                                            <View style={styles.logLeft}>
                                                <Text style={styles.logName}>{log.foodName}</Text>
                                                <Text style={styles.logValue}>{log.calories} kcal • {log.protein}g</Text>
                                            </View>
                                        </View>
                                    ))
                                )}
                            </View>
                        </AnimatedCard>

                        {loading && (
                            <View style={styles.loadingBox}>
                                <ActivityIndicator size="small" color={colors.primary} />
                                <Text style={styles.loadingText}>Loading history…</Text>
                            </View>
                        )}
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
    titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
    cardTitle: { fontSize: 18, fontWeight: '700', color: colors.text },
    helperText: { color: colors.textSecondary, fontSize: 13, marginBottom: 10 },
    loadingBox: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginTop: 20, gap: 8 },
    loadingText: { color: colors.textSecondary, fontSize: 13 },
    filterRow: { flexDirection: 'row', gap: 8, marginBottom: 12 },
    filterChip: { borderRadius: 999, borderWidth: 1, borderColor: colors.border, paddingVertical: 6, paddingHorizontal: 12, backgroundColor: colors.background },
    filterChipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
    filterText: { color: colors.textSecondary, fontSize: 12, fontWeight: '600' },
    filterTextActive: { color: '#FFFFFF' },
    metricRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 },
    metricChip: { borderRadius: 999, borderWidth: 1, borderColor: colors.border, paddingVertical: 6, paddingHorizontal: 12, backgroundColor: colors.background },
    metricChipActive: { backgroundColor: colors.surface, borderColor: colors.primary },
    metricText: { color: colors.textSecondary, fontSize: 12, fontWeight: '600' },
    metricTextActive: { color: colors.text },
    chartScroll: { paddingTop: 8, paddingBottom: 4, gap: 10 },
    barItem: { alignItems: 'center', width: 28 },
    barTrack: { height: 120, width: 18, borderRadius: 9, backgroundColor: colors.background, justifyContent: 'flex-end', overflow: 'hidden' },
    barFill: { width: '100%', borderRadius: 9 },
    barLabel: { color: colors.textSecondary, fontSize: 11, marginTop: 6 },
    barLabelActive: { color: colors.text, fontWeight: '700' },
    selectedStatsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 16 },
    selectedStatCard: { width: '47%', borderRadius: 12, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.background, padding: 12 },
    selectedStatLabel: { color: colors.textSecondary, fontSize: 12, marginBottom: 4 },
    selectedStatValue: { color: colors.text, fontSize: 16, fontWeight: '700' },
    logsWrap: { marginTop: 4 },
    subsectionTitle: { color: colors.text, fontSize: 14, fontWeight: '700', marginBottom: 8 },
    logRow: { borderRadius: 10, borderWidth: 1, borderColor: colors.border, padding: 10, marginBottom: 8, backgroundColor: colors.background, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    logLeft: { flex: 1 },
    logName: { color: colors.text, fontSize: 14, fontWeight: '600' },
    logValue: { color: colors.textSecondary, fontSize: 12, marginTop: 2 },
    emptyText: { color: colors.textSecondary, fontStyle: 'italic', fontSize: 13 },
});

export default HistoryScreen;
