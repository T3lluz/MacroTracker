package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
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
        prefs.edit()
            .putStringSet(KEY_SELECTED_CALENDARS, ids.map { it.toString() }.toSet())
            .apply()
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
            _healthConnectConnected.value = healthConnectRepository.isAvailable() &&
                healthConnectRepository.hasAllPermissions()

            // Check weather (location permission granted)
            _weatherConnected.value = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            // Check calendar (read permission granted)
            val calPerm = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            _calendarConnected.value = calPerm
            
            if (calPerm) {
                _availableCalendars.value = calendarRepository.getAvailableCalendars()
            }
        }
    }
}
