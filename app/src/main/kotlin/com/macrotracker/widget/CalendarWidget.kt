package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity

/**
 * Calendar / Events widget — 2×2 to 5×3.
 * TINY (2×2): icon + event count
 * COMPACT (*×2): header + next event title + time, width-adaptive pill
 * MEDIUM (2-3 cols, 3 rows): header + AI + today summary + next event card
 * FULL (4-5 cols, 3 rows): header + AI + today card + next event card + stat grid
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.SMALL_WIDGET)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent {
            GlanceTheme {
                CalendarWidgetRoot(data)
            }
        }
    }
}

@Composable
private fun CalendarWidgetRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.corner)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> CalendarTiny(data, c, sc)
            WSize.COMPACT -> CalendarCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> CalendarMedium(data, c, sc)
            WSize.FULL    -> CalendarFull(data, c, sc)
        }
    }
}

// ── TINY: 2×2 — icon + event count + next event time ────────────
@Composable
private fun CalendarTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📅", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (!d.hasCalendarData) {
            Text("No events", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        } else {
            Text(
                "${d.eventsToday}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.event),
            )
            Text(
                if (d.eventsToday == 1) "event today" else "events today",
                style = TextStyle(fontSize = sc.fxs, color = c.sub),
            )
            if (d.nextEventTime != null) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Text("Next: ${d.nextEventTime}", style = TextStyle(fontSize = sc.fxs, color = c.event), maxLines = 1)
            }
        }
    }
}

// ── COMPACT: *×2 — header + next event (width-adaptive) ──────────
@Composable
private fun CalendarCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "📅 Calendar", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasCalendarData || d.nextEventTitle == null) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm), contentAlignment = Alignment.Center) {
                Text("No upcoming events", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Main event card — fills remaining space
            Box(
                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Day pill
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(sc.spaceXs + 4.dp)
                            .background(c.event)
                            .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.nextEventRelativeDay ?: "Soon",
                            style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = c.bg),
                        )
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(d.nextEventTitle, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
                        if (d.nextEventTime != null) {
                            Text(d.nextEventTime, style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                    if (w >= 260.dp) {
                        Spacer(GlanceModifier.width(sc.spaceMd))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.eventsToday}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.event))
                            Text("today", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Status indicator row
            Box(
                GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (d.eventsToday > 0) "🗓" else "✅", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            if (d.eventsToday > 0) "${d.eventsToday} event${if (d.eventsToday != 1) "s" else ""} today" else "Free today",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = if (d.eventsToday > 0) c.event else c.text),
                        )
                        if (d.nextEventRelativeDay != null && d.nextEventRelativeDay != "Today") {
                            Text("Next: ${d.nextEventRelativeDay}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
        }
    }
}

// ── MEDIUM: 2×3 / 3×3 — AI + today count + upcoming events list ──
@Composable
private fun CalendarMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Calendar", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsightCalendar.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightCalendar, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Today summary row
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", style = TextStyle(fontSize = sc.iconMd))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text(
                "${d.eventsToday}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.event),
            )
            Spacer(GlanceModifier.width(sc.spaceXs))
            Text(
                "event${if (d.eventsToday != 1) "s" else ""} today",
                style = TextStyle(fontSize = sc.fsm, color = c.sub),
            )
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasCalendarData || d.upcomingEvents.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🗓", "No upcoming events", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.upcomingEvents) { event ->
                    EventRow(event, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── FULL: 4×3 / 5×3 — today summary + scrollable upcoming events list ──
@Composable
private fun CalendarFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Calendar", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsightCalendar.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightCalendar, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Today summary card
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(sc.cornerSm)
                .background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📅", style = TextStyle(fontSize = sc.iconHero))
                Spacer(GlanceModifier.width(sc.spaceMd))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        "${d.eventsToday}",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.event),
                    )
                    Text(
                        "event${if (d.eventsToday != 1) "s" else ""} today",
                        style = TextStyle(fontSize = sc.fxs, color = c.sub),
                    )
                }
                Box(
                    GlanceModifier
                        .cornerRadius(sc.cornerSm)
                        .background(if (d.eventsToday > 0) c.event else c.card)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                ) {
                    Text(
                        if (d.eventsToday > 0) "Busy" else "Free",
                        style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = if (d.eventsToday > 0) c.bg else c.sub),
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Section label
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.event)) {}
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text("UPCOMING EVENTS", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
        }
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (!d.hasCalendarData || d.upcomingEvents.isEmpty()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
                NoDataPlaceholder("🗓", "No upcoming events", c, sc)
            }
        } else {
            // Scrollable list of all upcoming events
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.upcomingEvents) { event ->
                    EventRow(event, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── Shared event row composable ───────────────────────────────────
@Composable
private fun EventRow(event: CalendarEvent, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(sc.cornerSm)
            .background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Day pill
            Box(
                modifier = GlanceModifier
                    .cornerRadius(sc.spaceXs + 4.dp)
                    .background(c.event)
                    .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    event.relativeDay.take(3),
                    style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = c.bg),
                )
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            Column(GlanceModifier.defaultWeight()) {
                Text(event.title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                Text(event.time, style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
        }
    }
}
