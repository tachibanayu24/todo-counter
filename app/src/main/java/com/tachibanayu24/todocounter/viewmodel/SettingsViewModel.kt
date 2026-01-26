package com.tachibanayu24.todocounter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.auth.GoogleAuthManager
import com.tachibanayu24.todocounter.data.AppDatabase
import com.tachibanayu24.todocounter.data.SyncManager
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val account: GoogleSignInAccount? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = GoogleAuthManager(application)
    private val db = AppDatabase.getInstance(application)
    private val syncManager = SyncManager(
        TasksRepository(application),
        CompletionRepository(db.dailyCompletionDao()),
        db.completedTaskDao()
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshAuthState()
    }

    fun refreshAuthState() {
        _uiState.value = _uiState.value.copy(
            isSignedIn = authManager.isSignedIn(),
            account = authManager.getSignedInAccount()
        )
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                account = null
            )
        }
    }

    fun syncCompletedTasks(days: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            try {
                val result = syncManager.syncCompletedTasks(days)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "Synced ${result.synced} tasks from past $days days"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "Sync failed: ${e.message}"
                )
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }
}
