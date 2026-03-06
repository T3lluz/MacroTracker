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
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    fun saveGeminiApiKey(key: String) {
        prefs.edit { putString(KEY_GEMINI_API_KEY, key.trim()) }
        _geminiApiKey.value = key.trim()
    }

    fun getGeminiApiKey(): String = _geminiApiKey.value

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}


