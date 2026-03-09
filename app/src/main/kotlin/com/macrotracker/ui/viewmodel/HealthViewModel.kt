package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class HealthConnectUiState {
    data object NotAvailable : HealthConnectUiState()
    data object PermissionRequired : HealthConnectUiState()
    data object Loading : HealthConnectUiState()
    data class Success(val stats: HealthStats, val isRefreshing: Boolean = false) : HealthConnectUiState()
    data class Error(val message: String) : HealthConnectUiState()
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val healthConnectRepository: HealthConnectRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "HealthViewModel"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String get() = LocalDate.now().format(dateFormat)

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary

    private val _logs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val logs: StateFlow<List<MacroLogEntity>> = _logs

    private val _weekHistory = MutableStateFlow<List<DailySummary>>(emptyList())
    val weekHistory: StateFlow<List<DailySummary>> = _weekHistory

    private val _healthConnectState = MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Loading)
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState

    private val _healthHistory = MutableStateFlow<List<DailyHealthStats>>(emptyList())
    val healthHistory: StateFlow<List<DailyHealthStats>> = _healthHistory

    // Detail States
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _intradayHeartRate = MutableStateFlow<List<HeartRateRecord.Sample>>(emptyList())
    val intradayHeartRate: StateFlow<List<HeartRateRecord.Sample>> = _intradayHeartRate

    private val _detailedSleep = MutableStateFlow<List<SleepSessionRecord>>(emptyList())
    val detailedSleep: StateFlow<List<SleepSessionRecord>> = _detailedSleep

    val healthConnectPermissions = HealthConnectRepository.PERMISSIONS

    private val _weekStartDay = MutableStateFlow(DayOfWeek.MONDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay

    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        loadData()
        loadHealthConnect(silent = true)
    }

    private fun getWeekRange(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val startDay = _weekStartDay.value
        var start = today
        while (start.dayOfWeek != startDay) {
            start = start.minusDays(1)
        }
        val end = start.plusDays(6)
        return Pair(start, end)
    }

    fun loadData() {
        viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
            val (start, end) = getWeekRange()
            _weekHistory.value = repository.getDailySummariesBetween(start, end)
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadDetailedData(date)
    }

    private fun loadDetailedData(date: LocalDate) {
        viewModelScope.launch {
            if (healthConnectRepository.hasAllPermissions()) {
                _intradayHeartRate.value = healthConnectRepository.readHeartRateIntraday(date)
                _detailedSleep.value = healthConnectRepository.readSleepSessions(date)
            }
        }
    }

    fun loadHealthConnect(permissionsGranted: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            if (!healthConnectRepository.isAvailable()) {
                Log.w(TAG, "Health Connect not available")
                _healthConnectState.value = HealthConnectUiState.NotAvailable
                return@launch
            }

            val hasPerms = permissionsGranted || healthConnectRepository.hasAllPermissions()
            if (!hasPerms) {
                _healthConnectState.value = HealthConnectUiState.PermissionRequired
                return@launch
            }

            val current = _healthConnectState.value
            if (!silent || current !is HealthConnectUiState.Success) {
                _healthConnectState.value = HealthConnectUiState.Loading
            } else {
                _healthConnectState.value = current.copy(isRefreshing = true)
            }

            try {
                val stats = healthConnectRepository.readTodayStats()
                // Safeguard against temporary 0 values if we already had a higher count
                if (stats.steps == 0L && current is HealthConnectUiState.Success && current.stats.steps > 0) {
                    _healthConnectState.value = current.copy(isRefreshing = false, stats = stats)
                } else {
                    _healthConnectState.value = HealthConnectUiState.Success(stats)
                }

                val (start, end) = getWeekRange()
                _healthHistory.value = healthConnectRepository.readHistoryStatsBetween(start, end)
                loadDetailedData(_selectedDate.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read health data", e)
                if (current !is HealthConnectUiState.Success) {
                    _healthConnectState.value = HealthConnectUiState.Error(
                        e.message ?: "Failed to read health data",
                    )
                } else {
                    _healthConnectState.value = current.copy(isRefreshing = false)
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
