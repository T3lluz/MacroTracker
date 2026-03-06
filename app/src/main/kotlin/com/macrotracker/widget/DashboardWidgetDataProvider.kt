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
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.Room
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.GoalsEntity
import com.macrotracker.data.local.MacroDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Reads all data sources directly for the widget (no Hilt available).
 */
object DashboardWidgetDataProvider {

    private const val TAG = "DashWidgetData"
    private const val WEATHER_PREFS = "daily_dash_weather_cache"
    private const val WIDGET_PREFS = "daily_dash_widget"
    private const val AI_INSIGHT_KEY = "ai_insight"
    private const val AI_INSIGHT_TS_KEY = "ai_insight_ts"
    private const val AI_INSIGHT_TTL = 60 * 60 * 1000L // 1 hour

    suspend fun loadData(context: Context): DashboardWidgetData {
        val macros = loadMacros(context)
        val health = loadHealth(context)
        val weather = loadWeather(context)
        val calendar = loadCalendar(context)

        val merged = macros.copy(
            steps = health.steps,
            stepsGoal = health.stepsGoal,
            avgHeartRate = health.avgHeartRate,
            sleepMinutes = health.sleepMinutes,
            activeCaloriesBurned = health.activeCaloriesBurned,
            hasHealthData = health.hasHealthData,
            weatherTemp = weather.weatherTemp,
            weatherHigh = weather.weatherHigh,
            weatherLow = weather.weatherLow,
            weatherIcon = weather.weatherIcon,
            weatherDescription = weather.weatherDescription,
            weatherLocation = weather.weatherLocation,
            hasWeatherData = weather.hasWeatherData,
            nextEventTitle = calendar.nextEventTitle,
            nextEventTime = calendar.nextEventTime,
            nextEventRelativeDay = calendar.nextEventRelativeDay,
            eventsToday = calendar.eventsToday,
            hasCalendarData = calendar.hasCalendarData,
        )

        val insight = loadAiInsight(context, merged)
        return merged.copy(aiInsight = insight)
    }

    private suspend fun loadMacros(context: Context): DashboardWidgetData {
        return try {
            val db = Room.databaseBuilder(
                context.applicationContext,
                MacroDatabase::class.java,
                "macro_tracker.db",
            ).build()

            val dao = db.macroDao()
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val totalCalories = dao.getTotalCaloriesForDate(today)
            val totalProtein = dao.getTotalProteinForDate(today)
            val goals = dao.getGoals() ?: GoalsEntity()
            val logs = dao.getLogsForDate(today)
            db.close()

            DashboardWidgetData(
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
                mealCount = logs.size,
                lastMeal = logs.firstOrNull()?.foodName,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    private suspend fun loadHealth(context: Context): DashboardWidgetData {
        return try {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                return DashboardWidgetData()
            }
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val needed = setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(SleepSessionRecord::class),
                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            )
            if (!needed.all { it in granted }) {
                return DashboardWidgetData()
            }

            val zone = ZoneId.systemDefault()
            val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
            val now = LocalDateTime.now().atZone(zone).toInstant()
            val todayRange = TimeRangeFilter.between(startOfDay, now)
            val sleepStart = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val sleepRange = TimeRangeFilter.between(sleepStart, now)

            val steps = try {
                client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRangeFilter = todayRange))
                    .records.sumOf { it.count }
            } catch (_: Exception) { 0L }

            val avgHr = try {
                val samples = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = todayRange))
                    .records.flatMap { it.samples }
                if (samples.isNotEmpty()) samples.map { it.beatsPerMinute }.average().toLong() else 0L
            } catch (_: Exception) { 0L }

            val sleepMin = try {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = sleepRange))
                    .records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            } catch (_: Exception) { 0L }

            val activeCal = try {
                client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = todayRange))
                    .records.sumOf { it.energy.inKilocalories }
            } catch (_: Exception) { 0.0 }

            DashboardWidgetData(
                steps = steps,
                stepsGoal = 10_000,
                avgHeartRate = avgHr,
                sleepMinutes = sleepMin,
                activeCaloriesBurned = activeCal,
                hasHealthData = steps > 0 || avgHr > 0 || sleepMin > 0 || activeCal > 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load health: ${e.message}", e)
            DashboardWidgetData()
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
            if (temp != null && icon != null) {
                DashboardWidgetData(
                    weatherTemp = temp,
                    weatherHigh = high,
                    weatherLow = low,
                    weatherIcon = icon,
                    weatherDescription = desc,
                    weatherLocation = location,
                    hasWeatherData = true,
                )
            } else {
                DashboardWidgetData()
            }
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

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startMillis = now.toEpochMilli()
            // Look ahead 7 days for the next event
            val endMillis = LocalDate.now().plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)

            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
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

            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "(No title)"
                    val begin = it.getLong(1)
                    val isAllDay = it.getInt(3) == 1

                    // Count today's events
                    if (begin in todayStart until todayEnd) {
                        todayCount++
                    }

                    // First upcoming event (that hasn't ended yet)
                    if (nextTitle == null) {
                        nextTitle = title
                        val eventDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone)
                        val eventDate = eventDt.toLocalDate()
                        val today = LocalDate.now()

                        nextRelativeDay = when {
                            eventDate == today -> "Today"
                            eventDate == today.plusDays(1) -> "Tomorrow"
                            else -> eventDate.dayOfWeek.name.lowercase()
                                .replaceFirstChar { c -> c.uppercase() }
                        }

                        nextTime = if (isAllDay) {
                            "All day"
                        } else {
                            eventDt.format(DateTimeFormatter.ofPattern("h:mm a"))
                        }
                    }
                }
            }

            DashboardWidgetData(
                nextEventTitle = nextTitle,
                nextEventTime = nextTime,
                nextEventRelativeDay = nextRelativeDay,
                eventsToday = todayCount,
                hasCalendarData = nextTitle != null || todayCount > 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load calendar: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    // ── AI daily insight (Gemini 2.0 Flash free tier, cached 1 h) ────────
    private suspend fun loadAiInsight(context: Context, data: DashboardWidgetData): String? {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(AI_INSIGHT_KEY, null)
        val ts = prefs.getLong(AI_INSIGHT_TS_KEY, 0L)
        if (cached != null && System.currentTimeMillis() - ts < AI_INSIGHT_TTL) return cached

        // Get API key from settings prefs (same store the app uses)
        val settingsPrefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)
        val apiKey = (settingsPrefs.getString("gemini_api_key", null)?.trim() ?: "")
            .ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
        if (apiKey.isBlank()) return cached  // no key → keep stale or null

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildInsightPrompt(data)
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 60)
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
                val text = json
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.take(120)  // safety limit

                if (!text.isNullOrBlank()) {
                    prefs.edit().putString(AI_INSIGHT_KEY, text).putLong(AI_INSIGHT_TS_KEY, System.currentTimeMillis()).apply()
                    text
                } else cached
            } catch (e: Exception) {
                Log.e(TAG, "AI insight failed: ${e.message}")
                cached
            }
        }
    }

    private fun buildInsightPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "Calories: ${d.totalCalories}/${d.calorieGoal} kcal"
        parts += "Protein: ${d.totalProtein}/${d.proteinGoal}g"
        if (d.steps > 0) parts += "Steps: ${d.steps}"
        if (d.sleepMinutes > 0) parts += "Sleep: ${d.sleepMinutes / 60}h ${d.sleepMinutes % 60}m"
        if (d.activeCaloriesBurned > 0) parts += "Active cal burned: ${d.activeCaloriesBurned.toInt()}"
        if (d.hasWeatherData) parts += "Weather: ${d.weatherTemp}° ${d.weatherDescription}"
        if (d.eventsToday > 0) parts += "Events today: ${d.eventsToday}"
        val hour = java.time.LocalTime.now().hour
        val timeOfDay = when { hour < 12 -> "morning"; hour < 17 -> "afternoon"; else -> "evening" }

        return """
            You are a concise wellness assistant for a home-screen widget.
            Given the user's data for today ($timeOfDay):
            ${parts.joinToString("; ")}
            
            Write ONE short motivational/practical tip (max 15 words). 
            Be specific to the data. No quotes, no emoji, no markdown.
        """.trimIndent()
    }
}

