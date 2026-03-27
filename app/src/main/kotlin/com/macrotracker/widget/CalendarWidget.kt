package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity

/**
 * Calendar / Events widget — 2×2 to 5×3.
 * TINY    (2×2)   — event count hero + next event pill
 * COMPACT (*×2)   — header + next event card + second event or summary
 * MEDIUM  (2-3×3) — header + AI + today summary + event list
 * FULL    (4-5×3) — header + AI + today card + event list
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { CalendarRoot(data) } }
    }
}

@Composable
private fun CalendarRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> CalTiny(data, c, sc)
            WSize.COMPACT -> CalCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> CalMedium(data, c, sc)
            WSize.FULL    -> CalFull(data, c, sc)
        }
    }
}

// ── TINY ─────────────────────────────────────────────────────────
@Composable
private fun CalTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📅", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (!d.hasCalendarData) {
            Text("No events", style = TextStyle(fontSize = sc.fsm, color = c.sub))
        } else {
            Text(
                "${d.eventsToday}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.event),
            )
            Text(
                if (d.eventsToday == 1) "event today" else "events today",
                style = TextStyle(fontSize = sc.fsm, color = c.sub),
            )
            if (d.nextEventTitle != null) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Box(
                    GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
                        .padding(horizontal = sc.spaceSm, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d.nextEventTitle.take(10), style = TextStyle(fontSize = sc.fxs, color = c.event), maxLines = 1)
                }
            }
        }
    }
}

// ── COMPACT ──────────────────────────────────────────────────────
@Composable
private fun CalCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Calendar", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.event)
        if (!d.hasCalendarData || d.nextEventTitle == null) {
            Spacer(GlanceModifier.defaultWeight())
            Box(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = TextStyle(fontSize = sc.iconMd))
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text("All clear today", style = TextStyle(fontSize = sc.fsm, color = c.sub))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Featured next event
            val first = d.upcomingEvents.firstOrNull() ?: CalendarEvent(
                title = d.nextEventTitle, time = d.nextEventTime ?: "",
                relativeDay = d.nextEventRelativeDay ?: "Soon", isAllDay = false,
            )
            EventRow(first, c, sc, primary = true)
            // Second event or today count
            val second = d.upcomingEvents.getOrNull(1)
            if (second != null) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                EventRow(second, c, sc, primary = false)
            } else {
                Spacer(GlanceModifier.height(sc.spaceXs))
                TodayCountRow(d, c, sc, w)
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── MEDIUM ───────────────────────────────────────────────────────
@Composable
private fun CalMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val isNarrow = sz.width < 200.dp
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Calendar", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.event)
        if (!d.aiInsightCalendar.isNullOrBlank() && !isNarrow) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightCalendar, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Today summary
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", style = TextStyle(fontSize = sc.iconMd))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text("${d.eventsToday}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.event))
            Spacer(GlanceModifier.width(sc.spaceXs))
            Text("event${if (d.eventsToday != 1) "s" else ""} today", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            if (d.eventsToday > 0) {
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(999.dp).background(c.event).padding(horizontal = sc.spaceSm, vertical = 2.dp)) {
                    Text("Busy", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.bg))
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        SectionLabel("UPCOMING", c.event, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (!d.hasCalendarData || d.upcomingEvents.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🗓", "No upcoming events", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.upcomingEvents) { event ->
                    EventRow(event, c, sc, primary = false)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── FULL ─────────────────────────────────────────────────────────
@Composable
private fun CalFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Calendar", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt, accent = c.event)
        if (!d.aiInsightCalendar.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightCalendar, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Today summary card
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📅", style = TextStyle(fontSize = sc.iconHero))
                Spacer(GlanceModifier.width(sc.spaceMd))
                Column(GlanceModifier.defaultWeight()) {
                    Text("${d.eventsToday}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.event))
                    Text("event${if (d.eventsToday != 1) "s" else ""} today", style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                }
                Box(
                    GlanceModifier.cornerRadius(sc.cornerSm)
                        .background(if (d.eventsToday > 0) c.event else c.pill)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                ) {
                    Text(
                        if (d.eventsToday > 0) "Busy" else "Free",
                        style = TextStyle(
                            fontSize = sc.fsm, fontWeight = FontWeight.Bold,
                            color = if (d.eventsToday > 0) c.bg else c.sub,
                        ),
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        SectionLabel("UPCOMING EVENTS", c.event, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (!d.hasCalendarData || d.upcomingEvents.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Box(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                NoDataPlaceholder("🗓", "No upcoming events", c, sc)
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.upcomingEvents) { event ->
                    EventRow(event, c, sc, primary = false)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── Shared composables ───────────────────────────────────────────

@Composable
private fun EventRow(event: CalendarEvent, c: WidgetClr, sc: WScale, primary: Boolean) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Date block
            val dayLabel = when (event.relativeDay) {
                "Today" -> "TDY"; "Tmrw" -> "TMW"
                else -> event.relativeDay.take(3).uppercase()
            }
            val dayNum = event.date.substringAfterLast(" ")
            Column(
                GlanceModifier.cornerRadius(sc.cornerSm)
                    .background(if (primary) c.event else c.pill)
                    .padding(horizontal = sc.spaceXs + 2.dp, vertical = sc.spaceXs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayLabel,
                    style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = if (primary) c.bg else c.event),
                    maxLines = 1,
                )
                if (dayNum.isNotEmpty()) {
                    Text(
                        dayNum,
                        style = TextStyle(fontSize = sc.fsm, fontWeight = FontWeight.Bold, color = if (primary) c.bg else c.text),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            // Event details
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    event.title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = if (primary) sc.fmd else sc.fsm, color = c.text),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
                            .padding(horizontal = sc.spaceXs + 2.dp, vertical = 1.dp),
                    ) {
                        Text(event.time, style = TextStyle(fontSize = sc.fxs, color = c.event), maxLines = 1)
                    }
                    if (event.date.isNotEmpty() && event.relativeDay != "Today" && event.relativeDay != "Tmrw") {
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text("· ${event.date}", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    }
                }
            }
            if (event.isAllDay) {
                Spacer(GlanceModifier.width(sc.spaceXs))
                Box(GlanceModifier.cornerRadius(999.dp).background(c.event).padding(horizontal = sc.spaceSm, vertical = 1.dp)) {
                    Text("All day", style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = c.bg), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TodayCountRow(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (d.eventsToday > 0) "🗓" else "✅", style = TextStyle(fontSize = sc.iconSm))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text(
                if (d.eventsToday > 0) "${d.eventsToday} event${if (d.eventsToday != 1) "s" else ""} today"
                else "Clear schedule today",
                style = TextStyle(
                    fontWeight = FontWeight.Bold, fontSize = sc.fsm,
                    color = if (d.eventsToday > 0) c.event else c.text,
                ),
                maxLines = 1,
            )
            if (w >= 260.dp && d.eventsToday > 0) {
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(999.dp).background(c.event).padding(horizontal = sc.spaceSm, vertical = 2.dp)) {
                    Text("${d.eventsToday}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.bg))
                }
            }
        }
    }
}
