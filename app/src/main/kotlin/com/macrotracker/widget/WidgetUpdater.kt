package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to refresh all DailyDash widgets from anywhere in the app.
 *
 * Invalidates the in-memory data cache first so widgets read fresh local data,
 * then re-renders all widgets. Also enqueues a background worker to fetch
 * fresh AI insights and API data.
 *
 * F1 widgets are skipped when none are installed to avoid unnecessary Glance renders.
 */
object WidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        // Invalidate so provideGlance re-reads local data instead of stale cache
        DashboardWidgetDataProvider.invalidate()

        withContext(Dispatchers.Main) {
            DashboardWidget().updateAll(context)
            MacrosWidget().updateAll(context)
            HealthWidget().updateAll(context)
            WeatherWidget().updateAll(context)
            CalendarWidget().updateAll(context)
            if (hasAnyF1Widgets(context)) {
                F1CountdownWidget().updateAll(context)
                F1StandingsWidget().updateAll(context)
                F1ScheduleWidget().updateAll(context)
            }
        }

        // Enqueue background worker to fetch fresh AI insights + re-render again
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    private fun hasAnyF1Widgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, F1CountdownWidgetReceiver::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, F1StandingsWidgetReceiver::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, F1ScheduleWidgetReceiver::class.java)).isNotEmpty()
    }
}
