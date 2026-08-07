package com.macrotracker.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Trampoline for [PackageInstaller] commit results.
 *
 * Starting the system confirmation UI from a manifest [android.content.BroadcastReceiver]
 * is unreliable on several OEMs (background-activity restrictions). An Activity PendingIntent
 * stays eligible to launch [PackageInstaller.STATUS_PENDING_USER_ACTION] confirmations.
 *
 * Successful silent self-updates never show UI — this Activity finishes immediately.
 */
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleInstallStatus(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallStatus(intent)
        finish()
    }

    private fun handleInstallStatus(intent: Intent?) {
        if (intent == null) return
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
                    runCatching { startActivity(confirmIntent) }
                        .onFailure { Log.e(TAG, "Failed to open install confirmation", it) }
                } else {
                    Log.e(TAG, "PENDING_USER_ACTION without confirmation intent")
                    toast("Update needs confirmation, but the system installer UI was missing.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update installed successfully")
            }
            else -> {
                Log.e(TAG, "Update install failed status=$status message=$message")
                val detail = message.ifBlank { "status $status" }
                toast("Update install failed: $detail")
            }
        }
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
        private const val TAG = "UpdateInstall"
    }
}
