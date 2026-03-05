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
import AnimatedButton from '../components/AnimatedButton';
import ScreenTransitionView from '../components/ScreenTransitionView';
import { colors } from '../theme/colors';
import { getLogsForDate } from '../store/storage';
import { DailyHealthMetrics, DailyMacroLog } from '../types';
import {
    getHealthMetricsRange,
    hasHealthConnectPermissions,
    requestHealthConnectPermissions,
    summarizeHealthRange,
    ensureHealthConnectInitialized,
} from '../utils/healthConnect';
import { hapticLight } from '../utils/haptics';

type MetricType = 'steps' | 'caloriesBurned' | 'sleepMinutes' | 'avgHeartRate';

const RANGE_OPTIONS: Array<7 | 14 | 30> = [7, 14, 30];

const buildEmptyHealthHistory = (rangeDays: number): DailyHealthMetrics[] => {
    return Array.from({ length: rangeDays }, (_, i) => {
        const day = subDays(new Date(), rangeDays - 1 - i);
        return {
            date: format(day, 'yyyy-MM-dd'),
            steps: 0,
            caloriesBurned: 0,
            sleepMinutes: 0,
            avgHeartRate: 0,
        };
    });
};

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
    steps: 'Steps',
    caloriesBurned: 'Calories Burned',
    sleepMinutes: 'Sleep',
    avgHeartRate: 'Avg Heart Rate',
};

const HistoryScreen = () => {
    const insets = useSafeAreaInsets();
    const isFocused = useIsFocused();
    const [rangeDays, setRangeDays] = useState<7 | 14 | 30>(7);
    const [metric, setMetric] = useState<MetricType>('steps');
    const [healthHistory, setHealthHistory] = useState<DailyHealthMetrics[]>(buildEmptyHealthHistory(7));
    const [selectedDate, setSelectedDate] = useState<string>(format(new Date(), 'yyyy-MM-dd'));
    const [selectedLogs, setSelectedLogs] = useState<DailyMacroLog[]>([]);
    const [loading, setLoading] = useState(true);
    const [permissionsGranted, setPermissionsGranted] = useState(false);
    const [healthStatus, setHealthStatus] = useState('Checking Health Connect status...');

    const syncSelectedDate = useCallback(async (days: DailyHealthMetrics[]) => {
        const fallbackDate = days[days.length - 1]?.date ?? format(new Date(), 'yyyy-MM-dd');

        let nextDate = fallbackDate;
        setSelectedDate((prev) => {
            nextDate = prev && days.some((entry) => entry.date === prev) ? prev : fallbackDate;
            return nextDate;
        });

        const logs = await getLogsForDate(nextDate);
        setSelectedLogs(logs);
    }, []);

    const loadHealthHistory = useCallback(
        async (requestAccess: boolean) => {
            setLoading(true);

            try {
                const initStatus = await ensureHealthConnectInitialized();
                if (!initStatus.available) {
                    const emptyHistory = buildEmptyHealthHistory(rangeDays);
                    setPermissionsGranted(false);
                    setHealthStatus(initStatus.message ?? 'Health Connect is unavailable.');
                    setHealthHistory(emptyHistory);
                    await syncSelectedDate(emptyHistory);
                    return;
                }

                const hasPermission = requestAccess
                    ? await requestHealthConnectPermissions()
                    : await hasHealthConnectPermissions();

                if (!hasPermission) {
                    const emptyHistory = buildEmptyHealthHistory(rangeDays);
                    setPermissionsGranted(false);
                    setHealthStatus('Grant Health Connect access to view watch-derived health metrics.');
                    setHealthHistory(emptyHistory);
                    await syncSelectedDate(emptyHistory);
                    return;
                }

                const metrics = await getHealthMetricsRange(rangeDays);
                setPermissionsGranted(true);
                setHealthStatus('Health Connect synced. Data source includes Garmin Connect if synced there.');
                setHealthHistory(metrics);
                await syncSelectedDate(metrics);
            } catch (error) {
                setPermissionsGranted(false);
                setHealthStatus(
                    error instanceof Error
                        ? error.message
                        : 'Could not read Health Connect data right now.',
                );
            } finally {
                setLoading(false);
            }
        },
        [rangeDays, syncSelectedDate],
    );

    useFocusEffect(
        useCallback(() => {
            loadHealthHistory(false);
        }, [loadHealthHistory]),
    );

    const handleSelectDate = async (date: string) => {
        hapticLight();
        setSelectedDate(date);
        const logs = await getLogsForDate(date);
        setSelectedLogs(logs);
    };

    const getMetricValue = useCallback((day: DailyHealthMetrics, metricType: MetricType) => {
        if (metricType === 'steps') return day.steps;
        if (metricType === 'caloriesBurned') return day.caloriesBurned;
        if (metricType === 'sleepMinutes') return day.sleepMinutes;
        return day.avgHeartRate;
    }, []);

    const formatMetricValue = useCallback((metricType: MetricType, value: number) => {
        if (metricType === 'steps') return `${value.toLocaleString()} steps`;
        if (metricType === 'caloriesBurned') return `${value} kcal`;
        if (metricType === 'sleepMinutes') return `${(value / 60).toFixed(1)} h`;
        return `${value} bpm`;
    }, []);

    const metricValues = healthHistory.map((day) => getMetricValue(day, metric));
    const maxMetricValue = Math.max(...metricValues, 1);

    const summary = useMemo(() => summarizeHealthRange(healthHistory), [healthHistory]);
    const selectedHealth = healthHistory.find((day) => day.date === selectedDate) ?? healthHistory[healthHistory.length - 1];

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
                                <Ionicons name="watch-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Health Connect</Text>
                            </View>

                            <Text style={styles.helperText}>{healthStatus}</Text>

                            {permissionsGranted ? (
                                <AnimatedButton
                                    title="Refresh Health Data"
                                    onPress={() => {
                                        hapticLight();
                                        loadHealthHistory(false);
                                    }}
                                />
                            ) : (
                                <AnimatedButton
                                    title="Connect Health Data"
                                    onPress={() => {
                                        hapticLight();
                                        loadHealthHistory(true);
                                    }}
                                />
                            )}
                        </AnimatedCard>

                        {loading ? (
                            <View style={styles.loadingBox}>
                                <ActivityIndicator size="small" color={colors.primary} />
                                <Text style={styles.loadingText}>Syncing health metrics…</Text>
                            </View>
                        ) : null}

                        <AnimatedCard delay={100}>
                            <View style={styles.titleRow}>
                                <Ionicons name="pulse-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Health Stats</Text>
                            </View>

                            <View style={styles.insightRow}>
                                <View style={styles.insightItem}>
                                    <Text style={styles.insightValue}>{summary.averageSteps.toLocaleString()}</Text>
                                    <Text style={styles.insightLabel}>avg steps</Text>
                                </View>
                                <View style={styles.insightItem}>
                                    <Text style={styles.insightValue}>{summary.averageCaloriesBurned}</Text>
                                    <Text style={styles.insightLabel}>avg active kcal</Text>
                                </View>
                                <View style={styles.insightItem}>
                                    <Text style={styles.insightValue}>{(summary.averageSleepMinutes / 60).toFixed(1)}h</Text>
                                    <Text style={styles.insightLabel}>avg sleep</Text>
                                </View>
                                <View style={styles.insightItem}>
                                    <Text style={styles.insightValue}>{summary.averageHeartRate || '--'}</Text>
                                    <Text style={styles.insightLabel}>avg HR (bpm)</Text>
                                </View>
                            </View>

                            <Text style={styles.secondaryMetaText}>{summary.daysWithData} active data days in {rangeDays}d range</Text>
                        </AnimatedCard>

                        <AnimatedCard delay={130}>
                            <View style={styles.titleRow}>
                                <Ionicons name="bar-chart-outline" size={18} color={colors.text} />
                                <Text style={styles.cardTitle}>Interactive Health Chart</Text>
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

                            <View style={styles.metricRow}>
                                {(['steps', 'caloriesBurned', 'sleepMinutes', 'avgHeartRate'] as MetricType[]).map((metricOption) => {
                                    const isActive = metric === metricOption;
                                    return (
                                        <Pressable
                                            key={metricOption}
                                            onPress={() => {
                                                hapticLight();
                                                setMetric(metricOption);
                                            }}
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
                                {healthHistory.map((day) => {
                                    const value = getMetricValue(day, metric);
                                    const height = 14 + (value / maxMetricValue) * 96;
                                    const isSelected = day.date === selectedDate;

                                    return (
                                        <Pressable
                                            key={day.date}
                                            onPress={() => handleSelectDate(day.date)}
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
                                                {format(new Date(day.date), 'EEEEE')}
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
                                {selectedHealth ? format(new Date(selectedHealth.date), 'EEEE, MMM d') : 'No day selected'}
                            </Text>

                            <View style={styles.selectedStatsGrid}>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Steps</Text>
                                    <Text style={styles.selectedStatValue}>{selectedHealth?.steps.toLocaleString() ?? 0}</Text>
                                </View>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Calories Burned</Text>
                                    <Text style={styles.selectedStatValue}>{selectedHealth?.caloriesBurned ?? 0} kcal</Text>
                                </View>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Sleep</Text>
                                    <Text style={styles.selectedStatValue}>
                                        {((selectedHealth?.sleepMinutes ?? 0) / 60).toFixed(1)} h
                                    </Text>
                                </View>
                                <View style={styles.selectedStatCard}>
                                    <Text style={styles.selectedStatLabel}>Avg Heart Rate</Text>
                                    <Text style={styles.selectedStatValue}>
                                        {selectedHealth?.avgHeartRate ? `${selectedHealth.avgHeartRate} bpm` : '--'}
                                    </Text>
                                </View>
                            </View>

                            <Text style={styles.secondaryMetaText}>
                                {metricLabelMap[metric]}: {formatMetricValue(metric, getMetricValue(selectedHealth ?? {
                                    date: selectedDate,
                                    steps: 0,
                                    caloriesBurned: 0,
                                    sleepMinutes: 0,
                                    avgHeartRate: 0,
                                }, metric))}
                            </Text>

                            <View style={styles.logsWrap}>
                                <Text style={styles.subsectionTitle}>Food logs for this day</Text>
                                {selectedLogs.length === 0 ? (
                                    <Text style={styles.emptyText}>No food logs for this day.</Text>
                                ) : (
                                    selectedLogs.map((log) => (
                                        <View key={log.id} style={styles.logRow}>
                                            <Text style={styles.logName}>{log.foodName}</Text>
                                            <Text style={styles.logValue}>{log.calories} kcal • {log.protein}g</Text>
                                        </View>
                                    ))
                                )}
                            </View>
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
    helperText: {
        color: colors.textSecondary,
        fontSize: 13,
        marginBottom: 10,
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
    secondaryMetaText: {
        color: colors.textSecondary,
        fontSize: 12,
        marginTop: 10,
        fontWeight: '600',
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
        color: colors.text,
    },
    metricRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 8,
        marginBottom: 10,
    },
    metricChip: {
        borderRadius: 999,
        borderWidth: 1,
        borderColor: colors.border,
        paddingVertical: 7,
        paddingHorizontal: 12,
        backgroundColor: colors.background,
    },
    metricChipActive: {
        backgroundColor: colors.surface,
        borderColor: colors.primary,
    },
    metricText: {
        color: colors.textSecondary,
        fontSize: 12,
        fontWeight: '600',
    },
    metricTextActive: {
        color: colors.text,
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
    selectedStatsGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 10,
        marginBottom: 4,
    },
    selectedStatCard: {
        width: '47%',
        borderRadius: 12,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.background,
        padding: 10,
    },
    selectedStatLabel: {
        color: colors.textSecondary,
        fontSize: 12,
        marginBottom: 2,
    },
    selectedStatValue: {
        color: colors.text,
        fontSize: 15,
        fontWeight: '700',
    },
    logsWrap: {
        marginTop: 12,
    },
    subsectionTitle: {
        color: colors.text,
        fontSize: 14,
        fontWeight: '700',
        marginBottom: 2,
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