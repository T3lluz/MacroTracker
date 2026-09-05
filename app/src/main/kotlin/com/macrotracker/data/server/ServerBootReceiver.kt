package com.macrotracker.data.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brings the live notification back after a reboot, but only if the user asked
 * for it. BOOT_COMPLETED is one of the few exemptions that still allows a
 * foreground service to be started from the background on Android 12+.
 */
@AndroidEntryPoint
class ServerBootReceiver : BroadcastReceiver() {

    @Inject lateinit var store: ServerStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val settings = store.settings.value
        if (!settings.liveNotificationEnabled || !settings.startOnBoot) return
        if (store.profiles.value.none { it.enabled }) return
        runCatching { ServerMonitorService.start(context) }
    }
}
