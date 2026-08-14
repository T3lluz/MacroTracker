package com.macrotracker.ui.viewmodel

import com.macrotracker.data.github.GitHubDeviceLogin
import com.macrotracker.data.github.GitHubSnapshot
import java.time.Instant

sealed interface GitHubUiState {
    data object Idle : GitHubUiState
    data object Loading : GitHubUiState
    data object NeedsAuth : GitHubUiState
    data class Success(
        val data: GitHubSnapshot,
        val lastUpdatedAt: Instant? = null,
    ) : GitHubUiState
    data class Error(val message: String) : GitHubUiState
}

data class GitHubAuthUiState(
    val isConnected: Boolean = false,
    val login: String? = null,
    val displayName: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isConfigured: Boolean = true,
    val isAwaitingBrowser: Boolean = false,
    val deviceLogin: GitHubDeviceLogin? = null,
)
