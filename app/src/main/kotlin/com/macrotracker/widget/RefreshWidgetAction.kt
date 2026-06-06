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
 * 1. Shows a feedback toast.
 * 2. Invalidate the in-memory cache so stale data isn't reused.
 * 3. Re-render only placed widgets immediately with fresh local data.
 * 4. Enqueue a background worker to fetch fresh AI insights + APIs (like live weather), then re-render again.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 1. Give user feedback
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Refreshing widgets...", Toast.LENGTH_SHORT).show()
        }

        // 2. Full update: invalidate, re-render, and enqueue worker
        WidgetUpdater.updateAllWidgets(context)
    }
}
