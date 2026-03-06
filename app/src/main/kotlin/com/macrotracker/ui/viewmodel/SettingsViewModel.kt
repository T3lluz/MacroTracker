package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val geminiApiKey: StateFlow<String> = settings.geminiApiKey

    private val _healthConnectConnected = MutableStateFlow(false)
    val healthConnectConnected: StateFlow<Boolean> = _healthConnectConnected

    private val _weatherConnected = MutableStateFlow(false)
    val weatherConnected: StateFlow<Boolean> = _weatherConnected

    private val _calendarConnected = MutableStateFlow(false)
    val calendarConnected: StateFlow<Boolean> = _calendarConnected

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
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            // Check calendar (read permission granted)
            _calendarConnected.value = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
