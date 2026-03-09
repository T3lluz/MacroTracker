package com.macrotracker.data.youtube

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleSignInState(
    val isSignedIn: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val accessToken: String? = null,
)

@Singleton
class GoogleSignInManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS = "google_account_prefs"
        private const val KEY_EMAIL = "selected_email"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _signInState = MutableStateFlow(restorePersistedAccount())
    val signInState: StateFlow<GoogleSignInState> = _signInState

    /**
     * Returns an Intent that opens the system account picker showing all Google accounts
     * already logged in on this device — no OAuth, no SHA-1, no error code 10.
     */
    fun getSignInIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            /* selectedAccount = */ null,
            /* allowableAccounts = */ null,
            /* allowableAccountTypes = */ arrayOf("com.google"),
            /* descriptionOverrideText = */ "Choose a Google account",
            /* addAccountAuthTokenType = */ null,
            /* addAccountRequiredFeatures = */ null,
            /* addAccountOptions = */ null,
        )

    /**
     * Called with the result Intent from the account picker.
     * Extracts the chosen account name (email) and updates state.
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInState> {
        if (data == null) return Result.failure(Exception("Sign-in cancelled"))
        val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?: return Result.failure(Exception("No account selected"))
        val displayName = email.substringBefore("@")
            .replaceFirstChar { it.uppercaseChar() }
        val state = GoogleSignInState(
            isSignedIn = true,
            displayName = displayName,
            email = email,
            photoUrl = null,
        )
        prefs.edit().putString(KEY_EMAIL, email).apply()
        _signInState.value = state
        return Result.success(state)
    }

    fun signOut() {
        prefs.edit().remove(KEY_EMAIL).apply()
        _signInState.value = GoogleSignInState()
    }

    private fun restorePersistedAccount(): GoogleSignInState {
        val email = prefs.getString(KEY_EMAIL, null) ?: return GoogleSignInState()
        return GoogleSignInState(
            isSignedIn = true,
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercaseChar() },
            email = email,
        )
    }
}
