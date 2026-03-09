package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to refresh all DailyDash widgets from anywhere in the app.
 */
object WidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        withContext(Dispatchers.Main) {
            DashboardWidget().updateAll(context)
            MacrosWidget().updateAll(context)
            HealthWidget().updateAll(context)
            WeatherWidget().updateAll(context)
            CalendarWidget().updateAll(context)
        }
    }
}

