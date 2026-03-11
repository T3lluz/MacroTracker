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
 * 2. Re-render all widgets immediately with fresh local data (instant).
 * 3. Enqueue a background worker to fetch fresh AI insights + re-render again.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 1. Invalidate in-memory cache
        DashboardWidgetDataProvider.invalidate()

        // 2. Re-render all dashboard widgets immediately (uses fast local-data path)
        DashboardWidget().updateAll(context)
        MacrosWidget().updateAll(context)
        HealthWidget().updateAll(context)
        WeatherWidget().updateAll(context)
        CalendarWidget().updateAll(context)

        // 3. Enqueue background worker to fetch fresh AI insights + APIs, then re-render
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }
}

