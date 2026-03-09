package com.macrotracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/** Refresh callback used by all F1 widgets to trigger a data reload. */
class RefreshF1WidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        F1WidgetDataProvider.invalidate()
        F1CountdownWidget().updateAll(context)
        F1StandingsWidget().updateAll(context)
        F1ScheduleWidget().updateAll(context)
    }
}

