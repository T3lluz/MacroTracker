package com.macrotracker.data.youtube

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Identity [AuthorizationClient] wrapper for YouTube Data API access.
 *
 * Cloud Console setup (required once):
 * 1. Enable **YouTube Data API v3**
 * 2. Configure OAuth consent screen (External / Testing is fine for personal use)
 * 3. Create an **Android** OAuth client with package `com.macrotracker` and the
 *    tester keystore SHA-1: `0C:C3:12:0A:18:2C:5A:A1:8F:39:5F:11:DB:65:1C:7D:DF:33:91:6C`
 *
 * No client ID is embedded in the app — Play Services matches package + signing cert.
 */
@Singleton
class YouTubeGoogleAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "YouTubeGoogleAuth"
        private const val PREFS_NAME = "youtube_settings"
        private const val KEY_CONNECTED = "google_connected"
        private const val KEY_EMAIL = "google_account_email"
        private const val KEY_ACCOUNT_NAME = "google_account_name"

        const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
        private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val requestedScopes = listOf(
        Scope(SCOPE_YOUTUBE_READONLY),
        Scope(SCOPE_USERINFO_EMAIL),
    )

    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false)

    fun connectedEmail(): String? = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun markConnected(email: String?, accountName: String? = email) {
        prefs.edit {
            putBoolean(KEY_CONNECTED, true)
            putString(KEY_EMAIL, email.orEmpty())
            putString(KEY_ACCOUNT_NAME, accountName.orEmpty())
        }
    }

    fun markDisconnected() {
        prefs.edit {
            putBoolean(KEY_CONNECTED, false)
            remove(KEY_EMAIL)
            remove(KEY_ACCOUNT_NAME)
        }
    }

    /**
     * Requests a YouTube access token. Returns [AuthorizeOutcome.NeedsConsent] when the
     * user must pick an account / grant scopes — launch [AuthorizeOutcome.NeedsConsent.pendingIntent]
     * then call [completeAuthorization] with the result Intent.
     */
    suspend fun authorize(activity: Activity): AuthorizeOutcome = withContext(Dispatchers.Main) {
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .build()
            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()
            if (result.hasResolution()) {
                val pending = result.pendingIntent
                    ?: return@withContext AuthorizeOutcome.Failed("Google sign-in UI unavailable")
                AuthorizeOutcome.NeedsConsent(pending)
            } else {
                val token = result.accessToken
                    ?: return@withContext AuthorizeOutcome.Failed("No access token returned")
                val email = fetchEmail(token)
                markConnected(email)
                AuthorizeOutcome.Ready(accessToken = token, email = email)
            }
        } catch (e: ApiException) {
            Log.e(TAG, "authorize ApiException status=${e.statusCode}", e)
            AuthorizeOutcome.Failed(humanizeAuthError(e))
        } catch (e: Exception) {
            Log.e(TAG, "authorize failed", e)
            AuthorizeOutcome.Failed(e.message ?: "Google authorization failed")
        }
    }

    suspend fun completeAuthorization(activity: Activity, data: Intent?): AuthorizeOutcome =
        withContext(Dispatchers.Main) {
            if (data == null) {
                return@withContext AuthorizeOutcome.Failed("Sign-in was cancelled")
            }
            try {
                val result = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data)
                val token = result.accessToken
                    ?: return@withContext AuthorizeOutcome.Failed("No access token returned")
                val email = fetchEmail(token)
                markConnected(email)
                AuthorizeOutcome.Ready(accessToken = token, email = email)
            } catch (e: ApiException) {
                Log.e(TAG, "completeAuthorization ApiException status=${e.statusCode}", e)
                AuthorizeOutcome.Failed(humanizeAuthError(e))
            } catch (e: Exception) {
                Log.e(TAG, "completeAuthorization failed", e)
                AuthorizeOutcome.Failed(e.message ?: "Could not finish Google sign-in")
            }
        }

    suspend fun revoke(activity: Activity): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val request = RevokeAccessRequest.builder()
                .setScopes(requestedScopes)
                .build()
            Identity.getAuthorizationClient(activity)
                .revokeAccess(request)
                .await()
            markDisconnected()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "revokeAccess failed — clearing local session anyway", e)
            markDisconnected()
            // Still treat as success for UX; grants may already be gone.
            Result.success(Unit)
        }
    }

    private suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(USERINFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                json.optString("email").takeIf { it.isNotBlank() }
                    ?: json.optString("name").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch userinfo", e)
            null
        }
    }

    private fun humanizeAuthError(e: ApiException): String = when (e.statusCode) {
        // CommonStatusCodes.DEVELOPER_ERROR — almost always package/SHA-1 mismatch
        10 -> "Google OAuth misconfigured. In Cloud Console, Android client must use " +
            "package com.macrotracker and SHA-1 " +
            "0C:C3:12:0A:18:2C:5A:A1:8F:39:5F:11:DB:65:1C:7D:DF:33:91:6C " +
            "(and enable YouTube Data API v3)."
        // GoogleSignInStatusCodes.SIGN_IN_CANCELLED
        12501 -> "Sign-in was cancelled"
        // CommonStatusCodes.CANCELED
        16 -> "Sign-in was cancelled"
        // GoogleSignInStatusCodes.SIGN_IN_FAILED
        12500 -> "Google sign-in failed. Check YouTube Data API + Android OAuth client in Cloud Console."
        // CommonStatusCodes.NETWORK_ERROR
        7 -> "Network error during Google sign-in"
        // CommonStatusCodes.API_NOT_CONNECTED / SERVICE issues
        17 -> "Google Play Services outdated — update Play Services and try again"
        else -> e.message?.takeIf { it.isNotBlank() && e.statusCode != 8 }
            ?: "Google sign-in failed (code ${e.statusCode})"
    }
}

sealed class AuthorizeOutcome {
    data class Ready(val accessToken: String, val email: String?) : AuthorizeOutcome()
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthorizeOutcome()
    data class Failed(val message: String) : AuthorizeOutcome()
}
