package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically refreshes all DailyDash dashboard widgets.
 * Ensures the widget always shows up-to-date data from all sources.
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DashboardWidget().updateAll(context)
        MacrosWidget().updateAll(context)
        HealthWidget().updateAll(context)
        WeatherWidget().updateAll(context)
        CalendarWidget().updateAll(context)
        F1CountdownWidget().updateAll(context)
        F1StandingsWidget().updateAll(context)
        F1ScheduleWidget().updateAll(context)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "dashboard_widget_refresh"

        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                15, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}


