package com.tachibanayu24.todocounter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tachibanayu24.todocounter.api.TaskCount
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.data.AppDatabase
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val taskCount: TaskCount? = null,
    val todayCompleted: Int = 0,
    val currentStreak: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val tasksRepository = TasksRepository(application)
    private val completionRepository = CompletionRepository(
        AppDatabase.getInstance(application).dailyCompletionDao()
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val taskCount = tasksRepository.getTaskCount()
                val todayCompletion = completionRepository.getTodayCompletion()
                val streak = completionRepository.getCurrentStreak()

                _uiState.value = _uiState.value.copy(
                    taskCount = taskCount,
                    todayCompleted = todayCompletion?.completedCount ?: 0,
                    currentStreak = streak,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refresh() {
        loadData()
    }
}
