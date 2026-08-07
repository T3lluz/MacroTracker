package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.update.AppUpdateInfo
import com.macrotracker.data.update.AppUpdateRepository
import com.macrotracker.data.update.AppUpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "AppUpdateVM"
    }

    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    val currentVersionName: String get() = repository.currentVersionName()
    val currentVersionCode: Int get() = repository.currentVersionCode()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var pendingInstallPath: String? = null

    /**
     * Startup check: quiet unless a newer build exists that the user hasn't dismissed.
     */
    fun checkOnLaunch() {
        if (!repository.shouldAutoCheck()) return
        checkForUpdate(showDialogIfAvailable = true)
    }

    /** Manual check from Settings — always hits the network. */
    fun checkFromSettings() {
        checkForUpdate(showDialogIfAvailable = true)
    }

    private fun checkForUpdate(showDialogIfAvailable: Boolean) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _state.value = AppUpdateUiState.Checking
            try {
                val info = repository.checkForUpdate()
                if (info == null) {
                    _state.value = AppUpdateUiState.UpToDate
                    return@launch
                }
                _state.value = AppUpdateUiState.Available(info)
                if (showDialogIfAvailable && !repository.isDismissed(info.versionCode)) {
                    _showDialog.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _state.value = AppUpdateUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates",
                )
            }
        }
    }

    fun dismissDialog(snooze: Boolean = true) {
        val available = (_state.value as? AppUpdateUiState.Available)?.info
            ?: (_state.value as? AppUpdateUiState.Downloading)?.info
            ?: (_state.value as? AppUpdateUiState.ReadyToInstall)?.info
        if (snooze && available != null) {
            repository.dismiss(available.versionCode)
        }
        _showDialog.value = false
    }

    fun openDialog() {
        if (_state.value is AppUpdateUiState.Available ||
            _state.value is AppUpdateUiState.Downloading ||
            _state.value is AppUpdateUiState.ReadyToInstall
        ) {
            _showDialog.value = true
        }
    }

    fun startDownload(info: AppUpdateInfo = requiredAvailableInfo()) {
        if (!repository.canInstallPackages()) {
            // Caller should send user to install-unknown-apps settings first.
            _state.value = AppUpdateUiState.Available(info)
            _showDialog.value = true
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.value = AppUpdateUiState.Downloading(info, 0f)
            _showDialog.value = true
            try {
                val file = repository.downloadApk(info) { progress ->
                    _state.value = AppUpdateUiState.Downloading(info, progress)
                }
                pendingInstallPath = file.absolutePath
                _state.value = AppUpdateUiState.ReadyToInstall(info, file.absolutePath)
                // Kick off the system installer immediately after download.
                installDownloaded()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _state.value = AppUpdateUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "Download failed",
                )
            }
        }
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun installPermissionSettingsIntent() = repository.installPermissionSettingsIntent()

    fun installDownloaded() {
        val path = pendingInstallPath
            ?: (_state.value as? AppUpdateUiState.ReadyToInstall)?.apkPath
            ?: return
        try {
            repository.installApk(File(path))
        } catch (e: Exception) {
            Log.e(TAG, "Install launch failed", e)
            _state.value = AppUpdateUiState.Error(
                e.message?.takeIf { it.isNotBlank() } ?: "Could not open installer",
            )
        }
    }

    private fun requiredAvailableInfo(): AppUpdateInfo {
        return when (val s = _state.value) {
            is AppUpdateUiState.Available -> s.info
            is AppUpdateUiState.Downloading -> s.info
            is AppUpdateUiState.ReadyToInstall -> s.info
            else -> error("No update available")
        }
    }
}
