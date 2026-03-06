package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherAiRepository
import com.macrotracker.data.remote.WeatherAiResult
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    data class Success(val stats: HealthStats) : HomeHealthState()
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(
        val events: List<CalendarEvent>,
        val upcomingEvents: List<CalendarEvent> = emptyList(),
    ) : CalendarUiState()
    data object PermissionRequired : CalendarUiState()
    data object Unavailable : CalendarUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherAiRepository: WeatherAiRepository,
    private val locationProvider: LocationProvider,
    private val healthConnectRepository: HealthConnectRepository,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
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

    fun loadData() {
        viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
        }
    }

    fun loadHealthConnect() {
        viewModelScope.launch {
            if (!healthConnectRepository.isAvailable() || !healthConnectRepository.hasAllPermissions()) {
                _healthState.value = HomeHealthState.Unavailable
                return@launch
            }

            _healthState.value = HomeHealthState.Loading
            try {
                val stats = healthConnectRepository.readTodayStats()
                _healthState.value = HomeHealthState.Success(stats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read health data for home: ${e.message}", e)
                _healthState.value = HomeHealthState.Unavailable
            }
        }
    }

    fun loadCalendar(hasPermission: Boolean) {
        if (!hasPermission) {
            _calendarState.value = CalendarUiState.PermissionRequired
            return
        }
        _calendarState.value = CalendarUiState.Loading
        viewModelScope.launch {
            try {
                val todayEvents = calendarRepository.readEvents()
                if (todayEvents.isNotEmpty()) {
                    _calendarState.value = CalendarUiState.Success(events = todayEvents)
                } else {
                    // No events today — look ahead 7 days for upcoming events
                    val endOfToday = LocalDate.now().plusDays(1).atStartOfDay()
                    val allEvents = calendarRepository.readEvents(extraDays = 7)
                    val upcoming = allEvents.filter { it.startTime.isAfter(endOfToday) || it.startTime.isEqual(endOfToday) }
                    _calendarState.value = CalendarUiState.Success(
                        events = emptyList(),
                        upcomingEvents = upcoming.take(5),
                    )
                }
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
        _weatherState.value = WeatherUiState.Loading
        viewModelScope.launch {
            try {
                val location = locationProvider.getLocation()
                if (location == null) {
                    _weatherState.value = WeatherUiState.Error("Could not get location")
                    return@launch
                }
                val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
                val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)
                _weatherState.value = WeatherUiState.Success(weather)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Weather error: ${e.message}", e)
                _weatherState.value = WeatherUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadWeatherAiSummary() {
        val current = _weatherState.value
        if (current !is WeatherUiState.Success) return
        // Don't reload if already loaded or loading
        if (current.aiSummary != null || current.aiSummaryLoading) return
        if (!weatherAiRepository.hasApiKey) return

        // Check cache first — return instantly without loading spinner
        val cached = weatherAiRepository.getCachedResult(current.weather.symbolCode)
        if (cached != null) {
            _weatherState.value = current.copy(
                aiSummary = cached.summary,
                aiClothingRecommendation = cached.clothingRecommendation,
                aiSummaryLoading = false,
            )
            return
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
                    // Still show what we can — at least clear the loading state
                    _weatherState.value = latest.copy(
                        aiSummary = "Weather summary unavailable.",
                        aiClothingRecommendation = "",
                        aiSummaryLoading = false,
                    )
                }
            }
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
        }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            repository.deleteLog(id)
            loadData()
        }
    }
}
