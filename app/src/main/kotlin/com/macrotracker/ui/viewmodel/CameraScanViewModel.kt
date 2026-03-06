package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.remote.NutritionAiRepository
import com.macrotracker.data.remote.ScanResult
import com.macrotracker.data.remote.maybeComputeTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanPhase { CAMERA, PREVIEW, RESULT }

@HiltViewModel
class CameraScanViewModel @Inject constructor(
    private val aiRepo: NutritionAiRepository,
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
                _servingsOverride.value = formatDouble(scanResult.servingsPerContainer)
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
        return maybeComputeTotals(
            foodName = _foodNameOverride.value.ifBlank { r.foodName },
            caloriesPerServing = _caloriesOverride.value.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.caloriesPerServing,
            proteinPerServing = _proteinOverride.value.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.proteinPerServing,
            servingsPerContainer = _servingsOverride.value.toDoubleOrNull()?.coerceAtLeast(0.0) ?: r.servingsPerContainer,
            servingSizeGrams = _servingSizeOverride.value.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.servingSizeGrams,
            packageWeightGrams = _packageWeightOverride.value.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: r.packageWeightGrams,
        )
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

