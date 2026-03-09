package com.macrotracker.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)
    private val healthPrefs = context.getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    private val _masterHealthConnectEnabled = MutableStateFlow(healthPrefs.getBoolean("master_health_connect_enabled", true))
    val masterHealthConnectEnabled: StateFlow<Boolean> = _masterHealthConnectEnabled

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean("weather_enabled", true))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled

    private val _calendarEnabled = MutableStateFlow(prefs.getBoolean("calendar_enabled", true))
    val calendarEnabled: StateFlow<Boolean> = _calendarEnabled

    private val _heartRateEnabled = MutableStateFlow(healthPrefs.getBoolean("heart_rate_enabled", true))
    val heartRateEnabled: StateFlow<Boolean> = _heartRateEnabled

    private val _restingHeartRateEnabled = MutableStateFlow(healthPrefs.getBoolean("resting_heart_rate_enabled", true))
    val restingHeartRateEnabled: StateFlow<Boolean> = _restingHeartRateEnabled

    private val _heartRateVariabilityEnabled = MutableStateFlow(healthPrefs.getBoolean("heart_rate_variability_enabled", true))
    val heartRateVariabilityEnabled: StateFlow<Boolean> = _heartRateVariabilityEnabled

    private val _oxygenSaturationEnabled = MutableStateFlow(healthPrefs.getBoolean("oxygen_saturation_enabled", true))
    val oxygenSaturationEnabled: StateFlow<Boolean> = _oxygenSaturationEnabled

    private val _respiratoryRateEnabled = MutableStateFlow(healthPrefs.getBoolean("respiratory_rate_enabled", true))
    val respiratoryRateEnabled: StateFlow<Boolean> = _respiratoryRateEnabled

    private val _stepsEnabled = MutableStateFlow(healthPrefs.getBoolean("steps_enabled", true))
    val stepsEnabled: StateFlow<Boolean> = _stepsEnabled

    private val _distanceEnabled = MutableStateFlow(healthPrefs.getBoolean("distance_enabled", true))
    val distanceEnabled: StateFlow<Boolean> = _distanceEnabled

    private val _floorsClimbedEnabled = MutableStateFlow(healthPrefs.getBoolean("floors_climbed_enabled", true))
    val floorsClimbedEnabled: StateFlow<Boolean> = _floorsClimbedEnabled

    private val _activeCaloriesEnabled = MutableStateFlow(healthPrefs.getBoolean("active_calories_enabled", true))
    val activeCaloriesEnabled: StateFlow<Boolean> = _activeCaloriesEnabled

    // Layout preferences for Home Screen
    private val _homeWidgetOrder = MutableStateFlow(
        prefs.getString("home_widget_order", "WEATHER:true,CALENDAR:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,YOUTUBE:true") ?: "WEATHER:true,CALENDAR:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,YOUTUBE:true"
    )
    val homeWidgetOrder: StateFlow<String> = _homeWidgetOrder

    // Layout preferences for Health Screen
    private val _healthWidgetOrder = MutableStateFlow(
        prefs.getString("health_widget_order", "BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true") ?: "BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true"
    )
    val healthWidgetOrder: StateFlow<String> = _healthWidgetOrder

    fun saveGeminiApiKey(key: String) {
        prefs.edit { putString(KEY_GEMINI_API_KEY, key.trim()) }
        _geminiApiKey.value = key.trim()
    }

    fun getGeminiApiKey(): String = _geminiApiKey.value

    fun updateHomeWidgetOrder(order: String) {
        prefs.edit { putString("home_widget_order", order) }
        _homeWidgetOrder.value = order
    }

    fun updateHealthWidgetOrder(order: String) {
        prefs.edit { putString("health_widget_order", order) }
        _healthWidgetOrder.value = order
    }

    fun setMasterHealthConnectEnabled(enabled: Boolean) {
        healthPrefs.edit { putBoolean("master_health_connect_enabled", enabled) }
        _masterHealthConnectEnabled.value = enabled
    }

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("weather_enabled", enabled) }
        _weatherEnabled.value = enabled
    }

    fun setCalendarEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("calendar_enabled", enabled) }
        _calendarEnabled.value = enabled
    }

    fun setMetricEnabled(metric: String, enabled: Boolean) {
        healthPrefs.edit { putBoolean(metric, enabled) }
        when (metric) {
            "heart_rate_enabled" -> _heartRateEnabled.value = enabled
            "resting_heart_rate_enabled" -> _restingHeartRateEnabled.value = enabled
            "heart_rate_variability_enabled" -> _heartRateVariabilityEnabled.value = enabled
            "oxygen_saturation_enabled" -> _oxygenSaturationEnabled.value = enabled
            "respiratory_rate_enabled" -> _respiratoryRateEnabled.value = enabled
            "steps_enabled" -> _stepsEnabled.value = enabled
            "distance_enabled" -> _distanceEnabled.value = enabled
            "floors_climbed_enabled" -> _floorsClimbedEnabled.value = enabled
            "active_calories_enabled" -> _activeCaloriesEnabled.value = enabled
        }
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}