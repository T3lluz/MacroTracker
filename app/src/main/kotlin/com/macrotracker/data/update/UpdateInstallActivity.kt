package com.macrotracker.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Trampoline for [PackageInstaller] commit results.
 *
 * An Activity PendingIntent stays eligible to launch
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] confirmations on OEMs that block
 * background BroadcastReceivers. On success, relaunches DailyDash so the update
 * opens immediately after install.
 */
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleInstallStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallStatus(intent)
    }

    private fun handleInstallStatus(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
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
                    // Keep this activity alive until confirmation is shown.
                    // Android's silent-update throttle (often ~1h) can force this path
                    // even for same-package self-updates.
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(confirmIntent) }
                        .onSuccess {
                            Log.i(TAG, "Opened install confirmation UI")
                            // Android throttles silent self-updates; a one-tap confirm is expected
                            // when installing again within roughly an hour.
                            toast("Tap Install to finish the update")
                        }
                        .onFailure {
                            Log.e(TAG, "Failed to open install confirmation", it)
                            toast("Could not open the update confirmation screen.")
                        }
                } else {
                    Log.e(TAG, "PENDING_USER_ACTION without confirmation intent")
                    toast("Update needs confirmation, but the system installer UI was missing.")
                }
                finish()
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update installed successfully — relaunching DailyDash")
                relaunchApp()
                // Delay finish so the launch intent is delivered before we tear down.
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 250L)
            }
            else -> {
                Log.e(TAG, "Update install failed status=$status message=$message")
                val detail = message.ifBlank { "status $status" }
                toast("Update install failed: $detail")
                finish()
            }
        }
    }

    private fun relaunchApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            putExtra(EXTRA_RELAUNCHED_AFTER_UPDATE, true)
        }
        if (launch == null) {
            Log.e(TAG, "No launch intent for $packageName")
            return
        }
        runCatching { startActivity(launch) }
            .onFailure { Log.e(TAG, "Failed to relaunch after update", it) }
    }

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
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
        const val EXTRA_RELAUNCHED_AFTER_UPDATE = "relaunched_after_update"
        private const val TAG = "UpdateInstall"
    }
}
