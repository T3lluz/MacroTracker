package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.NutritionAiRepository
import com.macrotracker.data.remote.ScanResult
import com.macrotracker.data.remote.maybeComputeTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class ScanPhase { CAMERA, PREVIEW, RESULT }

@HiltViewModel
class CameraScanViewModel @Inject constructor(
    private val aiRepo: NutritionAiRepository,
    private val macroRepo: MacroRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow(ScanPhase.CAMERA)
    val phase: StateFlow<ScanPhase> = _phase

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val hasApiKey: Boolean get() = aiRepo.hasApiKey

    // Override fields
    private val _foodNameOverride = MutableStateFlow("")
    val foodNameOverride: StateFlow<String> = _foodNameOverride

    private val _caloriesOverride = MutableStateFlow("")
    val caloriesOverride: StateFlow<String> = _caloriesOverride

    private val _proteinOverride = MutableStateFlow("")
    val proteinOverride: StateFlow<String> = _proteinOverride

    private val _servingsOverride = MutableStateFlow("")
    val servingsOverride: StateFlow<String> = _servingsOverride

    private val _servingSizeOverride = MutableStateFlow("")
    val servingSizeOverride: StateFlow<String> = _servingSizeOverride

    private val _packageWeightOverride = MutableStateFlow("")
    val packageWeightOverride: StateFlow<String> = _packageWeightOverride

    fun setFoodNameOverride(v: String) { _foodNameOverride.value = v }
    fun setCaloriesOverride(v: String) { _caloriesOverride.value = v }
    fun setProteinOverride(v: String) { _proteinOverride.value = v }
    fun setServingsOverride(v: String) { _servingsOverride.value = v }
    fun setServingSizeOverride(v: String) { _servingSizeOverride.value = v }
    fun setPackageWeightOverride(v: String) { _packageWeightOverride.value = v }

    fun setPhase(p: ScanPhase) { _phase.value = p }

    fun analyzeImage(base64: String) {
        _scanning.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val scanResult = aiRepo.analyzeImageWithGemini(base64)
                _result.value = scanResult
                _foodNameOverride.value = if (scanResult.foodName == "Scanned Food") "" else scanResult.foodName
                _caloriesOverride.value = formatNum(scanResult.caloriesPerServing)
                _proteinOverride.value = formatNum(scanResult.proteinPerServing)
                _servingsOverride.value = if (scanResult.servingsPerContainer > 0) formatDouble(scanResult.servingsPerContainer) else "1"
                _servingSizeOverride.value = formatNum(scanResult.servingSizeGrams)
                _packageWeightOverride.value = formatNum(scanResult.packageWeightGrams)
                _phase.value = ScanPhase.RESULT
            } catch (e: Exception) {
                _error.value = e.message ?: "Scan failed."
            } finally {
                _scanning.value = false
            }
        }
    }

    fun getAdjustedResult(): ScanResult? {
        val r = _result.value ?: return null
        return getAdjustedResult(
            foodNameOverride = _foodNameOverride.value,
            caloriesOverride = _caloriesOverride.value,
            proteinOverride = _proteinOverride.value,
            servingsOverride = _servingsOverride.value,
            servingSizeOverride = _servingSizeOverride.value,
            packageWeightOverride = _packageWeightOverride.value,
        )
    }

    /**
     * Overload that accepts explicit override strings.
     * Call this from Composables that have already collected the override StateFlows,
     * so the result is guaranteed to reflect the latest typed values.
     */
    fun getAdjustedResult(
        foodNameOverride: String,
        caloriesOverride: String,
        proteinOverride: String,
        servingsOverride: String,
        servingSizeOverride: String,
        packageWeightOverride: String,
    ): ScanResult? {
        val r = _result.value ?: return null
        // Default servings to 1 when the field is empty and original was 0,
        // so the user doesn't get blocked by a hidden-zero requirement.
        val fallbackServings = if (r.servingsPerContainer > 0) r.servingsPerContainer else 1.0
        return maybeComputeTotals(
            foodName = foodNameOverride.ifBlank { r.foodName },
            caloriesPerServing = caloriesOverride.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.caloriesPerServing,
            proteinPerServing = proteinOverride.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.proteinPerServing,
            servingsPerContainer = servingsOverride.toDoubleOrNull()?.coerceAtLeast(0.0) ?: fallbackServings,
            servingSizeGrams = servingSizeOverride.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.servingSizeGrams,
            packageWeightGrams = packageWeightOverride.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.packageWeightGrams,
        )
    }

    /** Emits true once after a successful log so the UI can navigate away. */
    private val _loggedEvent = MutableStateFlow(false)
    val loggedEvent: StateFlow<Boolean> = _loggedEvent
    fun consumeLoggedEvent() { _loggedEvent.value = false }

    fun saveScannedFood(scanResult: ScanResult) {
        viewModelScope.launch {
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            macroRepo.saveLog(
                MacroLogEntity(
                    id = System.currentTimeMillis().toString(),
                    date = LocalDate.now().format(dateFormat),
                    foodName = "${scanResult.foodName} (Scan)",
                    calories = scanResult.totalCalories,
                    protein = scanResult.totalProtein,
                )
            )
            _loggedEvent.value = true
        }
    }

    fun resetForNewScan() {
        _phase.value = ScanPhase.CAMERA
        _result.value = null
        _error.value = null
        _foodNameOverride.value = ""
        _caloriesOverride.value = ""
        _proteinOverride.value = ""
        _servingsOverride.value = ""
        _servingSizeOverride.value = ""
        _packageWeightOverride.value = ""
    }

    private fun formatNum(v: Int): String = if (v > 0) v.toString() else ""
    private fun formatDouble(v: Double): String = if (v > 0) {
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
    } else ""
}

