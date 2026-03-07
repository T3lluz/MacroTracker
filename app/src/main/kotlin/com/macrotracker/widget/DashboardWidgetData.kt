package com.macrotracker.widget

/**
 * Data snapshot for the DailyDash dashboard widget.
 * Collected from Room, SharedPreferences, calendar, and Health Connect.
 */
data class DashboardWidgetData(
    // Macros
    val totalCalories: Int = 0,
    val totalProtein: Int = 0,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val mealCount: Int = 0,
    val lastMeal: String? = null,
    // Health Connect
    val steps: Long = 0,
    val stepsGoal: Long = 10_000,
    val avgHeartRate: Long = 0,
    val sleepMinutes: Long = 0,
    val activeCaloriesBurned: Double = 0.0,
    val hasHealthData: Boolean = false,
    // Weather (from cached SharedPreferences)
    val weatherTemp: String? = null,
    val weatherHigh: String? = null,
    val weatherLow: String? = null,
    val weatherIcon: String? = null,
    val weatherDescription: String? = null,
    val weatherLocation: String? = null,
    val hasWeatherData: Boolean = false,
    // Calendar
    val nextEventTitle: String? = null,
    val nextEventTime: String? = null,
    val nextEventRelativeDay: String? = null,
    val eventsToday: Int = 0,
    val hasCalendarData: Boolean = false,
    // AI insight (cached, updated max once per hour)
    val aiInsight: String? = null,
)


