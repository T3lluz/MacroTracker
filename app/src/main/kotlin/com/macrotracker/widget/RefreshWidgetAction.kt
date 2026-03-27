package com.macrotracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Glance ActionCallback that manually refreshes all DailyDash widgets.
 * Triggered by the refresh button in the widget header.
 *
 * 1. Invalidate the in-memory cache so stale data isn't reused.
 * 2. Re-render only placed widgets immediately with fresh local data.
 * 3. Enqueue a background worker to fetch fresh AI insights + re-render again.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 1. Invalidate in-memory cache
        DashboardWidgetDataProvider.invalidate()

        // 2. Re-render only placed dashboard widgets immediately
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

        // 3. Enqueue background worker to fetch fresh AI insights + APIs, then re-render
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }
}
