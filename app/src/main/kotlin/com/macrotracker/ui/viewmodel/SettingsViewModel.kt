package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.calendar.CalendarInfo
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    companion object {
        private const val CALENDAR_PREFS = "calendar_settings"
        private const val KEY_SELECTED_CALENDARS = "selected_calendar_ids"
    }

    val geminiApiKey: StateFlow<String> = settings.geminiApiKey

    private val _healthConnectConnected = MutableStateFlow(false)
    val healthConnectConnected: StateFlow<Boolean> = _healthConnectConnected

    private val _weatherConnected = MutableStateFlow(false)
    val weatherConnected: StateFlow<Boolean> = _weatherConnected

    private val _calendarConnected = MutableStateFlow(false)
    val calendarConnected: StateFlow<Boolean> = _calendarConnected

    private val _availableCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<CalendarInfo>> = _availableCalendars

    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCalendarIds: StateFlow<Set<Long>> = _selectedCalendarIds

    // Master toggles
    val masterHealthConnectEnabled: StateFlow<Boolean> = settings.masterHealthConnectEnabled
    val masterWeatherEnabled: StateFlow<Boolean> = settings.weatherEnabled
    val masterCalendarEnabled: StateFlow<Boolean> = settings.calendarEnabled

    // Health Connect Metrics from SettingsRepository
    val heartRateEnabled: StateFlow<Boolean> = settings.heartRateEnabled
    val restingHeartRateEnabled: StateFlow<Boolean> = settings.restingHeartRateEnabled
    val oxygenSaturationEnabled: StateFlow<Boolean> = settings.oxygenSaturationEnabled
    val respiratoryRateEnabled: StateFlow<Boolean> = settings.respiratoryRateEnabled
    val stepsEnabled: StateFlow<Boolean> = settings.stepsEnabled
    val distanceEnabled: StateFlow<Boolean> = settings.distanceEnabled
    val floorsClimbedEnabled: StateFlow<Boolean> = settings.floorsClimbedEnabled
    val activeCaloriesEnabled: StateFlow<Boolean> = settings.activeCaloriesEnabled

    init {
        _selectedCalendarIds.value = getSelectedCalendarIds()
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

    fun setMasterHealthConnectEnabled(enabled: Boolean) {
        settings.setMasterHealthConnectEnabled(enabled)
        if (!enabled) {
            viewModelScope.launch {
                try {
                    // Try to actually revoke permissions if user disables the connection
                    healthConnectRepository.revokeAllPermissions()
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to revoke HC permissions", e)
                }
            }
        }
        refreshConnectionStatus()
    }

    fun setMasterWeatherEnabled(enabled: Boolean) {
        settings.setWeatherEnabled(enabled)
        refreshConnectionStatus()
    }

    fun setMasterCalendarEnabled(enabled: Boolean) {
        settings.setCalendarEnabled(enabled)
        if (!enabled) {
            // Clear selected calendars
            _selectedCalendarIds.value = emptySet()
            saveSelectedCalendarIds(emptySet())
        }
        refreshConnectionStatus()
    }

    fun setMetricEnabled(metric: String, enabled: Boolean) {
        settings.setMetricEnabled(metric, enabled)
        refreshConnectionStatus()
    }

    fun toggleCalendar(id: Long) {
        val current = _selectedCalendarIds.value
        val newSelected = current.toMutableSet()
        if (newSelected.contains(id)) {
            newSelected.remove(id)
        } else {
            newSelected.add(id)
        }
        _selectedCalendarIds.value = newSelected
        saveSelectedCalendarIds(newSelected)
    }

    fun saveApiKey(key: String) {
        settings.saveGeminiApiKey(key)
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            // Check Health Connect
            val permissions = mutableSetOf<String>()
            if (heartRateEnabled.value) permissions.add("android.permission.health.READ_HEART_RATE")
            if (restingHeartRateEnabled.value) permissions.add("android.permission.health.READ_RESTING_HEART_RATE")
            if (oxygenSaturationEnabled.value) permissions.add("android.permission.health.READ_OXYGEN_SATURATION")
            if (respiratoryRateEnabled.value) permissions.add("android.permission.health.READ_RESPIRATORY_RATE")
            if (stepsEnabled.value) permissions.add("android.permission.health.READ_STEPS")
            if (distanceEnabled.value) permissions.add("android.permission.health.READ_DISTANCE")
            if (floorsClimbedEnabled.value) permissions.add("android.permission.health.READ_FLOORS_CLIMBED")
            if (activeCaloriesEnabled.value) permissions.add("android.permission.health.READ_ACTIVE_CALORIES_BURNED")

            _healthConnectConnected.value = settings.masterHealthConnectEnabled.value && 
                healthConnectRepository.isAvailable() &&
                healthConnectRepository.hasPermissions(permissions)

            // Check weather (location permission granted)
            _weatherConnected.value = settings.weatherEnabled.value && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            // Check calendar (read permission granted)
            val calPerm = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            _calendarConnected.value = settings.calendarEnabled.value && calPerm
            
            if (calPerm) {
                _availableCalendars.value = calendarRepository.getAvailableCalendars()
            }
        }
    }
}