package com.macrotracker.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glance ActionCallback that manually refreshes all DailyDash widgets.
 * Triggered by the refresh button in the widget header.
 *
 * Clears location + weather caches, re-resolves the current GPS fix, fetches
 * live weather for that location, then re-renders placed dashboard widgets.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Updating location & weather…", Toast.LENGTH_SHORT).show()
        }
        WidgetUpdater.forceRefreshDashboardWidgets(context)
    }
}
