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
import { getDailySummary, getLogsForDate } from '../store/storage';
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

const HistoryScreen = () => {
    const insets = useSafeAreaInsets();
    const isFocused = useIsFocused();
    const [rangeDays, setRangeDays] = useState<7 | 14 | 30>(7);
    const [metric, setMetric] = useState<MetricType>('calories');
    const [history, setHistory] = useState<DailySummary[]>([]);
    const [selectedDate, setSelectedDate] = useState<string>('');
    const [selectedLogs, setSelectedLogs] = useState<DailyMacroLog[]>([]);
    const [loading, setLoading] = useState(true);

    const loadHistory = useCallback(async () => {
        setLoading(true);
        try {
            const dates = Array.from({ length: rangeDays }, (_, i) => {
                const day = subDays(new Date(), rangeDays - 1 - i);
                return format(day, 'yyyy-MM-dd');
            });

            const summaries = await Promise.all(dates.map((date) => getDailySummary(date)));
            setHistory(summaries);

            const nextSelectedDate = selectedDate && summaries.some((summary) => summary.date === selectedDate)
                ? selectedDate
                : summaries[summaries.length - 1]?.date ?? format(new Date(), 'yyyy-MM-dd');

            setSelectedDate(nextSelectedDate);
            const logs = await getLogsForDate(nextSelectedDate);
            setSelectedLogs(logs);
        } finally {
            setLoading(false);
        }
    }, [rangeDays, selectedDate]);

    useFocusEffect(
        useCallback(() => {
            loadHistory();
        }, [loadHistory]),
    );

    const handleSelectDate = async (date: string) => {
        hapticLight();
        setSelectedDate(date);
        const logs = await getLogsForDate(date);
        setSelectedLogs(logs);
    };

    const selectedSummary = history.find((summary) => summary.date === selectedDate) ?? history[history.length - 1];

    const metricValues = history.map((summary) =>
        metric === 'calories' ? summary.totalCalories : summary.totalProtein,
    );
    const maxMetricValue = Math.max(...metricValues, 1);

    const insight = useMemo(() => {
        const activeDays = history.filter((day) => day.totalCalories > 0 || day.totalProtein > 0).length;
        const hitDays = history.filter(
            (day) => day.totalCalories <= day.calorieGoal && day.totalProtein >= day.proteinGoal,
        ).length;
        const avgCalories = history.length
            ? Math.round(history.reduce((sum, day) => sum + day.totalCalories, 0) / history.length)
            : 0;
        const avgProtein = history.length
            ? Math.round(history.reduce((sum, day) => sum + day.totalProtein, 0) / history.length)
            : 0;

        return {
            activeDays,
            hitDays,
            avgCalories,
            avgProtein,
        };
    }, [history]);

    return (
        <View style={styles.container}>
            <ScreenTransitionView style={styles.container}>
                <ScrollView contentContainerStyle={styles.scrollContent} removeClippedSubviews>
                    <View style={styles.contentWrap}>
                    <View style={[styles.header, { marginTop: insets.top + 8 }]}>
                        <Text style={styles.headerTitle}>History</Text>
                    </View>

                    {loading ? (
                        <View style={styles.loadingBox}>
                            <ActivityIndicator size="small" color={colors.primary} />
                            <Text style={styles.loadingText}>Loading history…</Text>
                        </View>
                    ) : null}

                    <AnimatedCard delay={80}>
                        <View style={styles.titleRow}>
                            <Ionicons name="pulse-outline" size={18} color={colors.text} />
                            <Text style={styles.cardTitle}>Insights</Text>
                        </View>

                        <View style={styles.insightRow}>
                            <View style={styles.insightItem}>
                                <Text style={styles.insightValue}>{insight.activeDays}</Text>
                                <Text style={styles.insightLabel}>active days</Text>
                            </View>
                            <View style={styles.insightItem}>
                                <Text style={styles.insightValue}>{insight.hitDays}</Text>
                                <Text style={styles.insightLabel}>target-hit days</Text>
                            </View>
                            <View style={styles.insightItem}>
                                <Text style={styles.insightValue}>{insight.avgCalories}</Text>
                                <Text style={styles.insightLabel}>avg kcal</Text>
                            </View>
                            <View style={styles.insightItem}>
                                <Text style={styles.insightValue}>{insight.avgProtein}g</Text>
                                <Text style={styles.insightLabel}>avg protein</Text>
                            </View>
                        </View>
                    </AnimatedCard>

                    <AnimatedCard delay={120}>
                        <View style={styles.titleRow}>
                            <Ionicons name="bar-chart-outline" size={18} color={colors.text} />
                            <Text style={styles.cardTitle}>Interactive Chart</Text>
                        </View>

                        <View style={styles.filterRow}>
                            {RANGE_OPTIONS.map((option) => {
                                const isActive = option === rangeDays;
                                return (
                                    <Pressable
                                        key={option}
                                        onPress={() => {
                                            hapticLight();
                                            setRangeDays(option);
                                        }}
                                        style={[styles.filterChip, isActive && styles.filterChipActive]}
                                    >
                                        <Text style={[styles.filterText, isActive && styles.filterTextActive]}>
                                            {option}d
                                        </Text>
                                    </Pressable>
                                );
                            })}
                        </View>

                        <View style={styles.filterRow}>
                            <Pressable
                                onPress={() => {
                                    hapticLight();
                                    setMetric('calories');
                                }}
                                style={[styles.filterChip, metric === 'calories' && styles.filterChipActive]}
                            >
                                <Text
                                    style={[
                                        styles.filterText,
                                        metric === 'calories' && styles.filterTextActive,
                                    ]}
                                >
                                    Calories
                                </Text>
                            </Pressable>
                            <Pressable
                                onPress={() => {
                                    hapticLight();
                                    setMetric('protein');
                                }}
                                style={[styles.filterChip, metric === 'protein' && styles.filterChipActive]}
                            >
                                <Text
                                    style={[
                                        styles.filterText,
                                        metric === 'protein' && styles.filterTextActive,
                                    ]}
                                >
                                    Protein
                                </Text>
                            </Pressable>
                        </View>

                        <ScrollView
                            horizontal
                            showsHorizontalScrollIndicator={false}
                            contentContainerStyle={styles.chartScroll}
                        >
                            {history.map((summary) => {
                                const value = metric === 'calories' ? summary.totalCalories : summary.totalProtein;
                                const height = 14 + (value / maxMetricValue) * 96;
                                const isSelected = summary.date === selectedDate;
                                return (
                                    <Pressable
                                        key={summary.date}
                                        onPress={() => handleSelectDate(summary.date)}
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
                                            {format(new Date(summary.date), 'EEEEE')}
                                        </Text>
                                    </Pressable>
                                );
                            })}
                        </ScrollView>
                    </AnimatedCard>

                    <AnimatedCard delay={160}>
                        <View style={styles.titleRow}>
                            <Ionicons name="calendar-outline" size={18} color={colors.text} />
                            <Text style={styles.cardTitle}>Selected Day</Text>
                        </View>

                        <Text style={styles.selectedDateText}>
                            {selectedSummary ? format(new Date(selectedSummary.date), 'EEEE, MMM d') : 'No day selected'}
                        </Text>

                        <View style={styles.selectedStatsRow}>
                            <Text style={styles.selectedStatText}>
                                {selectedSummary?.totalCalories ?? 0} kcal
                            </Text>
                            <Text style={styles.selectedStatText}>
                                {selectedSummary?.totalProtein ?? 0}g protein
                            </Text>
                        </View>

                        {selectedLogs.length === 0 ? (
                            <Text style={styles.emptyText}>No logs for this day.</Text>
                        ) : (
                            selectedLogs.map((log) => (
                                <View key={log.id} style={styles.logRow}>
                                    <Text style={styles.logName}>{log.foodName}</Text>
                                    <Text style={styles.logValue}>{log.calories} kcal • {log.protein}g</Text>
                                </View>
                            ))
                        )}
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
    loadingBox: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        marginBottom: 10,
        paddingHorizontal: 4,
    },
    loadingText: {
        color: colors.textSecondary,
        fontSize: 13,
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
    insightRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 10,
    },
    insightItem: {
        width: '47%',
        borderRadius: 12,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.background,
        padding: 10,
    },
    insightValue: {
        color: colors.text,
        fontSize: 18,
        fontWeight: '700',
    },
    insightLabel: {
        color: colors.textSecondary,
        fontSize: 12,
        marginTop: 2,
    },
    filterRow: {
        flexDirection: 'row',
        gap: 8,
        marginBottom: 10,
    },
    filterChip: {
        borderRadius: 999,
        borderWidth: 1,
        borderColor: colors.border,
        paddingVertical: 7,
        paddingHorizontal: 12,
        backgroundColor: colors.background,
    },
    filterChipActive: {
        backgroundColor: colors.primary,
        borderColor: colors.primary,
    },
    filterText: {
        color: colors.textSecondary,
        fontSize: 12,
        fontWeight: '600',
    },
    filterTextActive: {
        color: '#FFFFFF',
    },
    chartScroll: {
        gap: 8,
        paddingTop: 4,
    },
    barItem: {
        alignItems: 'center',
        width: 24,
    },
    barTrack: {
        height: 120,
        width: 16,
        borderRadius: 8,
        backgroundColor: colors.background,
        justifyContent: 'flex-end',
        overflow: 'hidden',
    },
    barFill: {
        width: '100%',
        borderRadius: 8,
    },
    barLabel: {
        color: colors.textSecondary,
        fontSize: 11,
        marginTop: 6,
    },
    barLabelActive: {
        color: colors.text,
        fontWeight: '700',
    },
    selectedDateText: {
        color: colors.text,
        fontSize: 14,
        fontWeight: '600',
        marginBottom: 8,
    },
    selectedStatsRow: {
        flexDirection: 'row',
        gap: 14,
        marginBottom: 8,
    },
    selectedStatText: {
        color: colors.textSecondary,
        fontSize: 13,
        fontWeight: '600',
    },
    logRow: {
        borderRadius: 10,
        borderWidth: 1,
        borderColor: colors.border,
        padding: 10,
        marginTop: 8,
        backgroundColor: colors.background,
    },
    logName: {
        color: colors.text,
        fontSize: 14,
        fontWeight: '600',
    },
    logValue: {
        color: colors.textSecondary,
        fontSize: 12,
        marginTop: 2,
    },
    emptyText: {
        color: colors.textSecondary,
        fontStyle: 'italic',
        marginTop: 4,
    },
});

export default HistoryScreen;
