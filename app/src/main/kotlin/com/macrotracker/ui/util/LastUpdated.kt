package com.macrotracker.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun rememberRelativeTime(instant: Instant): String {
    var text by remember(instant) { mutableStateOf(relativeTimeString(instant)) }
    LaunchedEffect(instant) {
        while (true) {
            delay(30_000L)
            text = relativeTimeString(instant)
        }
    }
    return text
}

private fun relativeTimeString(instant: Instant): String {
    val now = Instant.now()
    val seconds = ChronoUnit.SECONDS.between(instant, now).coerceAtLeast(0)
    return when {
        seconds < 60    -> "just now"
        seconds < 3600  -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else            -> "${seconds / 86400}d"
    }
}

/**
 * Ultra-minimal last-updated indicator.
 * Renders a tiny clock icon + relative time (e.g. "· 2m") at very low opacity.
 * Designed to sit at the trailing end of a header row — visible on close inspection,
 * invisible at a glance. No word "Updated", no label — just the time.
 *
 * @param tint  Icon + text color. Pass white-alpha for colored card backgrounds.
 */
@Composable
fun LastUpdatedText(
    lastUpdatedAt: Instant?,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF99A8C2),
) {
    if (lastUpdatedAt == null) return
    val relTime = rememberRelativeTime(lastUpdatedAt)
    val dimColor = color.copy(alpha = color.alpha.coerceAtMost(1f) * 0.45f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = "Last synced $relTime ago",
            tint = dimColor,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = relTime,
            fontSize = 10.sp,
            color = dimColor,
            letterSpacing = 0.2.sp,
        )
    }
}
