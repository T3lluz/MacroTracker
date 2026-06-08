package com.macrotracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to refresh all DailyDash widgets from anywhere in the app.
 *
 * Only updates widget types that actually have instances placed on the home
 * screen (queried via [WidgetStateProvider]), so we never spin up Glance
 * renders for zero-instance widget types.
 *
 * Flow:
 * 1. Invalidate in-memory data cache
 * 2. Re-render all placed widgets immediately with fresh local data
 * 3. Enqueue a background worker to fetch fresh AI insights + re-render again
 */
object WidgetUpdater {

    /**
     * Full update: invalidate cache → re-render placed widgets → enqueue worker.
     * Call from the app whenever macro/health/weather/calendar data changes.
     */
    suspend fun updateAllWidgets(context: Context) {
        DashboardWidgetDataProvider.invalidate(context)
        F1WidgetDataProvider.invalidate()

        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
            updatePlacedF1Widgets(context)
        }

        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    /**
     * Re-render only the non-F1 (dashboard) widgets that are placed.
     * Useful after macro logging, health sync, etc.
     */
    suspend fun updateDashboardWidgets(context: Context) {
        DashboardWidgetDataProvider.invalidate(context)
        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
        }
    }

    /**
     * User-requested dashboard refresh: fetch live widget data before rendering so
     * the refresh button visibly updates weather/calendar/health data immediately.
     */
    suspend fun forceRefreshDashboardWidgets(context: Context) {
        DashboardWidgetDataProvider.invalidate(context)
        DashboardWidgetDataProvider.refreshNow(context)
        withContext(Dispatchers.Main) {
            updatePlacedDashboardWidgets(context)
        }
    }

    /**
     * Quick update for a single widget type after it's first placed.
     * Pre-warms data cache, then renders only that widget.
     */
    suspend fun warmAndUpdate(context: Context, widgetClass: Class<out androidx.glance.appwidget.GlanceAppWidget>) {
        DashboardWidgetDataProvider.preWarm(context)
        withContext(Dispatchers.Main) {
            widgetClass.getDeclaredConstructor().newInstance().updateAll(context)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────

    private suspend fun updatePlacedDashboardWidgets(context: Context) {
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

    private suspend fun updatePlacedF1Widgets(context: Context) {
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_COUNTDOWN))
            F1CountdownWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_STANDINGS))
            F1StandingsWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_SCHEDULE))
            F1ScheduleWidget().updateAll(context)
    }
}
