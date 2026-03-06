package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.NutritionAiRepository
import com.macrotracker.data.remote.NutritionEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class FeedbackState(val text: String, val isError: Boolean)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepo: NutritionAiRepository,
    private val macroRepo: MacroRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _estimate = MutableStateFlow<NutritionEstimate?>(null)
    val estimate: StateFlow<NutritionEstimate?> = _estimate

    private val _feedback = MutableStateFlow<FeedbackState?>(null)
    val feedback: StateFlow<FeedbackState?> = _feedback

    /** Emits `true` once after a successful log so the UI can navigate away. */
    private val _loggedEvent = MutableStateFlow(false)
    val loggedEvent: StateFlow<Boolean> = _loggedEvent

    fun consumeLoggedEvent() { _loggedEvent.value = false }

    fun estimateNutrition(foodQuery: String) {
        if (foodQuery.isBlank()) {
            _feedback.value = FeedbackState("Enter a food name or description first.", true)
            return
        }
        _loading.value = true
        _feedback.value = null
        viewModelScope.launch {
            try {
                val result = aiRepo.estimateNutritionWithAI(foodQuery)
                _estimate.value = result
                _feedback.value = FeedbackState("AI estimate ready.", false)
            } catch (e: Exception) {
                _estimate.value = null
                _feedback.value = FeedbackState(e.message ?: "Failed to estimate macros.", true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun logEstimate() {
        val est = _estimate.value ?: return
        viewModelScope.launch {
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            macroRepo.saveLog(
                MacroLogEntity(
                    id = System.currentTimeMillis().toString(),
                    date = LocalDate.now().format(dateFormat),
                    foodName = "${est.foodName} (AI)",
                    calories = est.calories,
                    protein = est.protein,
                )
            )
            _feedback.value = FeedbackState("AI estimate logged to today.", false)
            _loggedEvent.value = true
        }
    }
}

