package com.tachibanayu24.todocounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.entity.CompletedTask
import com.tachibanayu24.todocounter.data.entity.DailyCompletion
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import com.tachibanayu24.todocounter.data.repository.CompletionStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChartPeriod { WEEK, MONTH }

data class DashboardUiState(
    val chartData: List<DailyCompletion> = emptyList(),
    val heatmapData: List<DailyCompletion> = emptyList(),
    val stats: CompletionStats = CompletionStats(0, 0.0, 0, 0),
    val currentStreak: Int = 0,
    val selectedPeriod: ChartPeriod = ChartPeriod.WEEK,
    val isLoading: Boolean = false,
    val selectedDateTasks: List<CompletedTask> = emptyList(),
    val selectedDate: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val completionRepository: CompletionRepository,
    private val completedTaskDao: CompletedTaskDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val period = _uiState.value.selectedPeriod
            val chartData = when (period) {
                ChartPeriod.WEEK -> completionRepository.getWeeklyData()
                ChartPeriod.MONTH -> completionRepository.getMonthlyData()
            }
            val heatmapData = completionRepository.getHeatmapData()
            val stats = completionRepository.getStatistics()
            val streak = completionRepository.getCurrentStreak()

            _uiState.value = DashboardUiState(
                chartData = chartData,
                heatmapData = heatmapData,
                stats = stats,
                currentStreak = streak,
                selectedPeriod = period,
                isLoading = false
            )
        }
    }

    fun setPeriod(period: ChartPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        loadData()
    }

    fun selectDate(date: String) {
        viewModelScope.launch {
            val tasks = completedTaskDao.getByDate(date)
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                selectedDateTasks = tasks
            )
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedDate = null,
            selectedDateTasks = emptyList()
        )
    }
}
