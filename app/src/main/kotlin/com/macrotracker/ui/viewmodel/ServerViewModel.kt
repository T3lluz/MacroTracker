package com.macrotracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.server.ServerAuthMode
import com.macrotracker.data.server.ServerError
import com.macrotracker.data.server.ServerHostProfile
import com.macrotracker.data.server.ServerMonitorRepository
import com.macrotracker.data.server.ServerMonitorService
import com.macrotracker.data.server.ServerNotificationSettings
import com.macrotracker.data.server.ServerNotifier
import com.macrotracker.data.server.ServerProfile
import com.macrotracker.data.server.ServerRuntime
import com.macrotracker.data.server.ServerStore
import com.macrotracker.data.server.ServerThresholds
import com.macrotracker.data.server.TestConnectionResult
import com.macrotracker.data.server.parseServerTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the connection test is currently doing, for the add/edit form. */
sealed interface ServerTestUiState {
    data object Idle : ServerTestUiState
    data object Testing : ServerTestUiState
    data class Success(val host: ServerHostProfile, val fingerprint: String) : ServerTestUiState
    data class Failure(val error: ServerError) : ServerTestUiState
}

/**
 * Shared by the server screen, the home card and the settings screens.
 *
 * Polling is reference-counted in the repository, so several of these can be
 * alive at once without opening a second SSH session per server.
 */
@HiltViewModel
class ServerViewModel @Inject constructor(
    private val repository: ServerMonitorRepository,
    private val store: ServerStore,
    private val notifier: ServerNotifier,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val runtimes: StateFlow<Map<String, ServerRuntime>> = repository.runtimes
    val profiles: StateFlow<List<ServerProfile>> = repository.profiles
    val settings: StateFlow<ServerNotificationSettings> = repository.settings

    private val _testState = MutableStateFlow<ServerTestUiState>(ServerTestUiState.Idle)
    val testState: StateFlow<ServerTestUiState> = _testState

    /** Unique per ViewModel instance so two screens do not cancel each other's polling. */
    private val pollTag = "vm-${hashCode()}"

    fun startPolling() = repository.acquire(pollTag)

    fun stopPolling() = repository.release(pollTag)

    override fun onCleared() {
        repository.release(pollTag)
        super.onCleared()
    }

    // ── Profiles ────────────────────────────────────────────────────────

    /**
     * Accepts `user@host`, `host`, `user@host:port` or an `ssh://` paste, with
     * the explicit [username] and [port] fields winning when they are filled in.
     */
    fun saveServer(
        existingId: String?,
        label: String,
        target: String,
        username: String,
        port: String,
        authMode: ServerAuthMode,
        secret: String,
        keyPassphrase: String,
    ): SaveResult {
        val parsed = parseServerTarget(target)
            ?: return SaveResult.Invalid("Enter a hostname or IP address")
        val resolvedUser = username.trim().ifBlank { parsed.username.orEmpty() }
        if (resolvedUser.isBlank()) return SaveResult.Invalid("Enter a username, or type user@host")
        val resolvedPort = port.trim().toIntOrNull()
            ?: parsed.port
            ?: ServerProfile.DEFAULT_SSH_PORT
        if (resolvedPort !in 1..65535) return SaveResult.Invalid("Port must be between 1 and 65535")

        if (existingId == null) {
            if (secret.isBlank()) {
                return SaveResult.Invalid(
                    if (authMode == ServerAuthMode.PASSWORD) "Enter a password" else "Paste a private key",
                )
            }
            store.addProfile(
                label = label.trim(),
                host = parsed.host,
                username = resolvedUser,
                port = resolvedPort,
                authMode = authMode,
                secret = secret,
                keyPassphrase = keyPassphrase,
            )
        } else {
            val existing = store.profile(existingId) ?: return SaveResult.Invalid("Server no longer exists")
            // Moving a profile to a different host means the pinned key belongs to
            // a machine we are no longer talking to; keeping it would report a
            // bogus "host key changed" on the very next connect.
            if (existing.host != parsed.host || existing.port != resolvedPort) {
                store.forgetHostKey(existingId)
            }
            store.updateProfile(
                profile = existing.copy(
                    label = label.trim().ifBlank { parsed.host },
                    host = parsed.host,
                    username = resolvedUser,
                    port = resolvedPort,
                    authMode = authMode,
                ),
                // Blank means "keep the stored credential" so the form does not
                // have to round-trip a password just to rename a server.
                secret = secret.takeIf { it.isNotBlank() },
                keyPassphrase = keyPassphrase.takeIf { it.isNotBlank() },
            )
        }
        return SaveResult.Saved
    }

    fun deleteServer(id: String) {
        notifier.clearFor(id)
        store.deleteProfile(id)
        if (settings.value.liveNotificationServerId == id) {
            store.updateSettings { it.copy(liveNotificationServerId = null) }
        }
        if (profiles.value.isEmpty()) {
            setLiveNotificationEnabled(false)
        }
    }

    fun setServerEnabled(id: String, enabled: Boolean) {
        store.profile(id)?.let { store.updateProfile(it.copy(enabled = enabled)) }
    }

    fun trustNewHostKey(id: String) = repository.trustNewHostKey(id)

    fun refreshNews(id: String) = repository.refreshNews(id)

    // ── Connection test ─────────────────────────────────────────────────

    fun testConnection(
        target: String,
        username: String,
        port: String,
        authMode: ServerAuthMode,
        secret: String,
        keyPassphrase: String,
    ) {
        val parsed = parseServerTarget(target) ?: run {
            _testState.value = ServerTestUiState.Failure(
                ServerError.Unknown("Enter a hostname or IP address"),
            )
            return
        }
        val resolvedUser = username.trim().ifBlank { parsed.username.orEmpty() }
        if (resolvedUser.isBlank()) {
            _testState.value = ServerTestUiState.Failure(
                ServerError.AuthFailed("Enter a username, or type user@host"),
            )
            return
        }
        _testState.value = ServerTestUiState.Testing
        viewModelScope.launch {
            val result = repository.testConnection(
                host = parsed.host,
                username = resolvedUser,
                port = port.trim().toIntOrNull() ?: parsed.port ?: ServerProfile.DEFAULT_SSH_PORT,
                authMode = authMode,
                secret = secret,
                keyPassphrase = keyPassphrase,
            )
            _testState.value = when (result) {
                is TestConnectionResult.Success -> ServerTestUiState.Success(result.host, result.fingerprint)
                is TestConnectionResult.Failure -> ServerTestUiState.Failure(result.error)
            }
        }
    }

    fun resetTestState() {
        _testState.value = ServerTestUiState.Idle
    }

    // ── Notification settings ───────────────────────────────────────────

    fun setNotificationsEnabled(enabled: Boolean) {
        store.updateSettings { it.copy(enabled = enabled) }
        if (enabled) notifier.ensureChannels()
    }

    fun setCriticalEnabled(enabled: Boolean) = store.updateSettings { it.copy(criticalEnabled = enabled) }

    fun setWarningEnabled(enabled: Boolean) = store.updateSettings { it.copy(warningEnabled = enabled) }

    fun setUpdatesEnabled(enabled: Boolean) = store.updateSettings { it.copy(updatesEnabled = enabled) }

    fun setStartOnBoot(enabled: Boolean) = store.updateSettings { it.copy(startOnBoot = enabled) }

    fun setPollSeconds(seconds: Int) = store.updateSettings { it.copy(pollSeconds = seconds) }

    fun setBackgroundPollSeconds(seconds: Int) =
        store.updateSettings { it.copy(backgroundPollSeconds = seconds) }

    fun setAlertCooldownMinutes(minutes: Int) =
        store.updateSettings { it.copy(alertCooldownMinutes = minutes) }

    fun updateThresholds(transform: (ServerThresholds) -> ServerThresholds) =
        store.updateSettings { it.copy(thresholds = transform(it.thresholds)) }

    fun setLiveNotificationServer(id: String?) =
        store.updateSettings { it.copy(liveNotificationServerId = id) }

    /**
     * Starting the service is what actually posts the ongoing notification, so
     * the toggle and the service have to move together.
     */
    fun setLiveNotificationEnabled(enabled: Boolean) {
        store.updateSettings { it.copy(liveNotificationEnabled = enabled) }
        if (enabled && profiles.value.any { it.enabled }) {
            ServerMonitorService.start(context)
        } else {
            ServerMonitorService.stop(context)
        }
    }

    fun hasNotificationPermission(): Boolean = notifier.hasPermission()

    fun ensureChannels() = notifier.ensureChannels()

    sealed interface SaveResult {
        data object Saved : SaveResult
        data class Invalid(val message: String) : SaveResult
    }
}
