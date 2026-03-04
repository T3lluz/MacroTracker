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
