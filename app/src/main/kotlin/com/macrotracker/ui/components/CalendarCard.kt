package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.CalendarUiState

private val CalendarAccent = Color(0xFF4285F4) // Google blue

@Composable
fun CalendarCard(
    state: CalendarUiState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { MacroMotion.contentEnter togetherWith MacroMotion.contentExit },
        label = "calendarContent",
        modifier = modifier,
    ) { currentState ->
        when (currentState) {
            is CalendarUiState.Loading -> {
                // Don't show anything while loading to avoid flicker
            }

            is CalendarUiState.Success -> {
                val events = currentState.events
                val upcomingEvents = currentState.upcomingEvents
                if (events.isEmpty() && upcomingEvents.isEmpty()) {
                    // No events today or upcoming — show a clean compact card
                    MacroCard(delayMs = 125) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = CalendarAccent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Calendar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                Text(
                                    "No events this week — enjoy the free time!",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    }
                } else if (events.isEmpty() && upcomingEvents.isNotEmpty()) {
                    UpcomingEventsCard(events = upcomingEvents)
                } else {
                    CalendarEventsCard(events = events)
                }
            }

            is CalendarUiState.PermissionRequired -> {
                MacroCard(delayMs = 125) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Calendar",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                "Allow calendar access to see today's events",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        MacroButton(
                            text = "📅 Enable",
                            onClick = onRequestPermission,
                            variant = ButtonVariant.PRIMARY,
                        )
                    }
                }
            }

            is CalendarUiState.Unavailable -> {
                // Don't render anything
            }
        }
    }
}

@Composable
private fun CalendarEventsCard(events: List<CalendarEvent>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    // Show up to 2 events collapsed, all when expanded
    val nextEvent = events.firstOrNull { it.isHappeningNow } ?: events.firstOrNull()
    val upcomingEvents = if (nextEvent != null) events.filter { it != nextEvent } else events
    val visibleUpcoming = if (expanded) upcomingEvents else upcomingEvents.take(1)
    val hiddenCount = upcomingEvents.size - visibleUpcoming.size

    MacroCard(delayMs = 125) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (expanded) haptics.toggleOff() else haptics.toggleOn()
                    expanded = !expanded
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = CalendarAccent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Today's Schedule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${events.size} event${if (events.size != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Featured / current event
        if (nextEvent != null) {
            EventTile(
                event = nextEvent,
                featured = true,
            )
        }

        // Additional events
        AnimatedVisibility(
            visible = visibleUpcoming.isNotEmpty(),
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column {
                visibleUpcoming.forEach { event ->
                    Spacer(modifier = Modifier.height(8.dp))
                    EventTile(event = event, featured = false)
                }
            }
        }

        // "Show more" / "Show less" hint
        if (upcomingEvents.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (expanded) "Tap header to collapse" else "+$hiddenCount more event${if (hiddenCount != 1) "s" else ""} · Tap to expand",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (expanded) haptics.toggleOff() else haptics.toggleOn()
                        expanded = !expanded
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EventTile(
    event: CalendarEvent,
    featured: Boolean,
) {
    val eventColor = try {
        Color(event.calendarColor).copy(alpha = 1f)
    } catch (_: Exception) {
        CalendarAccent
    }
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (featured) {
                    Modifier
                        .background(Background, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                } else {
                    Modifier
                        .background(Background.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // Color indicator line
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(if (featured) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(eventColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = event.title,
                    fontSize = if (featured) 15.sp else 14.sp,
                    fontWeight = if (featured) FontWeight.Bold else FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (event.isHappeningNow) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(eventColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            // Always show date and time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = event.formattedDateAndTime,
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            if (event.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.location,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Meeting link
            val link = event.meetingLink
            if (link != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        try { uriHandler.openUri(link) } catch (_: Exception) { }
                    },
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = CalendarAccent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val displayLink = when {
                        link.contains("meet.google.com") -> "Google Meet"
                        link.contains("zoom.us") || link.contains("zoom.") -> "Zoom Meeting"
                        link.contains("teams.microsoft") || link.contains("teams.live") -> "Teams Meeting"
                        link.contains("webex") -> "Webex Meeting"
                        else -> "Join Meeting"
                    }
                    Text(
                        text = displayLink,
                        fontSize = 12.sp,
                        color = CalendarAccent,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Description snippet (only for featured)
            if (featured && event.description.isNotBlank() && link == null) {
                // Show a short snippet of the description if there's no meeting link
                val snippet = event.description
                    .replace(Regex("<[^>]*>"), "") // strip HTML
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(80)
                if (snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (snippet.length >= 80) "$snippet…" else snippet,
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingEventsCard(events: List<CalendarEvent>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val firstEvent = events.first()
    val restEvents = events.drop(1)
    val visibleRest = if (expanded) restEvents else restEvents.take(1)
    val hiddenCount = restEvents.size - visibleRest.size

    MacroCard(delayMs = 125) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (restEvents.size > 1) {
                        Modifier.clickable {
                            if (expanded) haptics.toggleOff() else haptics.toggleOn()
                            expanded = !expanded
                        }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.EventNote,
                    contentDescription = null,
                    tint = CalendarAccent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Nothing today",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        "Next up on your calendar",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
            }
            if (restEvents.size > 1) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Featured next event
        EventTile(
            event = firstEvent,
            featured = true,
        )

        // Additional upcoming events
        AnimatedVisibility(
            visible = visibleRest.isNotEmpty(),
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column {
                visibleRest.forEach { event ->
                    Spacer(modifier = Modifier.height(8.dp))
                    EventTile(
                        event = event,
                        featured = false,
                    )
                }
            }
        }

        // "Show more" / "Show less" hint
        if (restEvents.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (expanded) "Tap header to collapse" else "+$hiddenCount more event${if (hiddenCount != 1) "s" else ""} · Tap to expand",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (expanded) haptics.toggleOff() else haptics.toggleOn()
                        expanded = !expanded
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
