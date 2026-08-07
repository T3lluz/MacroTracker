package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.update.AppReleaseNotes
import com.macrotracker.data.update.AppUpdateInfo
import com.macrotracker.data.update.AppUpdateRepository
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.data.update.updateAvailable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    private val _releaseNotes = MutableStateFlow<List<AppReleaseNotes>>(emptyList())
    val releaseNotes: StateFlow<List<AppReleaseNotes>> = _releaseNotes.asStateFlow()

    private val _releaseNotesLoading = MutableStateFlow(false)
    val releaseNotesLoading: StateFlow<Boolean> = _releaseNotesLoading.asStateFlow()

    /** True when a newer APK is available (drives Settings tab badge). */
    val updateAvailable: StateFlow<Boolean> = _state
        .map { it.updateAvailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentVersionName: String get() = repository.currentVersionName()
    val currentVersionCode: Int get() = repository.currentVersionCode()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var listenJob: Job? = null
    private var releaseNotesJob: Job? = null
    private var pendingInstallPath: String? = null
    private var listeningStarted = false

    /**
     * Start foreground listening: immediate check, then poll GitHub every
     * [AppUpdateRepository.FOREGROUND_POLL_INTERVAL_MS] so a newly published
     * release prompts in-app quickly.
     */
    fun startListening() {
        if (listeningStarted) return
        listeningStarted = true
        checkForUpdate(
            showDialogIfAvailable = true,
            forceNetwork = true,
            quiet = true,
        )
        loadReleaseNotes()
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            while (isActive) {
                delay(AppUpdateRepository.FOREGROUND_POLL_INTERVAL_MS)
                checkForUpdate(
                    showDialogIfAvailable = true,
                    forceNetwork = true,
                    quiet = true,
                )
            }
        }
    }

    /** Re-check when the activity resumes (throttled). */
    fun checkOnResume() {
        checkForUpdate(
            showDialogIfAvailable = true,
            forceNetwork = false,
            quiet = true,
        )
    }

    /**
     * Startup check: quiet unless a newer build exists that the user hasn't dismissed.
     */
    fun checkOnLaunch() {
        startListening()
    }

    /** Manual check from Settings — always hits the network. */
    fun checkFromSettings() {
        checkForUpdate(
            showDialogIfAvailable = true,
            forceNetwork = true,
            quiet = false,
        )
        loadReleaseNotes(force = true)
    }

    fun loadReleaseNotes(force: Boolean = false) {
        if (!force && _releaseNotes.value.isNotEmpty()) return
        releaseNotesJob?.cancel()
        releaseNotesJob = viewModelScope.launch {
            _releaseNotesLoading.value = true
            try {
                _releaseNotes.value = repository.listReleaseNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load release notes", e)
            } finally {
                _releaseNotesLoading.value = false
            }
        }
    }

    private fun checkForUpdate(
        showDialogIfAvailable: Boolean,
        forceNetwork: Boolean,
        quiet: Boolean,
    ) {
        if (!forceNetwork && !repository.shouldAutoCheck()) return
        // Don't interrupt an active download / install flow with a re-check.
        if (_state.value is AppUpdateUiState.Downloading ||
            _state.value is AppUpdateUiState.ReadyToInstall
        ) {
            return
        }
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            if (!quiet) {
                _state.value = AppUpdateUiState.Checking
            }
            try {
                val info = repository.checkForUpdate()
                if (info == null) {
                    if (_state.value !is AppUpdateUiState.Available &&
                        _state.value !is AppUpdateUiState.Downloading &&
                        _state.value !is AppUpdateUiState.ReadyToInstall
                    ) {
                        _state.value = AppUpdateUiState.UpToDate
                    }
                    return@launch
                }
                val wasAlreadyAvailable =
                    (_state.value as? AppUpdateUiState.Available)?.info?.versionCode == info.versionCode
                _state.value = AppUpdateUiState.Available(info)
                // Prompt when a newer build appears (or on first discovery). Don't re-open
                // after the user snoozed the same versionCode.
                if (showDialogIfAvailable &&
                    !repository.isDismissed(info.versionCode) &&
                    !wasAlreadyAvailable
                ) {
                    _showDialog.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                if (!quiet || _state.value is AppUpdateUiState.Checking ||
                    _state.value is AppUpdateUiState.Idle
                ) {
                    _state.value = AppUpdateUiState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates",
                    )
                }
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
        if (_state.value.updateAvailable) {
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

    override fun onCleared() {
        listenJob?.cancel()
        super.onCleared()
    }
}
