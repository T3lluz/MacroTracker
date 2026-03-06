package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class HealthConnectUiState {
    data object NotAvailable : HealthConnectUiState()
    data object PermissionRequired : HealthConnectUiState()
    data object Loading : HealthConnectUiState()
    data class Success(val stats: HealthStats) : HealthConnectUiState()
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

    val healthConnectPermissions = HealthConnectRepository.PERMISSIONS

    fun loadData() {
        viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
            _weekHistory.value = repository.getDailySummariesRange(7)
        }
    }

    fun loadHealthConnect(permissionsGranted: Boolean = false) {
        viewModelScope.launch {
            if (!healthConnectRepository.isAvailable()) {
                Log.w(TAG, "Health Connect not available")
                _healthConnectState.value = HealthConnectUiState.NotAvailable
                return@launch
            }

            val hasPerms = permissionsGranted || healthConnectRepository.hasAllPermissions()
            Log.d(TAG, "loadHealthConnect: permissionsGranted=$permissionsGranted, hasPerms=$hasPerms")
            if (!hasPerms) {
                _healthConnectState.value = HealthConnectUiState.PermissionRequired
                return@launch
            }

            _healthConnectState.value = HealthConnectUiState.Loading
            try {
                val stats = healthConnectRepository.readTodayStats()
                Log.d(TAG, "Health stats loaded: $stats")
                _healthConnectState.value = HealthConnectUiState.Success(stats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read health data", e)
                _healthConnectState.value = HealthConnectUiState.Error(
                    e.message ?: "Failed to read health data",
                )
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
