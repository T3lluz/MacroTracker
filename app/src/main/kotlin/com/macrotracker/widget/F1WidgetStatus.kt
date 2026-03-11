package com.macrotracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.ui.util.relativeTimeString
import java.time.Instant

@Composable
internal fun F1WidgetStatusTag(data: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier
            .cornerRadius(999.dp)
            .background(if (data.isStale) c.cardAlt else c.card)
            .padding(horizontal = sc.spaceSm, vertical = 2.dp),
    ) {
        Text(
            text = when {
                data.lastUpdatedAt <= 0L && data.isLoading -> "syncing"
                data.lastUpdatedAt <= 0L -> "waiting"
                data.isStale -> "⏱ ${relativeTimeString(Instant.ofEpochMilli(data.lastUpdatedAt))} · cached"
                else -> "⏱ ${relativeTimeString(Instant.ofEpochMilli(data.lastUpdatedAt))}"
            },
            style = TextStyle(
                fontSize = sc.fxs,
                fontWeight = if (data.isLoading) FontWeight.Bold else FontWeight.Medium,
                color = if (data.isStale) c.gold else c.sub,
            ),
            maxLines = 1,
        )
    }
}

/** Compact status label text for embedding inside a header Row, right before the reload button. */
internal fun statusTagText(data: F1WidgetData): String = when {
    data.lastUpdatedAt <= 0L && data.isLoading -> "syncing…"
    data.lastUpdatedAt <= 0L -> "—"
    data.isStale -> "⏱ ${relativeTimeLabel(data.lastUpdatedAt)} · old"
    else -> "⏱ ${relativeTimeLabel(data.lastUpdatedAt)}"
}

internal fun f1WidgetEmptyMessage(data: F1WidgetData, fallback: String): String = when {
    data.isLoading && data.lastUpdatedAt <= 0L -> "Fetching F1 data…"
    data.lastUpdatedAt > 0L && data.isStale -> "Showing cached F1 data"
    else -> fallback
}

