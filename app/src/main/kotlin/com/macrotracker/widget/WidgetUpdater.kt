package com.macrotracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to refresh all DailyDash widgets from anywhere in the app.
 * F1 widgets are skipped when none are installed to avoid unnecessary Glance renders.
 */
object WidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
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
    }

    private fun hasAnyF1Widgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, F1CountdownWidgetReceiver::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, F1StandingsWidgetReceiver::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, F1ScheduleWidgetReceiver::class.java)).isNotEmpty()
    }
}
