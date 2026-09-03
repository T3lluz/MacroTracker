package com.macrotracker.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically refreshes all DailyDash widgets.
 *
 * Only refreshes data / re-renders widget types that are actually placed
 * on the home screen (via [WidgetStateProvider]).
 *
 * Dashboard data is fully refreshed (including AI insights) via
 * [DashboardWidgetDataProvider.refreshNow]. F1 data is warmed through
 * [F1WidgetDataProvider].
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hasDash = WidgetStateProvider.hasAnyDashboardWidget(context)
        val hasF1   = WidgetStateProvider.hasAnyF1Widget(context)

        // If no widgets are placed at all, succeed immediately
        if (!hasDash && !hasF1) return Result.success()

        // Countdown tick: redraw the F1 widgets from cache so the timer stays
        // honest, without re-hitting the network.
        if (inputData.getBoolean(KEY_RENDER_ONLY, false)) {
            if (!hasF1) return Result.success()
            renderF1Widgets(context)
            scheduleCountdownTick(context, F1WidgetDataProvider.loadData(context).secondsToNextSession())
            return Result.success()
        }

        // Refresh dashboard data (local + AI insights in parallel)
        if (hasDash) {
            runCatching { DashboardWidgetDataProvider.refreshNow(context) }
        }

        // Refresh F1 data
        if (hasF1) {
            F1WidgetDataProvider.refreshNow(context, force = true)
        }

        // Re-render only placed widgets
        if (hasDash) {
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.DASHBOARD))
                DashboardWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.MACROS))
                MacrosWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.HEALTH))
                HealthWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.WEATHER))
                WeatherWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.CALENDAR))
                CalendarWidget().updateAll(context)
        }
        if (hasF1) {
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_COUNTDOWN))
                F1CountdownWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_STANDINGS))
                F1StandingsWidget().updateAll(context)
            if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_SCHEDULE))
                F1ScheduleWidget().updateAll(context)
        }

        // Re-arm the midnight pass; a periodic run is the cheapest place to do it.
        scheduleDayRollover(context)
        if (hasF1) {
            scheduleCountdownTick(context, F1WidgetDataProvider.loadData(context).secondsToNextSession())
        }

        // Only worth retrying if a network exists — without one, the F1 fetch
        // can never succeed and retrying just burns battery in a backoff loop.
        return if (hasF1 && !F1WidgetDataProvider.hasCachedData(context) && hasNetwork(context)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun renderF1Widgets(context: Context) {
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_COUNTDOWN))
            F1CountdownWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_STANDINGS))
            F1StandingsWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_SCHEDULE))
            F1ScheduleWidget().updateAll(context)
    }

    /** True when a network with internet is currently available. */
    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        /** WorkManager's minimum periodic interval. */
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val ROLLOVER_WORK_NAME = "widget_day_rollover"
        private const val COUNTDOWN_WORK_NAME = "f1_countdown_tick"

        /** Input flag: redraw from cache instead of refetching. */
        const val KEY_RENDER_ONLY = "render_only"
        private const val IMMEDIATE_DASH_WORK_NAME = "dashboard_widget_refresh_now"
        private const val IMMEDIATE_F1_WORK_NAME = "f1_widget_refresh_now"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun noConstraints() = Constraints.Builder().build()

        /**
         * Periodic pass over every placed widget.
         *
         * **No network constraint.** Macros, health and calendar are read from
         * the device; gating the whole pass on connectivity meant a phone in
         * airplane mode showed yesterday's totals indefinitely. The network-only
         * parts (weather, F1, AI tips) fall back to their caches on their own.
         *
         * 15 minutes is WorkManager's floor for periodic work, and matches how
         * often the underlying sources actually move.
         */
        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(noConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            scheduleDayRollover(context)
        }

        /**
         * One-shot pass just after local midnight.
         *
         * Every "today" number in the widgets — macro totals, steps, the
         * calendar's event count — silently becomes wrong at the day boundary,
         * and the periodic worker only happened to fix it up to 30 minutes
         * later. Re-armed on each periodic run.
         */
        fun scheduleDayRollover(context: Context) {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1)
                .atStartOfDay(now.zone)
                .plusMinutes(1)
            val delayMs = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(60_000L)

            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ROLLOVER_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Enqueue a one-time immediate refresh for all placed widgets.
         * No network constraint — local data loads instantly; AI will use cached
         * insights if offline.
         */
        fun enqueueImmediateRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_DASH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueImmediateF1Refresh(context: Context, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_F1_WORK_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Keep the F1 countdown honest near a session.
         *
         * A Glance widget does not tick — it only redraws when something updates
         * it, so a "2h 14m" timer stayed on screen unchanged until the next
         * 15-minute pass. Redraw at the resolution the countdown is actually
         * showing, and only bother when a session is close: far from one, the
         * periodic pass is plenty.
         */
        fun scheduleCountdownTick(context: Context, secondsUntilSession: Long) {
            if (secondsUntilSession <= 0L) {
                // Session is live or unknown — nothing to count down to.
                WorkManager.getInstance(context).cancelUniqueWork(COUNTDOWN_WORK_NAME)
                return
            }
            val delaySeconds = when {
                secondsUntilSession <= 15 * 60 -> 60L
                secondsUntilSession <= 2 * 3600 -> 5 * 60L
                secondsUntilSession <= 12 * 3600 -> 20 * 60L
                else -> {
                    WorkManager.getInstance(context).cancelUniqueWork(COUNTDOWN_WORK_NAME)
                    return
                }
            }.coerceAtMost(secondsUntilSession)

            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(noConstraints())
                .setInputData(Data.Builder().putBoolean(KEY_RENDER_ONLY, true).build())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                COUNTDOWN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ROLLOVER_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(COUNTDOWN_WORK_NAME)
        }

        /**
         * Cancel the periodic worker only if no widgets of any type remain.
         * Call from receiver `onDisabled` callbacks.
         */
        fun cancelPeriodicRefreshIfNoWidgets(context: Context) {
            if (!WidgetStateProvider.hasAnyWidget(context)) {
                cancelPeriodicRefresh(context)
            }
        }
    }
}
