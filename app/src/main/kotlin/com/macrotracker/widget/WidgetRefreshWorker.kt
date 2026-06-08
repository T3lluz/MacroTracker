package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

        return if (hasF1 && !F1WidgetDataProvider.hasCachedData(context)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val IMMEDIATE_DASH_WORK_NAME = "dashboard_widget_refresh_now"
        private const val IMMEDIATE_F1_WORK_NAME = "f1_widget_refresh_now"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun noConstraints() = Constraints.Builder().build()

        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
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
