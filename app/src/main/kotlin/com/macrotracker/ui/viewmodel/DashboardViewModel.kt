package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.ui.components.HealthMetricUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthConnectRepository: HealthConnectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _heartRateState = MutableStateFlow(HealthMetricUiState())
    val heartRateState: StateFlow<HealthMetricUiState> = _heartRateState

    private val _restingHeartRateState = MutableStateFlow(HealthMetricUiState())
    val restingHeartRateState: StateFlow<HealthMetricUiState> = _restingHeartRateState

    private val _oxygenSaturationState = MutableStateFlow(HealthMetricUiState())
    val oxygenSaturationState: StateFlow<HealthMetricUiState> = _oxygenSaturationState

    private val _respiratoryRateState = MutableStateFlow(HealthMetricUiState())
    val respiratoryRateState: StateFlow<HealthMetricUiState> = _respiratoryRateState

    private val _stepsState = MutableStateFlow(HealthMetricUiState())
    val stepsState: StateFlow<HealthMetricUiState> = _stepsState

    private val _distanceState = MutableStateFlow(HealthMetricUiState())
    val distanceState: StateFlow<HealthMetricUiState> = _distanceState

    private val _floorsClimbedState = MutableStateFlow(HealthMetricUiState())
    val floorsClimbedState: StateFlow<HealthMetricUiState> = _floorsClimbedState

    private val _activeCaloriesState = MutableStateFlow(HealthMetricUiState())
    val activeCaloriesState: StateFlow<HealthMetricUiState> = _activeCaloriesState

    init {
        loadData()
    }

    fun loadData() {
        // Heart Rate
        viewModelScope.launch {
            combine(settingsRepository.heartRateEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getLatestHeartRate()
                    val yesterday = healthConnectRepository.getLatestHeartRate(yesterday = true)
                    _heartRateState.value = HealthMetricUiState(
                        value = today?.toString() ?: "–",
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _heartRateState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Resting Heart Rate
        viewModelScope.launch {
            combine(settingsRepository.restingHeartRateEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getLatestRestingHeartRate()
                    val yesterday = healthConnectRepository.getLatestRestingHeartRate(yesterday = true)
                    _restingHeartRateState.value = HealthMetricUiState(
                        value = today?.toString() ?: "–",
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _restingHeartRateState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Oxygen Saturation
        viewModelScope.launch {
            combine(settingsRepository.oxygenSaturationEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getLatestOxygenSaturation()
                    val yesterday = healthConnectRepository.getLatestOxygenSaturation(yesterday = true)
                    val formatted = today?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "–"
                    _oxygenSaturationState.value = HealthMetricUiState(
                        value = formatted,
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _oxygenSaturationState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Respiratory Rate
        viewModelScope.launch {
            combine(settingsRepository.respiratoryRateEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getLatestRespiratoryRate()
                    val yesterday = healthConnectRepository.getLatestRespiratoryRate(yesterday = true)
                    val formatted = today?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "–"
                    _respiratoryRateState.value = HealthMetricUiState(
                        value = formatted,
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _respiratoryRateState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Steps
        viewModelScope.launch {
            combine(settingsRepository.stepsEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getStepsToday()
                    val yesterday = healthConnectRepository.getStepsYesterday()
                    _stepsState.value = HealthMetricUiState(
                        value = today?.toString() ?: "0",
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _stepsState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Distance
        viewModelScope.launch {
            combine(settingsRepository.distanceEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getDistanceToday()
                    val yesterday = healthConnectRepository.getDistanceYesterday()
                    val formatted = today?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "0"
                    _distanceState.value = HealthMetricUiState(
                        value = formatted,
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _distanceState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Floors Climbed
        viewModelScope.launch {
            combine(settingsRepository.floorsClimbedEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getFloorsClimbedToday()
                    val yesterday = healthConnectRepository.getFloorsClimbedYesterday()
                    val formatted = today?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "0"
                    _floorsClimbedState.value = HealthMetricUiState(
                        value = formatted,
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _floorsClimbedState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }

        // Active Calories
        viewModelScope.launch {
            combine(settingsRepository.activeCaloriesEnabled, settingsRepository.masterHealthConnectEnabled) { metricEnabled, masterEnabled ->
                metricEnabled && masterEnabled
            }.collect { enabled ->
                if (enabled) {
                    val today = healthConnectRepository.getActiveCaloriesToday()
                    val yesterday = healthConnectRepository.getActiveCaloriesYesterday()
                    val formatted = today?.toInt()?.toString() ?: "0"
                    _activeCaloriesState.value = HealthMetricUiState(
                        value = formatted,
                        today = today,
                        yesterday = yesterday,
                        isEnabled = true
                    )
                } else {
                    _activeCaloriesState.value = HealthMetricUiState(isEnabled = false)
                }
            }
        }
    }
}
