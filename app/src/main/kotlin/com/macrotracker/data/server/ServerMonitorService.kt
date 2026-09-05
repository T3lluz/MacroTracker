package com.macrotracker.data.server

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.macrotracker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Keeps the live server notification on screen.
 *
 * Runs as a `specialUse` foreground service rather than `dataSync`: Android 15
 * caps dataSync at six hours per day, which would silently kill an always-on
 * monitor part-way through the afternoon. Continuous monitoring of a
 * user-configured host is exactly the case specialUse exists for.
 *
 * While the screen is off the repository drops to the slower background
 * cadence — nobody is reading the notification, and a 5-second SSH round trip
 * all night is a meaningful battery cost.
 */
@AndroidEntryPoint
class ServerMonitorService : Service() {

    @Inject lateinit var repository: ServerMonitorRepository

    @Inject lateinit var notifier: ServerNotifier

    @Inject lateinit var store: ServerStore

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var started = false

    /** Mirrors screen state into the repository's polling cadence. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> repository.setBackgroundMode(false)
                Intent.ACTION_SCREEN_OFF -> repository.setBackgroundMode(true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            store.updateSettings { it.copy(liveNotificationEnabled = false) }
            stopSelf()
            return START_NOT_STICKY
        }

        // Must post a notification within a few seconds of startForegroundService,
        // so go up with a placeholder before the first poll has any data.
        if (!started) {
            startForegroundCompat(buildNotification(null))
            started = true
            repository.acquire(TAG)
            observeRuntimes()
        }
        return START_STICKY
    }

    private fun observeRuntimes() {
        collectJob?.cancel()
        collectJob = scope.launch {
            repository.runtimes.collectLatest { runtimes ->
                val settings = store.settings.value
                val selected = settings.liveNotificationServerId?.let { runtimes[it] }
                    ?: runtimes.values.firstOrNull { it.profile.enabled }
                    ?: runtimes.values.firstOrNull()
                if (selected == null) {
                    // Every server was deleted — nothing left to display.
                    stopSelf()
                    return@collectLatest
                }
                runCatching {
                    NotificationManagerCompat.from(this@ServerMonitorService)
                        .notify(ServerNotifier.LIVE_NOTIFICATION_ID, buildNotification(selected))
                }
            }
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ServerNotifier.LIVE_NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(runtime: ServerRuntime?): android.app.Notification {
        val collapsed = RemoteViews(packageName, R.layout.notification_server_live_collapsed)
        val expanded = RemoteViews(packageName, R.layout.notification_server_live_expanded)

        val title = runtime?.profile?.label ?: getString(R.string.server_channel_live)
        val statusLine = statusLine(runtime)

        collapsed.setTextViewText(R.id.server_live_title, title)
        collapsed.setTextViewText(R.id.server_live_summary, statusLine)

        expanded.setTextViewText(R.id.server_live_header, title)
        expanded.setTextViewText(
            R.id.server_live_subhead,
            runtime?.let { it.profile.displayTarget + hostSuffix(it) } ?: "",
        )
        expanded.setTextViewText(R.id.server_live_footer, statusLine)

        if (runtime != null) {
            collapsed.setImageViewBitmap(
                R.id.server_live_gauges,
                ServerLiveGraphics.renderGaugeStrip(runtime),
            )
            expanded.setImageViewBitmap(R.id.server_live_panel, ServerLiveGraphics.renderPanel(runtime))
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ServerMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, ServerNotifier.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_server_live)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(notifier.openServersIntent(runtime?.profile?.id))
            .addAction(0, getString(R.string.server_live_stop), stopIntent)
            .build()
    }

    private fun statusLine(runtime: ServerRuntime?): String {
        if (runtime == null) return "Starting…"
        return when (val connection = runtime.connection) {
            is ServerConnectionState.Offline -> connection.reason.message
            is ServerConnectionState.Connecting -> "Connecting…"
            else -> {
                val snapshot = runtime.snapshot ?: return "Waiting for first sample…"
                buildList {
                    snapshot.cpu?.let { add("CPU ${it.totalPercent.roundToInt()}%") }
                    snapshot.memory?.let { add("RAM ${it.usedPercent.roundToInt()}%") }
                    snapshot.disks.maxByOrNull { it.usedPercent }?.let {
                        add("${it.mountPoint} ${it.usedPercent.roundToInt()}%")
                    }
                    snapshot.network?.let { add("↓${formatRate(it.rxBytesPerSec)}") }
                }.joinToString(" · ").ifBlank { "Collecting…" }
            }
        }
    }

    private fun hostSuffix(runtime: ServerRuntime): String {
        val pretty = runtime.hostProfile?.prettyName.orEmpty()
        return if (pretty.isBlank()) "" else "  ·  $pretty"
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        repository.release(TAG)
        repository.setBackgroundMode(false)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching {
            NotificationManagerCompat.from(this).cancel(ServerNotifier.LIVE_NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "live-notification"
        const val ACTION_STOP = "com.macrotracker.server.STOP_LIVE"

        fun start(context: Context) {
            val intent = Intent(context, ServerMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerMonitorService::class.java))
        }
    }
}
