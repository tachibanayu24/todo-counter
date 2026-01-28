package com.tachibanayu24.todocounter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tachibanayu24.todocounter.api.PendingTask
import com.tachibanayu24.todocounter.api.TaskCount
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.api.TaskStatus
import com.tachibanayu24.todocounter.api.TaskListInfo
import java.time.LocalDate
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
    val error: String? = null,
    val pendingTasks: List<PendingTask> = emptyList(),
    val expandedTaskId: String? = null,
    val updatingTaskIds: Set<String> = emptySet(),
    val taskLists: List<TaskListInfo> = emptyList(),
    val isAddingTask: Boolean = false
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
                val pendingTasks = tasksRepository.getPendingTasksDueToday()

                _uiState.value = _uiState.value.copy(
                    taskCount = taskCount,
                    todayCompleted = todayCompletion?.completedCount ?: 0,
                    currentStreak = streak,
                    pendingTasks = pendingTasks,
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

    fun toggleTaskStatus(task: PendingTask) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                updatingTaskIds = _uiState.value.updatingTaskIds + task.id
            )

            val newCompleted = task.status != TaskStatus.COMPLETED
            val success = tasksRepository.updateTaskStatus(
                taskListId = task.taskListId,
                taskId = task.id,
                completed = newCompleted
            )

            if (success) {
                // ローカル状態を即座に更新
                val updatedTasks = _uiState.value.pendingTasks.map {
                    if (it.id == task.id) {
                        it.copy(status = if (newCompleted) TaskStatus.COMPLETED else TaskStatus.NEEDS_ACTION)
                    } else it
                }.sortedWith(
                    compareBy<PendingTask> { it.status == TaskStatus.COMPLETED }
                        .thenBy { it.due }
                )

                _uiState.value = _uiState.value.copy(
                    pendingTasks = updatedTasks,
                    updatingTaskIds = _uiState.value.updatingTaskIds - task.id
                )

                // タスクカウントも更新
                loadData()
            } else {
                _uiState.value = _uiState.value.copy(
                    updatingTaskIds = _uiState.value.updatingTaskIds - task.id
                )
            }
        }
    }

    fun toggleTaskExpand(taskId: String) {
        _uiState.value = _uiState.value.copy(
            expandedTaskId = if (_uiState.value.expandedTaskId == taskId) null else taskId
        )
    }

    fun refresh() {
        loadData()
    }

    fun loadTaskLists() {
        viewModelScope.launch {
            try {
                val lists = tasksRepository.getTaskLists()
                _uiState.value = _uiState.value.copy(taskLists = lists)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTask(
        taskListId: String,
        title: String,
        notes: String?,
        dueDate: LocalDate?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingTask = true)
            try {
                val success = tasksRepository.addTask(
                    taskListId = taskListId,
                    title = title,
                    notes = notes,
                    dueDate = dueDate
                )
                if (success) {
                    loadData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isAddingTask = false)
            }
        }
    }
}
