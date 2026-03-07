package com.macrotracker.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.data.calendar.CalendarInfo
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherAiRepository
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherInfo,
        val aiSummary: String? = null,
        val aiClothingRecommendation: String? = null,
        val aiSummaryLoading: Boolean = false,
    ) : WeatherUiState()
    data object PermissionRequired : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

sealed class HomeHealthState {
    data object Loading : HomeHealthState()
    data object Unavailable : HomeHealthState()
    data class Success(val stats: HealthStats, val isRefreshing: Boolean = false) : HomeHealthState()
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(
        val events: List<CalendarEvent>,
        val upcomingEvents: List<CalendarEvent> = emptyList(),
        val availableCalendars: List<CalendarInfo> = emptyList(),
        val selectedCalendarIds: Set<Long> = emptySet(),
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

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.PermissionRequired)
    val weatherState: StateFlow<WeatherUiState> = _weatherState

    private val _healthState = MutableStateFlow<HomeHealthState>(HomeHealthState.Loading)
    val healthState: StateFlow<HomeHealthState> = _healthState

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private fun getSelectedCalendarIds(): Set<Long> {
        val prefs = appContext.getSharedPreferences(CALENDAR_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_CALENDARS, null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    private fun saveSelectedCalendarIds(ids: Set<Long>) {
        val prefs = appContext.getSharedPreferences(CALENDAR_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SELECTED_CALENDARS, ids.map { it.toString() }.toSet())
            .apply()
    }

    fun loadData() {
        viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
        }
    }

    fun refreshAll(hasLocationPermission: Boolean, hasCalendarPermission: Boolean, hasHealthPermission: Boolean) {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadData()
            loadWeather(hasLocationPermission)
            loadHealthConnect(silent = true)
            loadCalendar(hasCalendarPermission)
            _isRefreshing.value = false
        }
    }

    fun loadHealthConnect(silent: Boolean = false) {
        viewModelScope.launch {
            if (!healthConnectRepository.isAvailable() || !healthConnectRepository.hasAllPermissions()) {
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
                // Ensure we don't overwrite with 0 if we already had data and something went wrong silently
                if (stats.steps == 0L && current is HomeHealthState.Success && current.stats.steps > 0) {
                     Log.w(TAG, "Health Connect returned 0 steps, keeping previous value to avoid flicker")
                     _healthState.value = current.copy(isRefreshing = false)
                } else {
                    _healthState.value = HomeHealthState.Success(stats)
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
                    selectedCalendarIds = selected
                )
                
                WidgetUpdater.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Calendar error: ${e.message}", e)
                _calendarState.value = CalendarUiState.Unavailable
            }
        }
    }

    fun loadWeather(hasPermission: Boolean) {
        if (!hasPermission) {
            _weatherState.value = WeatherUiState.PermissionRequired
            return
        }
        viewModelScope.launch {
            try {
                val location = locationProvider.getLocation()
                if (location == null) {
                    if (_weatherState.value !is WeatherUiState.Success) {
                        _weatherState.value = WeatherUiState.Error("Could not get location")
                    }
                    return@launch
                }
                val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
                val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)
                
                val current = _weatherState.value
                if (current is WeatherUiState.Success) {
                    _weatherState.value = current.copy(weather = weather)
                    // Optionally refresh AI if weather significantly changed or cache expired
                    if (current.aiSummary == null) {
                        loadWeatherAiSummary()
                    }
                } else {
                    _weatherState.value = WeatherUiState.Success(weather)
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
        
        // If not forcing, check if we already have it or are loading it
        if (!force && (current.aiSummary != null || current.aiSummaryLoading)) return
        if (!weatherAiRepository.hasApiKey) return

        // Check cache first if not forcing
        if (!force) {
            val cached = weatherAiRepository.getCachedResult(current.weather.symbolCode)
            if (cached != null) {
                _weatherState.value = current.copy(
                    aiSummary = cached.summary,
                    aiClothingRecommendation = cached.clothingRecommendation,
                    aiSummaryLoading = false,
                )
                return
            }
        }

        _weatherState.value = current.copy(aiSummaryLoading = true)
        viewModelScope.launch {
            try {
                val result = weatherAiRepository.generateWeatherSummary(current.weather)
                val latest = _weatherState.value
                if (latest is WeatherUiState.Success) {
                    _weatherState.value = latest.copy(
                        aiSummary = result?.summary ?: "Could not generate summary.",
                        aiClothingRecommendation = result?.clothingRecommendation ?: "",
                        aiSummaryLoading = false,
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
            prefs.edit()
                .putString("temp", weather.temperature.toInt().toString())
                .putString("icon", weather.icon)
                .putString("description", weather.description)
                .putString("location", weather.locationName)
                .putString("high", todayForecast?.maxTemp?.toInt()?.toString())
                .putString("low", todayForecast?.minTemp?.toInt()?.toString())
                .apply()
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

    fun deleteLog(id: String) {
        viewModelScope.launch {
            repository.deleteLog(id)
            loadData()
            WidgetUpdater.updateAllWidgets(appContext)
        }
    }
}
