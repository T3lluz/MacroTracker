package com.macrotracker.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.macrotracker.BuildConfig
import com.macrotracker.MainActivity

/**
 * Safety net when PackageInstaller finishes replacing DailyDash.
 *
 * [UpdateInstallActivity] already tries to relaunch on success; this receiver
 * posts a tap-to-open notification if the relaunch is blocked by OEM / background
 * activity limits (common on Android 10+).
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i(TAG, "MY_PACKAGE_REPLACED — prompting user to open DailyDash ${BuildConfig.VERSION_NAME}")

        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(UpdateInstallActivity.EXTRA_RELAUNCHED_AFTER_UPDATE, true)
            putExtra(UpdateInstallActivity.EXTRA_SHOW_WHATS_NEW, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // System silhouette icon — app mipmaps are not valid notification small icons.
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("DailyDash updated")
            .setContentText("Version ${BuildConfig.VERSION_NAME} is ready — tap to open")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)

        // Best-effort relaunch; may be blocked in background on newer Android.
        runCatching {
            context.startActivity(open)
            Log.i(TAG, "Relaunch requested from MY_PACKAGE_REPLACED")
        }.onFailure {
            Log.w(TAG, "Could not relaunch from MY_PACKAGE_REPLACED (notification still posted)", it)
        }
    }

    companion object {
        private const val TAG = "PackageReplaced"
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 4_701

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "App updates",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Prompts to open DailyDash after an update installs"
                },
            )
        }

        fun cancelOpenPrompt(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIFICATION_ID)
        }
    }
}
