package com.macrotracker.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Receives PackageInstaller commit results for in-app self-updates.
 *
 * When the system still requires confirmation (rare for same-package updates with
 * [android.Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION]), we launch the
 * confirmation intent. Prefer silent commit so Play Protect's "Scan app" UI is skipped.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMPLETE) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        Log.i(TAG, "Install status=$status message=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = intent.confirmationIntent()
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                        .onFailure { Log.e(TAG, "Failed to open install confirmation", it) }
                } else {
                    Log.e(TAG, "PENDING_USER_ACTION without confirmation intent")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update installed successfully")
            }
            else -> {
                Log.e(TAG, "Update install failed status=$status message=$message")
            }
        }
    }

    private fun Intent.confirmationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }
    }

    companion object {
        const val ACTION_INSTALL_COMPLETE = "com.macrotracker.action.UPDATE_INSTALL_COMPLETE"
        private const val TAG = "UpdateInstall"
    }
}
