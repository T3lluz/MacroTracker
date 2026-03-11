package com.macrotracker.widget

import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
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
 * WorkManager worker that periodically refreshes all DailyDash dashboard widgets.
 * F1 data is warmed through the shared repository so widgets stay populated even when the API is slow.
 * Dashboard data is fully refreshed (including AI insights) via [DashboardWidgetDataProvider.refreshNow].
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Refresh dashboard data (local + AI insights in parallel)
        runCatching { DashboardWidgetDataProvider.refreshNow(context) }

        val hasF1Widgets = hasAnyF1Widgets(context)
        val fetchedF1 = if (hasF1Widgets) F1WidgetDataProvider.refreshNow(context, force = true) else false

        // Re-render all widgets with the freshly cached data
        DashboardWidget().updateAll(context)
        MacrosWidget().updateAll(context)
        HealthWidget().updateAll(context)
        WeatherWidget().updateAll(context)
        CalendarWidget().updateAll(context)
        if (hasF1Widgets) {
            F1CountdownWidget().updateAll(context)
            F1StandingsWidget().updateAll(context)
            F1ScheduleWidget().updateAll(context)
        }

        return if (hasF1Widgets && !fetchedF1 && !F1WidgetDataProvider.hasCachedData(context)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "dashboard_widget_refresh"
        private const val IMMEDIATE_F1_WORK_NAME = "f1_widget_refresh_now"
        private const val IMMEDIATE_DASH_WORK_NAME = "dashboard_widget_refresh_now"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun noConstraints() = Constraints.Builder().build()

        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Enqueue a one-time immediate refresh for all dashboard (non-F1) widgets.
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

        private fun hasAnyF1Widgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, F1CountdownWidgetReceiver::class.java)).isNotEmpty() ||
                manager.getAppWidgetIds(ComponentName(context, F1StandingsWidgetReceiver::class.java)).isNotEmpty() ||
                manager.getAppWidgetIds(ComponentName(context, F1ScheduleWidgetReceiver::class.java)).isNotEmpty()
        }
    }
}
