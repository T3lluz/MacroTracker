package com.macrotracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Glance ActionCallback that manually refreshes all DailyDash widgets.
 * Triggered by the refresh button in the widget header.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        DashboardWidget().updateAll(context)
    }
}

