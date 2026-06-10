package com.macrotracker.ui.util

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** When true, periodic UI tickers (countdowns, relative timestamps) pause updates. */
val LocalTickersPaused = staticCompositionLocalOf { false }

private val HOME_WIDGET_ITEM_KEYS = setOf(
    "F1", "YOUTUBE", "WEATHER", "CALENDAR", "BODY_STATS", "PROGRESS", "QUICK_ADD",
)

/** Tab slide duration in [MacroMotion] — used to defer work until navigation finishes. */
const val TAB_TRANSITION_MS = 300L

/** Extra buffer after tab transition before kicking off network refreshes. */
const val HOME_RESUME_DEFER_MS = TAB_TRANSITION_MS + 80L

fun LazyListLayoutInfo.visibleHomeWidgetIds(): Set<String> =
    visibleItemsInfo
        .mapNotNull { item -> item.key as? String }
        .filter { it in HOME_WIDGET_ITEM_KEYS }
        .toSet()

@Composable
fun rememberVisibleHomeWidgetIds(listState: LazyListState): Set<String> {
    val visibleIds by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) emptySet()
            else layoutInfo.visibleHomeWidgetIds()
        }
    }
    return visibleIds
}
