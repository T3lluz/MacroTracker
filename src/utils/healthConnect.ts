import { addDays, format, startOfDay, subDays } from 'date-fns';
import { Platform } from 'react-native';
import {
    type Permission,
} from 'react-native-health-connect';
import { DailyHealthMetrics, HealthRangeSummary } from '../types';

type HealthConnectModule = typeof import('react-native-health-connect');

export const HEALTH_RECORD_PERMISSIONS: Permission[] = [
    { accessType: 'read', recordType: 'Steps' },
    { accessType: 'read', recordType: 'ActiveCaloriesBurned' },
    { accessType: 'read', recordType: 'SleepSession' },
    { accessType: 'read', recordType: 'HeartRate' },
];

export interface HealthConnectAvailability {
    available: boolean;
    message?: string;
}

const NOT_LINKED_MESSAGE =
    'Health Connect native module is not linked. Use an Android development build (not Expo Go), then reinstall the app.';

let healthConnectModuleCache: HealthConnectModule | null | undefined;

const getHealthConnectModule = async (): Promise<HealthConnectModule | null> => {
    if (healthConnectModuleCache !== undefined) {
        return healthConnectModuleCache;
    }

    try {
        const module = await import('react-native-health-connect');
        healthConnectModuleCache = module;
        return module;
    } catch {
        healthConnectModuleCache = null;
        return null;
    }
};

const mapHealthConnectErrorMessage = (error: unknown): string => {
    const rawMessage = error instanceof Error ? error.message : String(error ?? '');
    if (
        rawMessage.includes("doesn't seem to be linked") ||
        rawMessage.includes('Native module')
    ) {
        return NOT_LINKED_MESSAGE;
    }

    return 'Could not access Health Connect data right now.';
};

export const checkHealthConnectAvailability = async (): Promise<HealthConnectAvailability> => {
    if (Platform.OS !== 'android') {
        return { available: false, message: 'Health Connect is available on Android only.' };
    }

    const healthConnect = await getHealthConnectModule();
    if (!healthConnect) {
        return {
            available: false,
            message: NOT_LINKED_MESSAGE,
        };
    }

    try {
        const sdkStatus = await healthConnect.getSdkStatus();
        if (sdkStatus === healthConnect.SdkAvailabilityStatus.SDK_AVAILABLE) {
            return { available: true };
        }

        if (sdkStatus === healthConnect.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            return {
                available: false,
                message: 'Health Connect needs to be installed or updated on this device.',
            };
        }

        return {
            available: false,
            message: 'Health Connect is unavailable on this device.',
        };
    } catch (error) {
        return {
            available: false,
            message: mapHealthConnectErrorMessage(error),
        };
    }
};

const hasAllRequiredPermissions = (
    permissions: Array<{ accessType?: string; recordType?: string }>,
): boolean => {
    return HEALTH_RECORD_PERMISSIONS.every((required) =>
        permissions.some(
            (permission) =>
                permission.accessType === required.accessType && permission.recordType === required.recordType,
        ),
    );
};

export const ensureHealthConnectInitialized = async (): Promise<HealthConnectAvailability> => {
    const availability = await checkHealthConnectAvailability();
    if (!availability.available) {
        return availability;
    }

    const healthConnect = await getHealthConnectModule();
    if (!healthConnect) {
        return { available: false, message: NOT_LINKED_MESSAGE };
    }

    try {
        const initialized = await healthConnect.initialize();
        if (!initialized) {
            return { available: false, message: 'Failed to initialize Health Connect.' };
        }
    } catch (error) {
        return {
            available: false,
            message: mapHealthConnectErrorMessage(error),
        };
    }

    return { available: true };
};

export const hasHealthConnectPermissions = async (): Promise<boolean> => {
    const initState = await ensureHealthConnectInitialized();
    if (!initState.available) {
        return false;
    }

    const healthConnect = await getHealthConnectModule();
    if (!healthConnect) {
        return false;
    }

    try {
        const grantedPermissions = await healthConnect.getGrantedPermissions();
        return hasAllRequiredPermissions(grantedPermissions);
    } catch {
        return false;
    }
};

export const requestHealthConnectPermissions = async (): Promise<boolean> => {
    const initState = await ensureHealthConnectInitialized();
    if (!initState.available) {
        return false;
    }

    const healthConnect = await getHealthConnectModule();
    if (!healthConnect) {
        return false;
    }

    try {
        const grantedPermissions = await healthConnect.requestPermission(HEALTH_RECORD_PERMISSIONS);
        return hasAllRequiredPermissions(grantedPermissions);
    } catch {
        return false;
    }
};

export const openHealthConnectAppSettings = () => {
    getHealthConnectModule().then((healthConnect) => {
        healthConnect?.openHealthConnectSettings();
    });
};

export const getDailyHealthMetrics = async (date: Date): Promise<DailyHealthMetrics> => {
    const healthConnect = await getHealthConnectModule();
    if (!healthConnect) {
        throw new Error(NOT_LINKED_MESSAGE);
    }

    const dayStart = startOfDay(date);
    const dayEnd = addDays(dayStart, 1);

    const timeRangeFilter = {
        operator: 'between' as const,
        startTime: dayStart.toISOString(),
        endTime: dayEnd.toISOString(),
    };

    const [stepsResult, caloriesResult, sleepResult, heartRateResult] = await Promise.all([
        healthConnect.readRecords('Steps', { timeRangeFilter }),
        healthConnect.readRecords('ActiveCaloriesBurned', { timeRangeFilter }),
        healthConnect.readRecords('SleepSession', { timeRangeFilter }),
        healthConnect.readRecords('HeartRate', { timeRangeFilter }),
    ]);

    const steps = Math.round(stepsResult.records.reduce((sum, record) => sum + (record.count ?? 0), 0));

    const caloriesBurned = Math.round(
        caloriesResult.records.reduce(
            (sum, record) => sum + (record.energy?.inKilocalories ?? 0),
            0,
        ),
    );

    const sleepMinutes = Math.round(
        sleepResult.records.reduce((sum, record) => {
            const startTime = new Date(record.startTime).getTime();
            const endTime = new Date(record.endTime).getTime();
            if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) {
                return sum;
            }

            return sum + (endTime - startTime) / 60000;
        }, 0),
    );

    const allHeartRateSamples = heartRateResult.records.flatMap((record) => record.samples ?? []);
    const avgHeartRate = allHeartRateSamples.length
        ? Math.round(
              allHeartRateSamples.reduce((sum, sample) => sum + (sample.beatsPerMinute ?? 0), 0) /
                  allHeartRateSamples.length,
          )
        : 0;

    return {
        date: format(dayStart, 'yyyy-MM-dd'),
        steps,
        caloriesBurned,
        sleepMinutes,
        avgHeartRate,
    };
};

export const getHealthMetricsRange = async (rangeDays: number): Promise<DailyHealthMetrics[]> => {
    const initState = await ensureHealthConnectInitialized();
    if (!initState.available) {
        throw new Error(initState.message ?? 'Health Connect is unavailable.');
    }

    const dates = Array.from({ length: rangeDays }, (_, index) => {
        const day = subDays(new Date(), rangeDays - 1 - index);
        return startOfDay(day);
    });

    const results = await Promise.all(dates.map((date) => getDailyHealthMetrics(date)));
    return results;
};

export const summarizeHealthRange = (days: DailyHealthMetrics[]): HealthRangeSummary => {
    if (days.length === 0) {
        return {
            averageSteps: 0,
            averageCaloriesBurned: 0,
            averageSleepMinutes: 0,
            averageHeartRate: 0,
            daysWithData: 0,
        };
    }

    const totalSteps = days.reduce((sum, day) => sum + day.steps, 0);
    const totalCaloriesBurned = days.reduce((sum, day) => sum + day.caloriesBurned, 0);
    const totalSleepMinutes = days.reduce((sum, day) => sum + day.sleepMinutes, 0);
    const daysWithHeartRate = days.filter((day) => day.avgHeartRate > 0);
    const totalHeartRate = daysWithHeartRate.reduce((sum, day) => sum + day.avgHeartRate, 0);

    return {
        averageSteps: Math.round(totalSteps / days.length),
        averageCaloriesBurned: Math.round(totalCaloriesBurned / days.length),
        averageSleepMinutes: Math.round(totalSleepMinutes / days.length),
        averageHeartRate: daysWithHeartRate.length ? Math.round(totalHeartRate / daysWithHeartRate.length) : 0,
        daysWithData: days.filter((day) => day.steps > 0 || day.caloriesBurned > 0 || day.sleepMinutes > 0).length,
    };
};