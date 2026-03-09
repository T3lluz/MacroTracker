package com.macrotracker.ui.viewmodel

import com.macrotracker.data.f1.F1Standings
import java.time.Instant

sealed interface F1UiState {
    data object Loading : F1UiState
    data class Success(val f1Data: F1Standings, val lastUpdatedAt: Instant? = null) : F1UiState
    data class Error(val message: String) : F1UiState
}
