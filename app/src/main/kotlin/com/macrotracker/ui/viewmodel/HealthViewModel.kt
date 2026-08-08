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
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val settingsRepository: SettingsRepository,
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

    private val _healthConnectState = MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Loading)
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState

    private val _healthHistory = MutableStateFlow<List<DailyHealthStats>>(emptyList())
    val healthHistory: StateFlow<List<DailyHealthStats>> = _healthHistory

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _intradayHeartRate = MutableStateFlow<List<HeartRateRecord.Sample>>(emptyList())
    val intradayHeartRate: StateFlow<List<HeartRateRecord.Sample>> = _intradayHeartRate

    private val _detailedSleep = MutableStateFlow<List<SleepSessionRecord>>(emptyList())
    val detailedSleep: StateFlow<List<SleepSessionRecord>> = _detailedSleep

    // Macro trends (formerly History tab)
    private val _macroRangeDays = MutableStateFlow(7)
    val macroRangeDays: StateFlow<Int> = _macroRangeDays

    private val _macroMetric = MutableStateFlow("calories")
    val macroMetric: StateFlow<String> = _macroMetric

    private val _macroHistory = MutableStateFlow<List<DailySummary>>(emptyList())
    val macroHistory: StateFlow<List<DailySummary>> = _macroHistory

    private val _macroSelectedDate = MutableStateFlow(LocalDate.now().format(dateFormat))
    val macroSelectedDate: StateFlow<String> = _macroSelectedDate

    private val _macroSelectedLogs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val macroSelectedLogs: StateFlow<List<MacroLogEntity>> = _macroSelectedLogs

    private val _macroHistoryLoading = MutableStateFlow(false)
    val macroHistoryLoading: StateFlow<Boolean> = _macroHistoryLoading

    val healthConnectPermissions = HealthConnectRepository.PERMISSIONS

    private val _weekStartDay = MutableStateFlow(DayOfWeek.MONDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay

    private val _weeksBack = MutableStateFlow(0)
    val weeksBack: StateFlow<Int> = _weeksBack

    val healthWidgetOrder: StateFlow<String> = settingsRepository.healthWidgetOrder

    private var lastResumeLoadMs = 0L
    private var macrosJob: Job? = null
    private var macroHistoryJob: Job? = null
    private var healthJob: Job? = null
    private var detailJob: Job? = null

    /** Which detail panel (if any) should load heavy intraday datasets. */
    private var detailMetric: DetailMetric = DetailMetric.NONE

    enum class DetailMetric { NONE, HEART_RATE, SLEEP }

    init {
        settingsRepository.masterHealthConnectEnabled.drop(1).onEach {
            loadHealthConnect()
        }.launchIn(viewModelScope)
    }

    /**
     * Call from ON_RESUME. Skips if called within 30 s of the previous load unless [force] is true.
     */
    fun loadDataOnResume(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && lastResumeLoadMs > 0 && now - lastResumeLoadMs < 30_000L) return
        lastResumeLoadMs = now
        loadData()
        loadHealthConnect(silent = true)
    }

    fun updateHealthWidgetOrder(order: String) {
        settingsRepository.updateHealthWidgetOrder(order)
    }

    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        reloadWeekOnly()
    }

    fun nextWeek() {
        if (_weeksBack.value > 0) {
            _weeksBack.value -= 1
            reloadWeekOnly()
        }
    }

    fun previousWeek() {
        if (_weeksBack.value < 2) {
            _weeksBack.value += 1
            reloadWeekOnly()
        }
    }

    /** Week navigation only needs Health Connect history — skip re-reading today's macros. */
    private fun reloadWeekOnly() {
        viewModelScope.launch {
            if (settingsRepository.masterHealthConnectEnabled.value &&
                healthConnectRepository.isAvailable() &&
                healthConnectRepository.hasAllPermissions()
            ) {
                val (start, end) = getWeekRange()
                _healthHistory.value = healthConnectRepository.readHistoryStatsBetween(start, end)
            }
        }
    }

    private fun getWeekRange(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val startDay = _weekStartDay.value
        var start = today.minusWeeks(_weeksBack.value.toLong())
        while (start.dayOfWeek != startDay) {
            start = start.minusDays(1)
        }
        val end = start.plusDays(6)
        return Pair(start, end)
    }

    fun loadData() {
        macrosJob?.cancel()
        macrosJob = viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
        }
        loadMacroHistory()
    }

    fun loadMacroHistory() {
        macroHistoryJob?.cancel()
        macroHistoryJob = viewModelScope.launch {
            val showLoading = _macroHistory.value.isEmpty()
            if (showLoading) _macroHistoryLoading.value = true
            try {
                _macroHistory.value = repository.getDailySummariesRange(_macroRangeDays.value)
                _macroSelectedLogs.value = repository.getLogsForDate(_macroSelectedDate.value)
            } catch (_: Exception) { }
            _macroHistoryLoading.value = false
        }
    }

    fun setMacroRangeDays(days: Int) {
        if (_macroRangeDays.value == days) return
        _macroRangeDays.value = days
        loadMacroHistory()
    }

    fun setMacroMetric(metric: String) {
        _macroMetric.value = metric
    }

    fun selectMacroDate(date: String) {
        _macroSelectedDate.value = date
        viewModelScope.launch {
            _macroSelectedLogs.value = repository.getLogsForDate(date)
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadDetailedData(date, detailMetric)
    }

    fun setDetailMetric(metric: DetailMetric) {
        if (detailMetric == metric) return
        detailMetric = metric
        if (metric == DetailMetric.NONE) {
            _intradayHeartRate.value = emptyList()
            _detailedSleep.value = emptyList()
            return
        }
        loadDetailedData(_selectedDate.value, metric)
    }

    private fun loadDetailedData(date: LocalDate, metric: DetailMetric) {
        if (metric == DetailMetric.NONE) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            if (!healthConnectRepository.hasAllPermissions()) return@launch
            when (metric) {
                DetailMetric.HEART_RATE -> {
                    _intradayHeartRate.value = healthConnectRepository.readHeartRateIntraday(date)
                }
                DetailMetric.SLEEP -> {
                    _detailedSleep.value = healthConnectRepository.readSleepSessions(date)
                }
                DetailMetric.NONE -> Unit
            }
        }
    }

    fun loadHealthConnect(permissionsGranted: Boolean = false, silent: Boolean = false) {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            if (permissionsGranted) {
                settingsRepository.setMasterHealthConnectEnabled(true)
            }

            if (!healthConnectRepository.isAvailable()) {
                Log.w(TAG, "Health Connect not available")
                _healthConnectState.value = HealthConnectUiState.NotAvailable
                return@launch
            }

            if (!settingsRepository.masterHealthConnectEnabled.value) {
                _healthConnectState.value = HealthConnectUiState.PermissionRequired
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
                coroutineScope {
                    val statsDeferred = async { healthConnectRepository.readTodayStats() }
                    val (start, end) = getWeekRange()
                    val historyDeferred = async { healthConnectRepository.readHistoryStatsBetween(start, end) }
                    val stats = statsDeferred.await()
                    if (stats.steps == 0L && current is HealthConnectUiState.Success && current.stats.steps > 0) {
                        _healthConnectState.value = current.copy(isRefreshing = false, stats = stats)
                    } else {
                        _healthConnectState.value = HealthConnectUiState.Success(stats)
                    }
                    _healthHistory.value = historyDeferred.await()
                }
                // Detail datasets only when the HR/Sleep panel is open.
                loadDetailedData(_selectedDate.value, detailMetric)
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
