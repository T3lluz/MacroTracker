package com.macrotracker.widget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.Room
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.GoalsEntity
import com.macrotracker.data.local.MacroDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Reads all data sources directly for the widget (no Hilt available).
 *
 * Two loading paths:
 * - [loadData] — fast, for `provideGlance`. Returns memory cache or loads local
 *   data + cached AI from SharedPrefs. **Never** makes network calls.
 * - [refreshNow] — full, for background workers. Loads local data + fresh AI
 *   insights from Gemini in parallel. Updates memory + disk caches.
 */
object DashboardWidgetDataProvider {

    private const val TAG = "DashWidgetData"
    private const val WEATHER_PREFS = "daily_dash_weather_cache"
    private const val WIDGET_PREFS = "daily_dash_widget"
    private const val CALENDAR_PREFS = "calendar_settings"
    private const val KEY_SELECTED_CALENDARS = "selected_calendar_ids"
    private const val AI_INSIGHT_KEY = "ai_insight"
    private const val AI_INSIGHT_TS_KEY = "ai_insight_ts"
    private const val AI_NUTRITION_KEY = "ai_insight_nutrition"
    private const val AI_NUTRITION_TS_KEY = "ai_insight_nutrition_ts"
    private const val AI_HEALTH_KEY = "ai_insight_health"
    private const val AI_HEALTH_TS_KEY = "ai_insight_health_ts"
    private const val AI_WEATHER_KEY = "ai_insight_weather"
    private const val AI_WEATHER_TS_KEY = "ai_insight_weather_ts"
    private const val AI_CALENDAR_KEY = "ai_insight_calendar"
    private const val AI_CALENDAR_TS_KEY = "ai_insight_calendar_ts"
    private const val AI_INSIGHT_TTL = 60 * 60 * 1000L // 1 hour

    /** In-memory cache — avoids redundant local reads across multiple widget renders. */
    private const val MEMORY_TTL = 30_000L // 30 seconds
    @Volatile private var cached: DashboardWidgetData? = null
    @Volatile private var cachedAt: Long = 0L

    /** Singleton Room database — avoids rebuilding on every load call. */
    @Volatile private var dbInstance: MacroDatabase? = null
    private val dbLock = Any()

    private fun getDb(context: Context): MacroDatabase {
        return dbInstance ?: synchronized(dbLock) {
            dbInstance ?: Room.databaseBuilder(
                context.applicationContext,
                MacroDatabase::class.java,
                "macro_tracker.db",
            ).build().also { dbInstance = it }
        }
    }

    /** Invalidate in-memory cache so the next [loadData] re-reads local sources. */
    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    /**
     * Pre-warm the data cache — call this from receivers when a widget is first
     * placed so that [provideGlance] never shows empty/stale data.
     * This is a suspend function that loads local data and caches it.
     */
    suspend fun preWarm(context: Context) {
        invalidate()
        loadData(context)
    }

    /**
     * Fast path — called by every widget's `provideGlance`.
     * Returns in-memory cache if fresh, otherwise reads local data sources
     * (Room, Health Connect, SharedPrefs, Calendar) and reads **cached** AI
     * insights from disk. Never makes network calls — returns almost instantly.
     */
    suspend fun loadData(context: Context): DashboardWidgetData {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < MEMORY_TTL }?.let { return it }

        val merged = loadLocalData(context)

        // Read cached AI insights from SharedPrefs (no network)
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val result = merged.copy(
            aiInsight = prefs.getString(AI_INSIGHT_KEY, null),
            aiInsightNutrition = prefs.getString(AI_NUTRITION_KEY, null),
            aiInsightHealth = prefs.getString(AI_HEALTH_KEY, null),
            aiInsightWeather = prefs.getString(AI_WEATHER_KEY, null),
            aiInsightCalendar = prefs.getString(AI_CALENDAR_KEY, null),
            lastUpdatedAt = now,
        )
        cache(result)
        return result
    }

    /**
     * Full refresh — called by [WidgetRefreshWorker] and [RefreshWidgetAction].
     * Loads local data, then fetches fresh AI insights from Gemini **in parallel**.
     * Updates both memory and disk caches.
     */
    suspend fun refreshNow(context: Context): DashboardWidgetData {
        fetchLiveWeather(context)
        val merged = loadLocalData(context)

        // Fetch all AI insights in parallel (each has its own TTL check)
        val results = coroutineScope {
            val insight = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_INSIGHT_KEY, AI_INSIGHT_TS_KEY, buildInsightPrompt(merged))
            }
            val nutrition = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_NUTRITION_KEY, AI_NUTRITION_TS_KEY, buildNutritionPrompt(merged))
            }
            val health = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_HEALTH_KEY, AI_HEALTH_TS_KEY, buildHealthPrompt(merged))
            }
            val weather = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_WEATHER_KEY, AI_WEATHER_TS_KEY, buildWeatherPrompt(merged))
            }
            val calendar = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_CALENDAR_KEY, AI_CALENDAR_TS_KEY, buildCalendarPrompt(merged))
            }
            listOf(insight.await(), nutrition.await(), health.await(), weather.await(), calendar.await())
        }

        val result = merged.copy(
            aiInsight = results[0],
            aiInsightNutrition = results[1],
            aiInsightHealth = results[2],
            aiInsightWeather = results[3],
            aiInsightCalendar = results[4],
            lastUpdatedAt = System.currentTimeMillis(),
        )
        cache(result)
        return result
    }

    private suspend fun fetchLiveWeather(context: Context) {
        try {
            val hiltEntryPoint = context.widgetEntryPoint()
            val settings = hiltEntryPoint.settingsRepository()
            if (!settings.weatherEnabled.value) return
            
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
            
            val locationProvider = hiltEntryPoint.locationProvider()
            val weatherRepository = hiltEntryPoint.weatherRepository()
            
            val location = locationProvider.getLocation() ?: return
            val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
            val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)
            
            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val todayForecast = weather.dailyForecasts.firstOrNull()
            
            val hourlyStr = weather.hourlyForecasts.take(24).joinToString("|") { h ->
                val displayHour = try {
                    val parts = h.time.split(":")
                    val hr = parts[0].toInt()
                    when {
                        hr == 0  -> "12 AM"
                        hr < 12  -> "$hr AM"
                        hr == 12 -> "12 PM"
                        else     -> "${hr - 12} PM"
                    }
                } catch (_: Exception) { h.time }
                val temp = h.temperature.toInt().toString()
                val wind = "${h.windSpeed.toInt()} km/h"
                val desc = h.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                "$displayHour|${h.iconRes}|$temp|0|$wind|$desc"
            }
            
            prefs.edit().apply {
                putString("temp", weather.temperature.toInt().toString())
                putString("icon", weather.iconRes.toString())
                putString("description", weather.description)
                putString("location", weather.locationName)
                putString("high", todayForecast?.maxTemp?.toInt()?.toString())
                putString("low", todayForecast?.minTemp?.toInt()?.toString())
                putString("feels_like", (weather.feelsLike ?: weather.temperature).toInt().toString())
                putString("humidity", weather.humidity?.toInt()?.toString())
                putString("wind_speed", weather.windSpeed.toInt().toString())
                putString("sunrise", weather.sunrise)
                putString("sunset", weather.sunset)
                putString("hourly_forecast", hourlyStr.ifEmpty { null })
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live weather in widget refresh: ${e.message}")
        }
    }

    private fun cache(data: DashboardWidgetData) {
        cached = data
        cachedAt = System.currentTimeMillis()
    }

    /**
     * Loads macros, health, weather, and calendar from local sources.
     * No network calls — fast enough for `provideGlance`.
     */
    private suspend fun loadLocalData(context: Context): DashboardWidgetData {
        val macros = loadMacros(context)
        val health = loadHealth(context)
        val weather = loadWeather(context)
        val calendar = loadCalendar(context)

        return macros.copy(
            steps = health.steps,
            stepsGoal = health.stepsGoal,
            avgHeartRate = health.avgHeartRate,
            sleepMinutes = health.sleepMinutes,
            activeCaloriesBurned = health.activeCaloriesBurned,
            hasHealthData = health.hasHealthData,
            weatherTemp = weather.weatherTemp,
            dailyMinMax = weather.dailyMinMax,
            weatherIconRes = weather.weatherIconRes,
            weatherDesc = weather.weatherDesc,
            weatherHigh = weather.weatherHigh,
            weatherLow = weather.weatherLow,
            weatherDescription = weather.weatherDescription,
            weatherLocation = weather.weatherLocation,
            weatherFeelsLike = weather.weatherFeelsLike,
            weatherHumidity = weather.weatherHumidity,
            weatherWindSpeed = weather.weatherWindSpeed,
            weatherSunrise = weather.weatherSunrise,
            weatherSunset = weather.weatherSunset,
            hasWeatherData = weather.hasWeatherData,
            hourlyForecast = weather.hourlyForecast,
            nextEventTitle = calendar.nextEventTitle,
            nextEventTime = calendar.nextEventTime,
            nextEventRelativeDay = calendar.nextEventRelativeDay,
            eventsToday = calendar.eventsToday,
            hasCalendarData = calendar.hasCalendarData,
            upcomingEvents = calendar.upcomingEvents,
        )
    }

    private suspend fun loadMacros(context: Context): DashboardWidgetData {
        return try {
            val db = getDb(context)
            val dao = db.macroDao()
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val totalsMap = dao.getTotalsForDates(listOf(today, yesterday)).associateBy { it.date }
            val totalCalories = totalsMap[today]?.totalCalories ?: 0
            val totalProtein  = totalsMap[today]?.totalProtein  ?: 0
            val yesterdayCalories = totalsMap[yesterday]?.totalCalories ?: 0
            val goals = dao.getGoals() ?: GoalsEntity()
            val logs = dao.getLogsForDate(today)

            // Build recent meals list: "Food Name · 420 kcal"
            val recentMeals = logs.take(5).map { "${it.foodName} · ${it.calories} kcal" }

            DashboardWidgetData(
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalFat = 0,
                totalCarbs = 0,
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
                fatGoal = 0,
                carbGoal = 0,
                mealCount = logs.size,
                lastMeal = logs.firstOrNull()?.foodName,
                recentMeals = recentMeals,
                yesterdayCalories = yesterdayCalories,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    private suspend fun loadHealth(context: Context): DashboardWidgetData {
        return try {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                return DashboardWidgetData(hasHealthData = false)
            }
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            // Require at least the steps permission — load any additional metrics
            // for which permissions are granted. hasHealthData = true as long as
            // Health Connect is available and at least steps is permitted, even if
            // today's activity values happen to be zero.
            val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
            if (stepsPermission !in granted) return DashboardWidgetData(hasHealthData = false)
            val heartGranted = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
            val sleepGranted = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
            val calGranted   = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val todayRange = TimeRangeFilter.between(startOfDay, now)
            val sleepStart = LocalDate.now(zone).minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val sleepRange = TimeRangeFilter.between(sleepStart, now)

            val aggMetrics = buildSet {
                add(StepsRecord.COUNT_TOTAL)
                if (heartGranted) add(HeartRateRecord.BPM_AVG)
                if (calGranted)   add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            val response = client.aggregate(
                AggregateRequest(metrics = aggMetrics, timeRangeFilter = todayRange),
            )

            val sleepResponse = if (sleepGranted) client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = sleepRange,
                ),
            ) else null

            val steps    = response[StepsRecord.COUNT_TOTAL] ?: 0L
            val avgHr    = if (heartGranted) response[HeartRateRecord.BPM_AVG] ?: 0L else 0L
            val sleepMin = sleepResponse?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes() ?: 0L
            val activeCal = if (calGranted) response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0 else 0.0

            DashboardWidgetData(
                steps = steps,
                stepsGoal = 10_000,
                avgHeartRate = avgHr,
                sleepMinutes = sleepMin,
                activeCaloriesBurned = activeCal,
                // true as long as permissions are granted — data may legitimately be 0 for today
                hasHealthData = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load health: ${e.message}", e)
            // If the HC SDK is installed, show zero values rather than "Connect" so we don't
            // mislead the user.  The IPC call may have failed transiently; the next refresh
            // will attempt again.
            val sdkOk = try {
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
            } catch (_: Exception) { false }
            DashboardWidgetData(hasHealthData = sdkOk, stepsGoal = 10_000)
        }
    }

    private fun loadWeather(context: Context): DashboardWidgetData {
        return try {
            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val temp = prefs.getString("temp", null)
            val icon = prefs.getString("icon", null)
            val desc = prefs.getString("description", null)
            val location = prefs.getString("location", null)
            val high = prefs.getString("high", null)
            val low = prefs.getString("low", null)
            val feelsLike = prefs.getString("feels_like", null)
            val humidity = prefs.getString("humidity", null)
            val windSpeed = prefs.getString("wind_speed", null)
            val sunrise = prefs.getString("sunrise", null)
            val sunset = prefs.getString("sunset", null)

            // Hourly forecast: stored as pipe-separated segments
            // Format: "3 PM|213000|18|20|12 km/h|Short desc"
            val hourlyRaw = prefs.getString("hourly_forecast", null)
            val hourlyForecast: List<HourlyForecast> = if (!hourlyRaw.isNullOrBlank()) {
                hourlyRaw.split("|").chunked(6).mapNotNull { seg ->
                    if (seg.size < 3) null
                    else HourlyForecast(
                        hour = seg[0],
                        iconRes = seg[1].toIntOrNull() ?: com.macrotracker.R.drawable.ic_weather_sun,
                        temp = seg[2],
                        pop  = seg.getOrNull(3)?.toIntOrNull(),
                        windSpeed = seg.getOrNull(4)?.takeIf { it.isNotBlank() },
                        description = seg.getOrNull(5)?.takeIf { it.isNotBlank() },
                    )
                }
            } else emptyList()

            // Combine high/low
            val minMax = if (high != null && low != null) "$high° / $low°" else null
            
            DashboardWidgetData(
                weatherTemp = temp,
                weatherDesc = desc,
                weatherIconRes = icon?.toIntOrNull() ?: com.macrotracker.R.drawable.ic_weather_sun,
                dailyMinMax = minMax,
                weatherHigh = high,
                weatherLow = low,
                weatherDescription = desc,
                weatherLocation = location,
                weatherFeelsLike = feelsLike,
                weatherHumidity = humidity,
                weatherWindSpeed = windSpeed,
                weatherSunrise = sunrise,
                weatherSunset = sunset,
                hasWeatherData = temp != null,
                hourlyForecast = hourlyForecast,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load weather: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    private fun loadCalendar(context: Context): DashboardWidgetData {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return DashboardWidgetData()
            }

            val calendarPrefs = context.getSharedPreferences(CALENDAR_PREFS, Context.MODE_PRIVATE)
            val selectedIds = calendarPrefs.getStringSet(KEY_SELECTED_CALENDARS, null)
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startMillis = now.toEpochMilli()
            // Look ahead 30 days to collect enough upcoming events
            val endMillis = LocalDate.now().plusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
            )

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)

            var selection: String? = null
            if (selectedIds != null && selectedIds.isNotEmpty()) {
                selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${selectedIds.joinToString(",")})"
            }

            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )

            // Count today's events
            val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            var nextTitle: String? = null
            var nextTime: String? = null
            var nextRelativeDay: String? = null
            var todayCount = 0
            val upcomingEvents = mutableListOf<CalendarEvent>()

            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "(No title)"
                    val begin = it.getLong(1)
                    val isAllDay = it.getInt(3) == 1

                    // Count today's events
                    if (begin in todayStart until todayEnd) {
                        todayCount++
                    }

                    val eventDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone)
                    val eventDate = eventDt.toLocalDate()
                    val today = LocalDate.now()

                    val monthDay = eventDate.format(DateTimeFormatter.ofPattern("MMM d"))
                    val relativeDay = when {
                        eventDate == today -> "Today"
                        eventDate == today.plusDays(1) -> "Tmrw"
                        else -> eventDate.dayOfWeek.name.take(3)
                            .lowercase().replaceFirstChar { c -> c.uppercase() }
                    }

                    val timeStr = if (isAllDay) {
                        "All day"
                    } else {
                        eventDt.format(DateTimeFormatter.ofPattern("h:mm a"))
                    }

                    // Capture the first upcoming event for backward compat fields
                    if (nextTitle == null) {
                        nextTitle = title
                        nextRelativeDay = relativeDay
                        nextTime = timeStr
                    }

                    // Collect up to 10 upcoming events
                    if (upcomingEvents.size < 10) {
                        upcomingEvents += CalendarEvent(
                            title = title,
                            time = timeStr,
                            relativeDay = relativeDay,
                            date = monthDay,
                            isAllDay = isAllDay,
                        )
                    }
                }
            }

            DashboardWidgetData(
                nextEventTitle = nextTitle,
                nextEventTime = nextTime,
                nextEventRelativeDay = nextRelativeDay,
                eventsToday = todayCount,
                hasCalendarData = nextTitle != null || todayCount > 0,
                upcomingEvents = upcomingEvents,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load calendar: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    // ── AI daily insight (Gemini 2.0 Flash free tier, cached 1 h) ────────
    private suspend fun loadAiInsight(
        context: Context,
        data: DashboardWidgetData,
        cacheKey: String,
        cacheTsKey: String,
        prompt: String,
    ): String? {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(cacheKey, null)
        val ts = prefs.getLong(cacheTsKey, 0L)
        if (cached != null && System.currentTimeMillis() - ts < AI_INSIGHT_TTL) return cached

        // Get API key from settings prefs (same store the app uses)
        val settingsPrefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)
        val apiKey = (settingsPrefs.getString("gemini_api_key", null)?.trim() ?: "")
            .ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
        if (apiKey.isBlank()) return cached  // no key → keep stale or null

        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 120)
                        put("temperature", 0.7)
                    })
                }.toString()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: "")
                val rawText = json
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()

                // Trim to the last complete sentence so we never show a truncated fragment.
                val text = rawText?.let { raw ->
                    val truncated = raw.take(220)
                    // Find the last sentence-ending punctuation
                    val lastEnd = truncated.indexOfLast { it == '.' || it == '!' || it == '?' }
                    if (lastEnd >= 0) truncated.substring(0, lastEnd + 1) else truncated
                }

                if (!text.isNullOrBlank()) {
                    prefs.edit()
                        .putString(cacheKey, text)
                        .putLong(cacheTsKey, System.currentTimeMillis())
                        .apply()
                    text
                } else cached
            } catch (e: Exception) {
                Log.e(TAG, "AI insight failed [$cacheKey]: ${e.message}")
                cached
            }
        }
    }

    /** General dashboard insight (nutrition + health overview). */
    private fun buildInsightPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "Calories: ${d.totalCalories}/${d.calorieGoal} kcal"
        parts += "Protein: ${d.totalProtein}/${d.proteinGoal}g"
        if (d.steps > 0) parts += "Steps: ${d.steps}"
        if (d.sleepMinutes > 0) parts += "Sleep: ${d.sleepMinutes / 60}h ${d.sleepMinutes % 60}m"
        if (d.activeCaloriesBurned > 0) parts += "Active cal burned: ${d.activeCaloriesBurned.toInt()}"
        return "Given these health stats: ${parts.joinToString(", ")}. Reply with exactly ONE complete supportive tip sentence (max 20 words, must end with a period)."
    }

    /** Nutrition-focused insight for the Macros widget. */
    private fun buildNutritionPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "Calories: ${d.totalCalories}/${d.calorieGoal} kcal"
        parts += "Protein: ${d.totalProtein}/${d.proteinGoal}g"
        if (d.fatGoal > 0) parts += "Fat: ${d.totalFat}/${d.fatGoal}g"
        if (d.carbGoal > 0) parts += "Carbs: ${d.totalCarbs}/${d.carbGoal}g"
        parts += "Meals logged: ${d.mealCount}"
        return "Today's nutrition: ${parts.joinToString(", ")}. Reply with exactly ONE complete meal or macro tip sentence (max 20 words, must end with a period)."
    }

    /** Health & activity-focused insight for the Health widget. */
    private fun buildHealthPrompt(d: DashboardWidgetData): String {
        if (!d.hasHealthData) return "Reply with exactly ONE complete fitness motivation sentence (max 20 words, must end with a period)."
        val parts = mutableListOf<String>()
        if (d.steps > 0) parts += "Steps: ${d.steps}/${d.stepsGoal}"
        if (d.avgHeartRate > 0) parts += "Avg HR: ${d.avgHeartRate} BPM"
        if (d.sleepMinutes > 0) parts += "Sleep: ${d.sleepMinutes / 60}h ${d.sleepMinutes % 60}m"
        if (d.activeCaloriesBurned > 0) parts += "Active kcal: ${d.activeCaloriesBurned.toInt()}"
        return "Today's activity: ${parts.joinToString(", ")}. Reply with exactly ONE complete fitness or recovery tip sentence (max 20 words, must end with a period)."
    }

    /** Weather-focused insight for the Weather widget. */
    private fun buildWeatherPrompt(d: DashboardWidgetData): String {
        if (d.weatherTemp == null) return "Reply with exactly ONE complete outdoor activity tip sentence (max 20 words, must end with a period)."
        val parts = mutableListOf<String>()
        parts += "Temp: ${d.weatherTemp}°C"
        if (d.weatherDesc != null) parts += "Conditions: ${d.weatherDesc}"
        if (d.dailyMinMax != null) parts += "Range: ${d.dailyMinMax}"
        return "Today's weather: ${parts.joinToString(", ")}. Reply with exactly ONE complete clothing or outdoor activity tip sentence (max 20 words, must end with a period)."
    }

    /** Calendar-focused insight for the Calendar widget. */
    private fun buildCalendarPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "${d.eventsToday} events today"
        if (d.nextEventTitle != null) {
            parts += "Next: \"${d.nextEventTitle}\""
            if (d.nextEventRelativeDay != null) parts += "${d.nextEventRelativeDay}"
            if (d.nextEventTime != null) parts += "at ${d.nextEventTime}"
        }
        return "My schedule: ${parts.joinToString(", ")}. Reply with exactly ONE complete productivity or time-management tip sentence (max 20 words, must end with a period)."
    }
}
