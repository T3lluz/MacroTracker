package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: MacroRepository,
) : ViewModel() {

    private val _history = MutableStateFlow<List<DailySummary>>(emptyList())
    val history: StateFlow<List<DailySummary>> = _history

    private val _calGoal = MutableStateFlow("2000")
    val calGoal: StateFlow<String> = _calGoal

    private val _protGoal = MutableStateFlow("150")
    val protGoal: StateFlow<String> = _protGoal

    fun setCalGoal(v: String) { _calGoal.value = v }
    fun setProtGoal(v: String) { _protGoal.value = v }

    fun loadData() {
        viewModelScope.launch {
            val summaries = repository.getDailySummariesRange(7)
            _history.value = summaries.reversed() // most recent first
            val goals = repository.getGoals()
            _calGoal.value = goals.calorieGoal.toString()
            _protGoal.value = goals.proteinGoal.toString()
        }
    }

    fun saveGoals() {
        viewModelScope.launch {
            val cal = _calGoal.value.toIntOrNull() ?: 2000
            val prot = _protGoal.value.toIntOrNull() ?: 150
            repository.saveGoals(cal, prot)
            loadData()
        }
    }
}

