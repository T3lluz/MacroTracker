package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.data.calendar.CalendarInfo
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.f1.F1Repository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherAiRepository
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherInfo,
        val aiSummary: String? = null,
        val aiClothingRecommendation: String? = null,
        val aiSummaryLoading: Boolean = false,
        /** True when the location was obtained with coarse (approximate) permission only. */
        val isPrecise: Boolean = true,
        val lastUpdatedAt: Instant? = null,
        val aiSummaryUpdatedAt: Instant? = null,
    ) : WeatherUiState()
    data object PermissionRequired : WeatherUiState()
    /** User granted only approximate (coarse) location — weather works but precision is limited. */
    data object ApproximateLocation : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

sealed class HomeHealthState {
    data object Loading : HomeHealthState()
    data object Unavailable : HomeHealthState()
    data class Success(val stats: HealthStats, val isRefreshing: Boolean = false, val lastUpdatedAt: Instant? = null) : HomeHealthState()
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(
        val events: List<CalendarEvent>,
        val upcomingEvents: List<CalendarEvent> = emptyList(),
        val availableCalendars: List<CalendarInfo> = emptyList(),
        val selectedCalendarIds: Set<Long> = emptySet(),
        val lastUpdatedAt: Instant? = null,
    ) : CalendarUiState()
    data object PermissionRequired : CalendarUiState()
    data object Unavailable : CalendarUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: MacroRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherAiRepository: WeatherAiRepository,
    private val locationProvider: LocationProvider,
    private val healthConnectRepository: HealthConnectRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val f1Repository: F1Repository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
        private const val WEATHER_PREFS = "daily_dash_weather_cache"
        private const val CALENDAR_PREFS = "calendar_settings"
        private const val KEY_SELECTED_CALENDARS = "selected_calendar_ids"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String get() = LocalDate.now().format(dateFormat)

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary

    private val _logs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val logs: StateFlow<List<MacroLogEntity>> = _logs

    private val _logsLastUpdatedAt = MutableStateFlow<Instant?>(null)
    val logsLastUpdatedAt: StateFlow<Instant?> = _logsLastUpdatedAt

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.PermissionRequired)
    val weatherState: StateFlow<WeatherUiState> = _weatherState

    private val _healthState = MutableStateFlow<HomeHealthState>(HomeHealthState.Loading)
    val healthState: StateFlow<HomeHealthState> = _healthState

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState

    private val _f1State = MutableStateFlow<F1UiState>(F1UiState.Loading)
    val f1State: StateFlow<F1UiState> = _f1State

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val hasAiApiKey: Boolean get() = weatherAiRepository.hasApiKey

    private var f1DataJob: Job? = null

    val homeWidgetOrder: StateFlow<String> = settingsRepository.homeWidgetOrder

    init {
        // drop(1) skips the initial replay emission — these only need to react
        // to user-driven *changes* in settings, not fire on every cold start.
        // refreshAll() called from the screen covers the initial load.
        settingsRepository.masterHealthConnectEnabled.drop(1).onEach {
            loadHealthConnect(silent = true)
        }.launchIn(viewModelScope)

        settingsRepository.weatherEnabled.drop(1).onEach { enabled ->
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission) {
                loadWeather(true)
            } else if (!enabled) {
                loadWeather(false)
            }
        }.launchIn(viewModelScope)

        settingsRepository.calendarEnabled.drop(1).onEach { enabled ->
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission) {
                loadCalendar(true)
            } else if (!enabled) {
                loadCalendar(false)
            }
        }.launchIn(viewModelScope)
    }

    fun updateHomeWidgetOrder(order: String) {
        settingsRepository.updateHomeWidgetOrder(order)
    }

    fun setMasterWeatherEnabled(enabled: Boolean) {
        settingsRepository.setWeatherEnabled(enabled)
    }

    fun setMasterCalendarEnabled(enabled: Boolean) {
        settingsRepository.setCalendarEnabled(enabled)
    }

    private fun getSelectedCalendarIds(): Set<Long> {
        val prefs = appContext.getSharedPreferences(CALENDAR_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_CALENDARS, null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    private fun saveSelectedCalendarIds(ids: Set<Long>) {
        val prefs = appContext.getSharedPreferences(CALENDAR_PREFS, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(KEY_SELECTED_CALENDARS, ids.map { it.toString() }.toSet()) }
    }

    fun loadData() {
        viewModelScope.launch {
            val newSummary = repository.getDailySummary(today)
            val newLogs = repository.getLogsForDate(today)

            // Only advance the "last updated" timestamp when the data actually changed
            val prevSummary = _summary.value
            val dataChanged = prevSummary == null ||
                prevSummary.totalCalories != newSummary.totalCalories ||
                prevSummary.totalProtein != newSummary.totalProtein ||
                _logs.value.size != newLogs.size

            _summary.value = newSummary
            _logs.value = newLogs
            if (dataChanged) {
                _logsLastUpdatedAt.value = Instant.now()
            }
        }
    }

    /** Epoch-ms of the last completed refreshAll, used to throttle ON_RESUME calls. */
    private var lastRefreshMs = 0L

    fun refreshAll(
        hasLocationPermission: Boolean,
        hasCalendarPermission: Boolean,
        force: Boolean = false,
    ) {
        if (_isRefreshing.value) return
        // Skip automatic ON_RESUME refreshes that fire within 30 s of the last one.
        // This prevents heavy work from running during every tab-switch transition.
        val now = System.currentTimeMillis()
        if (!force && lastRefreshMs > 0 && now - lastRefreshMs < 30_000L) return

        viewModelScope.launch {
            _isRefreshing.value = true
            loadData()
            loadWeather(hasLocationPermission, forceRefresh = force)
            loadHealthConnect(silent = true)
            loadCalendar(hasCalendarPermission)
            loadF1Data(forceRefresh = force)
            lastRefreshMs = System.currentTimeMillis()
            _isRefreshing.value = false
        }
    }

    fun loadF1Data(forceRefresh: Boolean = false) {
        if (!forceRefresh && f1DataJob?.isActive == true) return
        f1DataJob?.cancel()

        f1DataJob = viewModelScope.launch {
            val cached = f1Repository.getCachedF1Data()
            val cachedAt = f1Repository.lastFetchTimeMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            if (cached != null) {
                _f1State.value = F1UiState.Success(cached, lastUpdatedAt = cachedAt)
            } else {
                _f1State.value = F1UiState.Loading
            }

            f1Repository.getOverallF1Data(forceRefresh)
                .onSuccess { f1Data ->
                    val fetchedAt = f1Repository.lastFetchTimeMs
                        .takeIf { it > 0 }?.let(Instant::ofEpochMilli)
                    _f1State.value = F1UiState.Success(f1Data, lastUpdatedAt = fetchedAt)
                    WidgetUpdater.updateAllWidgets(appContext)
                }
                .onFailure { error ->
                    if (cached == null) {
                        _f1State.value = F1UiState.Error(error.message ?: "Unknown error")
                    }
                }
        }
    }

    fun loadHealthConnect(silent: Boolean = false) {
        viewModelScope.launch {
            if (!settingsRepository.masterHealthConnectEnabled.value || !healthConnectRepository.isAvailable() || !healthConnectRepository.hasAllPermissions()) {
                _healthState.value = HomeHealthState.Unavailable
                return@launch
            }

            val current = _healthState.value
            if (!silent || current !is HomeHealthState.Success) {
                _healthState.value = HomeHealthState.Loading
            } else {
                _healthState.value = current.copy(isRefreshing = true)
            }

            try {
                val stats = healthConnectRepository.readTodayStats()
                if (stats.steps == 0L && current is HomeHealthState.Success && current.stats.steps > 0) {
                     Log.w(TAG, "Health Connect returned 0 steps, keeping previous value to avoid flicker")
                     _healthState.value = current.copy(isRefreshing = false) // preserve lastUpdatedAt
                } else {
                    _healthState.value = HomeHealthState.Success(stats, lastUpdatedAt = Instant.now())
                }
                WidgetUpdater.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read health data for home: ${e.message}", e)
                if (current !is HomeHealthState.Success) {
                    _healthState.value = HomeHealthState.Unavailable
                } else {
                    _healthState.value = current.copy(isRefreshing = false)
                }
            }
        }
    }

    fun loadCalendar(hasPermission: Boolean) {
        if (!settingsRepository.calendarEnabled.value) {
            _calendarState.value = CalendarUiState.Unavailable
            return
        }
        if (!hasPermission) {
             _calendarState.value = CalendarUiState.PermissionRequired
            return
        }
        viewModelScope.launch {
            try {
                val available = calendarRepository.getAvailableCalendars()
                val selected = getSelectedCalendarIds().let {
                    if (it.isEmpty() && available.isNotEmpty()) {
                        val all = available.map { cal -> cal.id }.toSet()
                        saveSelectedCalendarIds(all)
                        all
                    } else it
                }

                val allEvents = calendarRepository.readEvents(extraDays = 14, calendarIds = selected)
                val now = LocalDateTime.now()
                val endOfToday = LocalDate.now().plusDays(1).atStartOfDay()

                val todayEvents = allEvents.filter {
                    (it.startTime.isBefore(endOfToday) && it.endTime.isAfter(now)) ||
                    it.startTime.toLocalDate() == LocalDate.now()
                }

                val upcoming = allEvents.filter {
                    it.startTime.isAfter(now) && it.startTime.toLocalDate() != LocalDate.now()
                }

                _calendarState.value = CalendarUiState.Success(
                    events = todayEvents,
                    upcomingEvents = upcoming.take(10),
                    availableCalendars = available,
                    selectedCalendarIds = selected,
                    lastUpdatedAt = Instant.now(),
                )

                WidgetUpdater.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Calendar error: ${e.message}", e)
                _calendarState.value = CalendarUiState.Unavailable
            }
        }
    }

    fun loadWeather(hasPermission: Boolean, forceRefresh: Boolean = false) {
        if (!settingsRepository.weatherEnabled.value) {
            _weatherState.value = WeatherUiState.PermissionRequired
            appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE).edit { clear() }
            viewModelScope.launch { WidgetUpdater.updateAllWidgets(appContext) }
            return
        }
        if (!hasPermission) {
            _weatherState.value = WeatherUiState.PermissionRequired
            return
        }

        // Detect whether the user granted precise (fine) or approximate (coarse) location
        val hasPreciseLocation = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        viewModelScope.launch {
            try {
                _weatherState.value = WeatherUiState.Loading
                val location = locationProvider.getLocation()
                if (location == null) {
                    if (_weatherState.value !is WeatherUiState.Success) {
                        _weatherState.value = WeatherUiState.Error("Could not get location")
                    }
                    return@launch
                }
                val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
                if (forceRefresh) weatherRepository.clearCache()
                val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)

                val current = _weatherState.value
                val fetchedAt = Instant.now()
                if (current is WeatherUiState.Success) {
                    _weatherState.value = current.copy(weather = weather, isPrecise = hasPreciseLocation, lastUpdatedAt = fetchedAt)
                    if (current.aiSummary == null) {
                        loadWeatherAiSummary()
                    }
                } else {
                    _weatherState.value = WeatherUiState.Success(weather, isPrecise = hasPreciseLocation, lastUpdatedAt = fetchedAt)
                    loadWeatherAiSummary()
                }

                cacheWeatherForWidget(weather)
                WidgetUpdater.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Weather error: ${e.message}", e)
                if (_weatherState.value !is WeatherUiState.Success) {
                    _weatherState.value = WeatherUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun loadWeatherAiSummary(force: Boolean = false) {
        val current = _weatherState.value
        if (current !is WeatherUiState.Success) return
        if (!force && (current.aiSummary != null || current.aiSummaryLoading)) return
        if (!weatherAiRepository.hasApiKey) return

        if (!force) {
            val cached = weatherAiRepository.getCachedResult(current.weather.symbolCode)
            if (cached != null) {
                // Use the real generation timestamp from the repository cache
                val aiGeneratedAt = weatherAiRepository.aiLastFetchTimeMs
                    .takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
                _weatherState.value = current.copy(
                    aiSummary = cached.summary,
                    aiClothingRecommendation = cached.clothingRecommendation,
                    aiSummaryLoading = false,
                    aiSummaryUpdatedAt = aiGeneratedAt,
                )
                return
            }
        }

        _weatherState.value = current.copy(aiSummaryLoading = true)
        viewModelScope.launch {
            try {
                val result = weatherAiRepository.generateWeatherSummary(current.weather)
                // Stamp with the repository's cached time — set inside cacheResult() when Gemini responded
                val aiGeneratedAt = weatherAiRepository.aiLastFetchTimeMs
                    .takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
                val latest = _weatherState.value
                if (latest is WeatherUiState.Success) {
                    _weatherState.value = latest.copy(
                        aiSummary = result?.summary ?: "Could not generate summary.",
                        aiClothingRecommendation = result?.clothingRecommendation ?: "",
                        aiSummaryLoading = false,
                        aiSummaryUpdatedAt = aiGeneratedAt,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI weather summary error: ${e.message}", e)
                val latest = _weatherState.value
                if (latest is WeatherUiState.Success) {
                    _weatherState.value = latest.copy(
                        aiSummary = "Weather summary unavailable.",
                        aiClothingRecommendation = "",
                        aiSummaryLoading = false,
                    )
                }
            }
        }
    }

    private fun cacheWeatherForWidget(weather: WeatherInfo) {
        try {
            val prefs = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val todayForecast = weather.dailyForecasts.firstOrNull()

            // Build hourly forecast string: "3 PM|clearsky|18|0|12 m/s|Short desc|2024-06-12"
            // Take next 72 hours (scrollable list)
            val hourlyStr = weather.hourlyForecasts.take(72).joinToString("|") { h ->
                val displayHour = h.time
                val temp = h.temperature.toInt().toString()
                val wind = "${h.windSpeed.toInt()} m/s"
                val desc = h.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                val dateStr = h.dateStr ?: ""
                val precip = if (h.precipitation != null && h.precipitation > 0) "${h.precipitation}mm" else ""

                "$displayHour|${h.symbolCode}|$temp|0|$wind|$desc|$dateStr|$precip"
            }

            prefs.edit {
                putString("temp", weather.temperature.toInt().toString())
                putString("symbol_code", weather.symbolCode)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache weather for widget: ${e.message}")
        }
    }

    fun addLog(foodName: String, calories: Int, protein: Int) {
        viewModelScope.launch {
            val log = MacroLogEntity(
                id = System.currentTimeMillis().toString(),
                date = today,
                foodName = foodName.ifBlank { "Quick Add" },
                calories = calories,
                protein = protein,
            )
            repository.saveLog(log)
            loadData()
            WidgetUpdater.updateAllWidgets(appContext)
        }
    }
}
