package com.tachibanayu24.todocounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tachibanayu24.todocounter.api.ITasksRepository
import com.tachibanayu24.todocounter.api.PendingTask
import com.tachibanayu24.todocounter.api.TaskCount
import com.tachibanayu24.todocounter.api.TaskStatus
import com.tachibanayu24.todocounter.api.TaskListInfo
import com.tachibanayu24.todocounter.data.SyncManager
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tasksRepository: ITasksRepository,
    private val completionRepository: CompletionRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 今日の完了タスクを同期してからデータを取得
                syncManager.syncTodayCompletedTasks()

                val taskCountResult = tasksRepository.getTaskCount()
                val todayCompletion = completionRepository.getTodayCompletion()
                val streak = completionRepository.getCurrentStreak()
                val pendingTasksResult = tasksRepository.getPendingTasksDueToday()

                _uiState.value = _uiState.value.copy(
                    taskCount = taskCountResult.getOrNull(),
                    todayCompleted = todayCompletion?.completedCount ?: 0,
                    currentStreak = streak,
                    pendingTasks = pendingTasksResult.getOrNull() ?: emptyList(),
                    isLoading = false,
                    error = taskCountResult.exceptionOrNull()?.message
                        ?: pendingTasksResult.exceptionOrNull()?.message
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load home data")
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
            val result = tasksRepository.updateTaskStatus(
                taskListId = task.taskListId,
                taskId = task.id,
                completed = newCompleted
            )

            if (result.isSuccess) {
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

                loadData()
            } else {
                Timber.e(result.exceptionOrNull(), "Failed to toggle task status")
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
            val result = tasksRepository.getTaskLists()
            result.onSuccess { lists ->
                _uiState.value = _uiState.value.copy(taskLists = lists)
            }.onError { e ->
                Timber.e(e, "Failed to load task lists")
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
            val result = tasksRepository.addTask(
                taskListId = taskListId,
                title = title,
                notes = notes,
                dueDate = dueDate
            )
            result.onSuccess {
                loadData()
            }.onError { e ->
                Timber.e(e, "Failed to add task")
            }
            _uiState.value = _uiState.value.copy(isAddingTask = false)
        }
    }
}
