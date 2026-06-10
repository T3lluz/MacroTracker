package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MacroRepository,
) : ViewModel() {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _rangeDays = MutableStateFlow(7)
    val rangeDays: StateFlow<Int> = _rangeDays

    private val _metric = MutableStateFlow("calories")
    val metric: StateFlow<String> = _metric

    private val _macroHistory = MutableStateFlow<List<DailySummary>>(emptyList())
    val macroHistory: StateFlow<List<DailySummary>> = _macroHistory

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormat))
    val selectedDate: StateFlow<String> = _selectedDate

    private val _selectedLogs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val selectedLogs: StateFlow<List<MacroLogEntity>> = _selectedLogs

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun loadData() {
        viewModelScope.launch {
            val showLoading = _macroHistory.value.isEmpty()
            if (showLoading) _loading.value = true
            try {
                _macroHistory.value = repository.getDailySummariesRange(_rangeDays.value)
                _selectedLogs.value = repository.getLogsForDate(_selectedDate.value)
            } catch (_: Exception) { }
            _loading.value = false
        }
    }

    fun setRangeDays(days: Int) {
        _rangeDays.value = days
        loadData()
    }

    fun setMetric(m: String) {
        _metric.value = m
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        viewModelScope.launch {
            _selectedLogs.value = repository.getLogsForDate(date)
        }
    }
}

