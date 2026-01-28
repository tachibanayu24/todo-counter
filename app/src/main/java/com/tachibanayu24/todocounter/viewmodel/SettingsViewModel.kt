package com.tachibanayu24.todocounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tachibanayu24.todocounter.auth.GoogleAuthManager
import com.tachibanayu24.todocounter.data.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val account: GoogleSignInAccount? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val syncManager: SyncManager
) : ViewModel() {

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
                Timber.e(e, "Failed to sync completed tasks")
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
