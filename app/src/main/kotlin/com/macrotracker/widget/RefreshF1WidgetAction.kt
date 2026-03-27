package com.macrotracker.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refresh callback used by all F1 widgets.
 *
 * Mirrors the in-app pull-to-refresh pipeline:
 *  1. Show a feedback toast.
 *  2. Invalidate the in-memory widget cache.
 *  3. Attempt an immediate network fetch (same as HomeViewModel.refreshAll).
 *  4. Re-enqueue via WorkManager with replace=true — identical to the background
 *     worker that the app uses for periodic updates, so rate-limits apply correctly.
 *  5. Re-render all three F1 widgets with the freshest available data.
 */
class RefreshF1WidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // 1. Give user feedback
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Refreshing F1 data...", Toast.LENGTH_SHORT).show()
        }

        // 2. Invalidate in-memory cache
        F1WidgetDataProvider.invalidate()

        // 3. Attempt immediate force-refresh from the network (best-effort; failures are silent)
        runCatching { F1WidgetDataProvider.refreshNow(context, force = true) }

        // 4. Also enqueue via WorkManager with replace=true — matches the in-app refresh path
        //    so the full background worker pipeline (retry, backoff, network constraint) is used
        WidgetRefreshWorker.enqueueImmediateF1Refresh(context, replace = true)

        // 5. Re-render only placed F1 widgets
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_COUNTDOWN))
            F1CountdownWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_STANDINGS))
            F1StandingsWidget().updateAll(context)
        if (WidgetStateProvider.isInstalled(context, WidgetStateProvider.WidgetType.F1_SCHEDULE))
            F1ScheduleWidget().updateAll(context)
    }
}
