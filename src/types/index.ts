export interface DailyMacroLog {
    id: string;
    date: string; // ISO string YYYY-MM-DD
    calories: number;
    protein: number;
    foodName: string;
}

export interface DailySummary {
    date: string;
    totalCalories: number;
    totalProtein: number;
    calorieGoal: number;
    proteinGoal: number;
}

export interface DailyHealthMetrics {
    date: string; // ISO string YYYY-MM-DD
    steps: number;
    caloriesBurned: number; // active kcal
    sleepMinutes: number;
    avgHeartRate: number; // bpm
}

export interface HealthRangeSummary {
    averageSteps: number;
    averageCaloriesBurned: number;
    averageSleepMinutes: number;
    averageHeartRate: number;
    daysWithData: number;
}
