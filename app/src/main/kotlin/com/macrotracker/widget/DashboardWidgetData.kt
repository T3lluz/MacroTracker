package com.macrotracker.widget

/**
 * A single hourly weather forecast slot.
 * @param hour  Display label like "3 PM"
 * @param icon  Weather emoji
 * @param temp  Temperature as a string (e.g. "18")
 * @param pop   Probability of precipitation 0–100 (null if unavailable)
 */
data class HourlyForecast(
    val hour: String,
    val icon: String,
    val temp: String,
    val pop: Int? = null,
    val windSpeed: String? = null,
    val description: String? = null,
)

/**
 * A single upcoming calendar event for widget display.
 */
data class CalendarEvent(
    val title: String,
    val time: String,          // e.g. "3:00 PM" or "All day"
    val relativeDay: String,   // "Today", "Tomorrow", or weekday abbreviation e.g. "Mon"
    val date: String = "",     // e.g. "Mar 12" — shown as the numeric date below the day label
    val isAllDay: Boolean,
)

/**
 * Data snapshot for the DailyDash dashboard widget.
 * Collected from Room, SharedPreferences, calendar, and Health Connect.
 */
data class DashboardWidgetData(
    // Timestamp of when the data was last refreshed
    val lastUpdatedAt: Long = 0L, // epoch millis
    // Macros
    val totalCalories: Int = 0,
    val totalProtein: Int = 0,
    val totalFat: Int = 0,
    val totalCarbs: Int = 0,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val fatGoal: Int = 70,
    val carbGoal: Int = 250,
    val mealCount: Int = 0,
    val lastMeal: String? = null,
    // Recent meal log entries (most recent first, up to 5)
    val recentMeals: List<String> = emptyList(),       // e.g. ["Chicken Salad · 420 kcal", "Oats · 310 kcal"]
    // Yesterday comparison
    val yesterdayCalories: Int = 0,
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
    val weatherFeelsLike: String? = null,
    val weatherHumidity: String? = null,
    val weatherWindSpeed: String? = null,
    val weatherSunrise: String? = null,
    val weatherSunset: String? = null,
    val hasWeatherData: Boolean = false,
    // Hourly forecast (next 6–8 hours, cached from OWM)
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    // Calendar
    val nextEventTitle: String? = null,
    val nextEventTime: String? = null,
    val nextEventRelativeDay: String? = null,
    val eventsToday: Int = 0,
    val hasCalendarData: Boolean = false,
    // Multiple upcoming events (up to 10, within the next 30 days)
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    // AI insights per domain (cached, updated max once per hour)
    val aiInsight: String? = null,           // general / dashboard
    val aiInsightNutrition: String? = null,  // macros widget
    val aiInsightHealth: String? = null,     // health widget
    val aiInsightWeather: String? = null,    // weather widget
    val aiInsightCalendar: String? = null,   // calendar widget
)
