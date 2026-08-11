package com.macrotracker.data.twitch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Twitch OAuth via Authorization Code + in-app WebView.
 *
 * Twitch Developer Console:
 * 1. Confidential app with Client ID + Secret in `local.properties`
 * 2. OAuth Redirect URL **exactly**: [REDIRECT_URI]
 * 3. Scope: `user:read:follows`
 *
 * The WebView intercepts the HTTPS localhost redirect (Chrome cannot load that host,
 * which is why an external browser shows "site can't be reached").
 */
@Singleton
class TwitchAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "TwitchAuth"
        private const val PREFS_NAME = "twitch_settings"
        private const val KEY_CONNECTED = "twitch_connected"
        private const val KEY_LOGIN = "twitch_account_login"
        private const val KEY_DISPLAY_NAME = "twitch_account_display"
        private const val KEY_USER_ID = "twitch_account_user_id"
        private const val KEY_ACCESS = "twitch_access_token"
        private const val KEY_REFRESH = "twitch_refresh_token"
        private const val KEY_EXPIRES_AT = "twitch_expires_at_ms"

        const val SCOPE_USER_READ_FOLLOWS = "user:read:follows"
        /** Must match an OAuth Redirect URL on the Twitch application (HTTPS required). */
        const val REDIRECT_URI = "https://localhost/twitch/oauth"

        private const val AUTHORIZE_URL = "https://id.twitch.tv/oauth2/authorize"
        private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val REVOKE_URL = "https://id.twitch.tv/oauth2/revoke"
        private const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        private const val AUTH_TIMEOUT_MS = 5 * 60_000L
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    private val pendingState = AtomicReference<String?>(null)
    private val pendingResult = AtomicReference<CompletableDeferred<TwitchAuthOutcome>?>(null)

    private val _isAwaitingBrowser = MutableStateFlow(false)
    val isAwaitingBrowser: StateFlow<Boolean> = _isAwaitingBrowser

    /** Non-null while the UI should show the in-app Twitch login WebView. */
    private val _webLoginUrl = MutableStateFlow<String?>(null)
    val webLoginUrl: StateFlow<String?> = _webLoginUrl

    fun clientId(): String = BuildConfig.TWITCH_CLIENT_ID.trim()
    fun clientSecret(): String = BuildConfig.TWITCH_CLIENT_SECRET.trim()

    fun isConfigured(): Boolean = clientId().isNotBlank() && clientSecret().isNotBlank()

    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false) &&
        !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    fun connectedLogin(): String? = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() }
    fun connectedDisplayName(): String? =
        prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: connectedLogin()
    fun connectedUserId(): String? = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun isTwitchRedirect(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.path.orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("localhost", ignoreCase = true) &&
            (path == "/twitch/oauth" || path.startsWith("/twitch/oauth/"))
    }

    /**
     * Shows an in-app WebView (via [webLoginUrl]) and suspends until redirect, cancel, or timeout.
     */
    suspend fun authorizeInteractively(): TwitchAuthOutcome {
        if (!isConfigured()) {
            return TwitchAuthOutcome.Failed(
                "Twitch Client ID/Secret missing — add TWITCH_CLIENT_ID and " +
                    "TWITCH_CLIENT_SECRET to local.properties",
            )
        }
        return authMutex.withLock {
            cancelPendingLocked(TwitchAuthOutcome.Failed("Superseded by a new login"))
            val state = randomState()
            val deferred = CompletableDeferred<TwitchAuthOutcome>()
            pendingState.set(state)
            pendingResult.set(deferred)
            _isAwaitingBrowser.value = true
            try {
                _webLoginUrl.value = buildAuthorizeUrl(state)
                val outcome = withTimeoutOrNull(AUTH_TIMEOUT_MS) { deferred.await() }
                    ?: TwitchAuthOutcome.Failed("Twitch login timed out — try again")
                outcome
            } catch (e: Exception) {
                Log.e(TAG, "authorizeInteractively failed", e)
                TwitchAuthOutcome.Failed(e.message ?: "Twitch authorization failed")
            } finally {
                pendingState.set(null)
                pendingResult.set(null)
                _isAwaitingBrowser.value = false
                _webLoginUrl.value = null
            }
        }
    }

    fun cancelPendingLogin() {
        val deferred = pendingResult.getAndSet(null)
        pendingState.set(null)
        _isAwaitingBrowser.value = false
        _webLoginUrl.value = null
        deferred?.complete(TwitchAuthOutcome.Failed("Twitch login cancelled"))
    }

    /**
     * Called from [com.macrotracker.MainActivity] when Twitch redirects back into the app.
     */
    fun handleRedirectIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (!isTwitchRedirect(uri)) return false
        // Consume so recomposition / redelivery doesn't re-handle.
        intent.data = null
        return handleRedirectUri(uri)
    }

    fun handleRedirectUri(uri: Uri): Boolean {
        if (!isTwitchRedirect(uri)) return false
        val deferred = pendingResult.get() ?: run {
            Log.w(TAG, "Twitch redirect with no pending login")
            return true
        }
        val expectedState = pendingState.get()
        val returnedState = uri.getQueryParameter("state")
        if (expectedState.isNullOrBlank() || returnedState != expectedState) {
            deferred.complete(TwitchAuthOutcome.Failed("Twitch login state mismatch — try again"))
            return true
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description")?.replace('+', ' ')
            deferred.complete(
                TwitchAuthOutcome.Failed(desc?.takeIf { it.isNotBlank() } ?: "Twitch login was denied"),
            )
            return true
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            deferred.complete(TwitchAuthOutcome.Failed("Twitch returned no authorization code"))
            return true
        }
        scope.launch {
            val outcome = try {
                exchangeCodeForSession(code)
            } catch (e: Exception) {
                Log.e(TAG, "code exchange failed", e)
                TwitchAuthOutcome.Failed(e.message ?: "Could not finish Twitch login")
            }
            deferred.complete(outcome)
        }
        return true
    }

    /** Returns a valid user access token, refreshing when needed. */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() } ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val skewMs = 60_000L
        if (System.currentTimeMillis() < expiresAt - skewMs) return@withContext access
        refreshAccessToken() ?: access.takeIf { validateToken(it) }
    }

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        try {
            val bodyBuilder = FormBody.Builder()
                .add("client_id", clientId())
                .add("client_secret", clientSecret())
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(bodyBuilder.build())
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh failed ${response.code}")
                    if (response.code == 400 || response.code == 401) markDisconnected()
                    return@withContext null
                }
                val json = JSONObject(body)
                val access = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
                val expiresIn = json.optInt("expires_in", 14_400)
                prefs.edit {
                    putString(KEY_ACCESS, access)
                    putString(KEY_REFRESH, newRefresh)
                    putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)
                    putBoolean(KEY_CONNECTED, true)
                }
                access
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken failed", e)
            null
        }
    }

    /**
     * App access token (client credentials) for channel search without a user session.
     */
    suspend fun appAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId())
                .add("client_secret", clientSecret())
                .add("grant_type", "client_credentials")
                .build()
            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "app token failed ${response.code}")
                    return@withContext null
                }
                JSONObject(raw).optString("access_token").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "appAccessToken failed", e)
            null
        }
    }

    suspend fun revoke(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_ACCESS, null)
        try {
            if (!token.isNullOrBlank() && clientId().isNotBlank()) {
                val body = FormBody.Builder()
                    .add("client_id", clientId())
                    .add("token", token)
                    .build()
                val request = Request.Builder().url(REVOKE_URL).post(body).build()
                okHttpClient.newCall(request).execute().close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "revoke failed — clearing local session anyway", e)
        }
        markDisconnected()
        Result.success(Unit)
    }

    fun markDisconnected() {
        cancelPendingLogin()
        prefs.edit {
            putBoolean(KEY_CONNECTED, false)
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
            remove(KEY_EXPIRES_AT)
            remove(KEY_LOGIN)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_USER_ID)
        }
    }

    private fun cancelPendingLocked(outcome: TwitchAuthOutcome) {
        pendingResult.getAndSet(null)?.complete(outcome)
        pendingState.set(null)
        _isAwaitingBrowser.value = false
        _webLoginUrl.value = null
    }

    private fun buildAuthorizeUrl(state: String): String =
        AUTHORIZE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId())
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE_USER_READ_FOLLOWS)
            .addQueryParameter("state", state)
            .addQueryParameter("force_verify", "false")
            .build()
            .toString()

    private suspend fun exchangeCodeForSession(code: String): TwitchAuthOutcome =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", clientId())
                .add("client_secret", clientSecret())
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()
            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseOAuthError(raw)
                        ?: "Twitch token exchange failed (${response.code})"
                    // Common misconfig: redirect URI not registered exactly.
                    if (message.contains("redirect", ignoreCase = true) || response.code == 400) {
                        return@withContext TwitchAuthOutcome.Failed(
                            "Twitch login misconfigured — in Twitch Console add OAuth Redirect URL: " +
                                REDIRECT_URI,
                        )
                    }
                    return@withContext TwitchAuthOutcome.Failed(message)
                }
                val json = JSONObject(raw)
                val access = json.optString("access_token")
                if (access.isBlank()) {
                    return@withContext TwitchAuthOutcome.Failed("Twitch returned no access token")
                }
                val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optInt("expires_in", 14_400)
                val profile = fetchProfile(access)
                persistSession(
                    access = access,
                    refresh = refresh,
                    expiresInSec = expiresIn,
                    userId = profile?.userId,
                    login = profile?.login,
                    displayName = profile?.displayName,
                )
                TwitchAuthOutcome.Ready(
                    accessToken = access,
                    userId = profile?.userId,
                    login = profile?.login,
                    displayName = profile?.displayName,
                )
            }
        }

    private fun persistSession(
        access: String,
        refresh: String?,
        expiresInSec: Int,
        userId: String?,
        login: String?,
        displayName: String?,
    ) {
        prefs.edit {
            putBoolean(KEY_CONNECTED, true)
            putString(KEY_ACCESS, access)
            if (!refresh.isNullOrBlank()) putString(KEY_REFRESH, refresh)
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSec * 1000L)
            if (!userId.isNullOrBlank()) putString(KEY_USER_ID, userId)
            if (!login.isNullOrBlank()) putString(KEY_LOGIN, login)
            if (!displayName.isNullOrBlank()) putString(KEY_DISPLAY_NAME, displayName)
        }
    }

    private fun fetchProfile(accessToken: String): TwitchProfile? {
        val request = Request.Builder()
            .url("https://api.twitch.tv/helix/users")
            .header("Client-ID", clientId())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                val data = JSONObject(raw).optJSONArray("data") ?: return null
                val user = data.optJSONObject(0) ?: return null
                TwitchProfile(
                    userId = user.optString("id"),
                    login = user.optString("login"),
                    displayName = user.optString("display_name").ifBlank { user.optString("login") },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchProfile failed", e)
            null
        }
    }

    private fun validateToken(accessToken: String): Boolean {
        val request = Request.Builder()
            .url(VALIDATE_URL)
            .header("Authorization", "OAuth $accessToken")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseOAuthError(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun randomState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class TwitchProfile(
        val userId: String,
        val login: String,
        val displayName: String,
    )
}

sealed class TwitchAuthOutcome {
    data class Ready(
        val accessToken: String,
        val userId: String?,
        val login: String?,
        val displayName: String?,
    ) : TwitchAuthOutcome()

    data class Failed(val message: String) : TwitchAuthOutcome()
}
