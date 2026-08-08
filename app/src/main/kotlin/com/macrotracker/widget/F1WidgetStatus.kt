package com.macrotracker.widget

import com.macrotracker.ui.util.relativeTimeString
import java.time.Instant

/** Compact status label text for embedding inside a header Row, right before the reload button. */
internal fun statusTagText(data: F1WidgetData): String {
    val loading = data.isLoading
    return when {
        loading && data.lastUpdatedAt <= 0L -> "Loading..."
        loading -> "Syncing..."
        data.lastUpdatedAt <= 0L -> "—"
        else -> {
            when {
                data.isStale -> "${relativeTimeString(Instant.ofEpochMilli(data.lastUpdatedAt))} · cached"
                else -> relativeTimeString(Instant.ofEpochMilli(data.lastUpdatedAt))
            }
        }
    }
}

internal fun f1WidgetEmptyMessage(data: F1WidgetData, fallback: String): String = when {
    data.isLoading && data.lastUpdatedAt <= 0L -> "Fetching F1 data…"
    data.lastUpdatedAt > 0L && data.isStale -> "Showing cached F1 data"
    else -> fallback
}
