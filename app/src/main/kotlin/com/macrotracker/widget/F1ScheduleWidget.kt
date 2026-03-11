package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F1 Schedule Widget — 3×2 to 5×3.
 *
 * All sizes ≥ COMPACT are fully scrollable.
 * Session timetable lives as a compact pill strip ABOVE the hero card —
 * keeping the hero tight and maximising the LazyColumn scroll area.
 *
 * COMPACT (*×2)  — header + session pills + scrollable race list
 * MEDIUM  (2-3×3)— header + session pills + compact hero + scrollable race list
 * FULL    (4-5×3)— header + session pills + full hero + full scrollable calendar
 */
class F1ScheduleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_TALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1ScheduleRoot(data) } }
    }
}

// ── Root ──────────────────────────────────────────────────────────
@Composable
private fun F1ScheduleRoot(data: F1WidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> ScheduleTiny(data, c, sc)
            WSize.COMPACT -> ScheduleCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> ScheduleMedium(data, c, sc)
            WSize.FULL    -> ScheduleFull(data, c, sc, sz.width)
        }
    }
}

// ── Header ────────────────────────────────────────────────────────
@Composable
private fun ScheduleHeader(title: String, data: F1WidgetData, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.flg.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.flg, color = c.text), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        val statusText = statusTagText(data)
        if (statusText.isNotBlank() && statusText != "—") {
            Box(
                GlanceModifier.cornerRadius(sc.btnCorner)
                    .background(if (data.isStale) c.cardAlt else c.card)
                    .padding(horizontal = sc.spaceSm, vertical = 2.dp),
            ) {
                Text(statusText, style = TextStyle(fontSize = sc.fxs,
                    fontWeight = FontWeight.Medium,
                    color = if (data.isStale) c.gold else c.sub), maxLines = 1)
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        Box(
            GlanceModifier.width(sc.btnSize).height(sc.btnSize).cornerRadius(sc.btnCorner)
                .background(c.card).clickable(actionRunCallback<RefreshF1WidgetAction>()).padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) { Text("↻", style = TextStyle(fontSize = sc.fmd, fontWeight = FontWeight.Bold, color = c.sub)) }
    }
}

// ── TINY: 2×2 — flag + round + countdown + locality ──────────────
@Composable
private fun ScheduleTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val next = d.schedule.firstOrNull { it.isNext } ?: d.schedule.firstOrNull { !it.isPast }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        // Flag + round on same line
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(next?.flag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
            if (next != null) {
                Spacer(GlanceModifier.width(sc.spaceXs))
                Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("R${next.round}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceXs))
        when {
            d.daysUntil == 0L -> {
                Text("TODAY", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.red))
                if (d.hoursUntil >= 0)
                    Text("${d.hoursUntil}h ${d.minutesUntil}m", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
            d.daysUntil > 0 -> {
                Text("${d.daysUntil}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                Text("days", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
            d.isLoading -> Text("SYNC", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub))
            else -> {
                Text("R${next?.round ?: "?"}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                Text(next?.locality?.take(10) ?: "F1", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
        // Locality for upcoming race
        if (next != null && d.daysUntil >= 0) {
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(next.locality.take(12), style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

// ── COMPACT: *×2 — header → pills → full scroll race list ─────────
@Composable
private fun ScheduleCompact(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("F1 ${LocalDate.now().year}", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card), contentAlignment = Alignment.Center) {
                Text(f1WidgetEmptyMessage(d, "No data"), style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                SessionPillStrip(futureSessions, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            val upcoming = d.schedule.filter { !it.isPast }
            val toShow = if (upcoming.isEmpty()) d.schedule.takeLast(3) else upcoming
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(toShow) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── MEDIUM: 2-3×3 — header → sessions → compact hero → scroll list ───
@Composable
private fun ScheduleMedium(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("${LocalDate.now().year} Calendar", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text(f1WidgetEmptyMessage(d, "No schedule data"), style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            // Detailed session table when in race weekend
            if (futureSessions.isNotEmpty()) {
                WeekendSessionTable(futureSessions, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            } else if (next != null) {
                HeroCardCompact(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            } else if (d.lastRaceResults.isNotEmpty()) {
                // No upcoming race card — show last race result
                LastRaceCard(d, c, sc, compact = true)
                Spacer(GlanceModifier.height(sc.spaceSm))
            } else if (d.lastQualiResults.isNotEmpty()) {
                // Fallback: show qualifying grid
                QualiGridCard(d, c, sc, compact = true)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            val upcoming = d.schedule.filter { !it.isPast && !it.isNext }
            val toShow = if (upcoming.isEmpty()) d.schedule.takeLast(5) else upcoming
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(toShow) { race ->
                    RaceRow(race, c, sc, showDate = true)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── FULL: 4-5×3 — header → sessions → full hero → full scroll calendar ─
@Composable
private fun ScheduleFull(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("Formula 1  ·  ${LocalDate.now().year}", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text(f1WidgetEmptyMessage(d, "No schedule data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                // Full session table in race weekend
                WeekendSessionTable(futureSessions, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            } else if (next != null) {
                HeroCardFull(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Show last race + quali cards when there's no hero to show and we have data
            if (futureSessions.isEmpty() && next == null) {
                val hasRace = d.lastRaceResults.isNotEmpty()
                val hasQuali = d.lastQualiResults.isNotEmpty()
                when {
                    hasRace && hasQuali -> {
                        // Side-by-side on wide FULL layout
                        Row(GlanceModifier.fillMaxWidth()) {
                            Box(GlanceModifier.defaultWeight()) {
                                LastRaceCard(d, c, sc, compact = true)
                            }
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Box(GlanceModifier.defaultWeight()) {
                                QualiGridCard(d, c, sc, compact = true)
                            }
                        }
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                    hasRace -> {
                        LastRaceCard(d, c, sc, compact = false)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                    hasQuali -> {
                        QualiGridCard(d, c, sc, compact = false)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                }
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text("FULL CALENDAR", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            val past  = d.schedule.filter { it.isPast }.takeLast(1)
            val shown = (past + d.schedule.filter { !it.isPast }).sortedBy { it.round }
            val toShow = if (shown.isEmpty()) d.schedule.takeLast(5) else shown
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(toShow) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── LAST RACE CARD ────────────────────────────────────────────────
/** Compact card showing the top-3 finishers from the last race. */
@Composable
private fun LastRaceCard(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            // Header row
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(d.lastRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconSm))
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(cleanRaceName(d.lastRaceName ?: "Last Race").take(if (compact) 18 else 24),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(3.dp).background(c.pill).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("RESULT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
                }
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            // Podium row: P1 / P2 / P3 side by side
            Row(GlanceModifier.fillMaxWidth()) {
                d.lastRaceResults.take(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm / 2)
                        .background(c.cardAlt)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(GlanceModifier.cornerRadius(2.dp)
                                .background(podiumColor(row.position, c))
                                .padding(horizontal = 3.dp, vertical = 1.dp)) {
                                Text("P${row.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                                    fontSize = sc.fxs, color = if (row.position <= 3) c.bg else c.text))
                            }
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                                .background(teamColorProvider(row.teamColor))) {}
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text(row.acronym, style = TextStyle(fontWeight = FontWeight.Bold,
                                fontSize = sc.fsm, color = c.text))
                            if (!compact && !row.timeOrStatus.isNullOrEmpty()) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Text(row.timeOrStatus.take(9), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                            }
                            if (row.fastestLap) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Box(GlanceModifier.cornerRadius(2.dp).background(c.accent)
                                    .padding(horizontal = 3.dp, vertical = 1.dp)) {
                                    Text("FL", style = TextStyle(fontWeight = FontWeight.Bold,
                                        fontSize = (sc.fxs.value * 0.8f).sp, color = c.bg))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── QUALIFYING GRID CARD ──────────────────────────────────────────
/** Standalone qualifying grid card for the schedule widget. */
@Composable
private fun QualiGridCard(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean) {
    if (d.lastQualiResults.isEmpty()) return
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            // Header
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(d.lastRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconSm))
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(cleanRaceName(d.lastRaceName ?: "Qualifying").take(18),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("QUALI", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                }
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            // P1-P3 rows
            d.lastQualiResults.take(if (compact) 3 else 5).forEachIndexed { i, qr ->
                if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
                Row(
                    GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.cardAlt)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(qr.position, c))
                        .padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text("${qr.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                            fontSize = sc.fxs, color = if (qr.position <= 3) c.bg else c.text))
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                        .background(teamColorProvider(qr.teamColor))) {}
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(qr.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                    if (qr.position == 1) {
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.gold)
                            .padding(horizontal = 3.dp, vertical = 1.dp)) {
                            Text("POLE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val timeText = if (qr.position == 1) qr.bestTime else qr.gapToP1
                    if (!timeText.isNullOrEmpty()) {
                        Text(timeText.take(10), style = TextStyle(fontSize = sc.fxs,
                            color = if (qr.position == 1) c.gold else c.sub))
                    }
                }
            }
        }
    }
}

// ── WEEKEND SESSION TABLE ─────────────────────────────────────────
// Full-detail list of all upcoming sessions in the race weekend.
@Composable
private fun WeekendSessionTable(sessions: List<SessionRow>, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text("RACE WEEKEND", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
        }
        Spacer(GlanceModifier.height(sc.spaceXs))
        sessions.forEachIndexed { i, s ->
            if (i > 0) Spacer(GlanceModifier.height(1.dp))
            val bg = when { s.isNext -> c.red; s.isPast -> c.bg; else -> c.card }
            val tc = if (s.isPast) c.sub else c.text
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(bg)
                    .padding(horizontal = sc.padSm, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status dot
                Box(GlanceModifier.width(5.dp).height(5.dp).cornerRadius(3.dp)
                    .background(when { s.isNext -> c.gold; s.isPast -> c.divider; else -> c.sub })) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Session label pill
                Box(
                    GlanceModifier.cornerRadius(3.dp)
                        .background(if (s.isNext) c.gold else c.pill)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(abbrevSession(s.label), style = TextStyle(
                        fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                        color = if (s.isNext) c.bg else c.sub))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(s.label, style = TextStyle(
                    fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                    fontSize = sc.fsm, color = tc), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                // Date + local time on one line
                val localT = fmtLocalTime(s.date, s.time)
                val dateTime = buildString {
                    append(fmtLongDate(s.date))
                    if (localT.isNotEmpty()) { append(" · "); append(localT) }
                }
                Text(dateTime, style = TextStyle(
                    fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                    fontSize = sc.fxs, color = if (s.isNext) c.gold else c.sub), maxLines = 1)
            }
        }
    }
}

// ── SESSION PILL STRIP ────────────────────────────────────────────
// Compact single-row of pills: gold = next session, dim = future, invisible = past.
@Composable
private fun SessionPillStrip(sessions: List<SessionRow>, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.cardAlt)
            .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            sessions.take(5).forEachIndexed { i, s ->
                if (i > 0) Spacer(GlanceModifier.width(sc.spaceXs))
                val isNext   = s.isNext
                val tinyFont = (sc.fxs.value * 0.82f).coerceAtLeast(7f).sp
                val localT   = fmtLocalTime(s.date, s.time)
                Box(
                    GlanceModifier.cornerRadius(4.dp)
                        .background(when { isNext -> c.gold; s.isPast -> c.divider; else -> c.pill })
                        .padding(horizontal = sc.spaceXs + 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            abbrevSession(s.label),
                            style = TextStyle(
                                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                                fontSize = tinyFont,
                                color = if (isNext) c.bg else c.sub,
                            ), maxLines = 1,
                        )
                        if (localT.isNotEmpty()) {
                            Text(
                                localT,
                                style = TextStyle(
                                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = tinyFont,
                                    color = if (isNext) c.bg else c.text,
                                ), maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── COMPACT HERO CARD (MEDIUM layout) ────────────────────────────
@Composable
private fun HeroCardCompact(race: ScheduleRow, d: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.red)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(race.flag, style = TextStyle(fontSize = sc.iconMd))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Column(GlanceModifier.defaultWeight()) {
                Text(cleanRaceName(race.raceName),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${race.locality}  ·  ${fmtLongDate(race.raceDate)}",
                        style = TextStyle(fontSize = sc.fxs, color = c.text), maxLines = 1)
                    if (race.hasSprint) { Spacer(GlanceModifier.width(sc.spaceSm)); SprintBadge(c, sc) }
                }
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            CountdownBlock(d, c, sc, compact = true)
        }
    }
}

// ── FULL HERO CARD (FULL layout) ──────────────────────────────────
@Composable
private fun HeroCardFull(race: ScheduleRow, d: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.red)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(race.flag, style = TextStyle(fontSize = sc.iconMd))
            Spacer(GlanceModifier.width(sc.spaceMd))
            Column(GlanceModifier.defaultWeight()) {
                Text(cleanRaceName(race.raceName),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                Spacer(GlanceModifier.height(sc.spaceXs))
                val raceLocal = fmtLocalTime(race.raceDate, race.raceTime ?: d.nextRaceTime)
                Text(buildString {
                    append(race.locality); append("  ·  "); append(fmtLongDate(race.raceDate))
                    if (raceLocal.isNotEmpty()) { append("  ·  Race "); append(raceLocal) }
                }, style = TextStyle(fontSize = sc.fxs, color = c.text), maxLines = 1)
                // Circuit info row
                val circuitInfo = buildString {
                    if (d.laps != null) { append("🔄 "); append(d.laps); append(" laps") }
                    if (d.lapRecord != null) {
                        if (isNotEmpty()) append("  ·  ")
                        append("⏱ "); append(d.lapRecord)
                        if (d.lapRecordHolder != null) { append(" ("); append(d.lapRecordHolder.split(" ").last()); append(")") }
                    }
                }
                if (circuitInfo.isNotEmpty()) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text(circuitInfo, style = TextStyle(fontSize = sc.fxs, color = c.text), maxLines = 1)
                }
                if (race.hasSprint) { Spacer(GlanceModifier.height(sc.spaceXs)); SprintBadge(c, sc) }
            }
            Spacer(GlanceModifier.width(sc.spaceMd))
            CountdownBlock(d, c, sc, compact = false)
        }
    }
}

// ── COUNTDOWN BLOCK ───────────────────────────────────────────────
@Composable
private fun CountdownBlock(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean) {
    val bigFont = if (compact) sc.fxl else sc.fxxl
    val smFont  = if (compact) sc.fxs else sc.fsm
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            d.daysUntil == 0L -> {
                Text("TODAY", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = bigFont, color = c.text))
                if (d.hoursUntil >= 0)
                    Text("${d.hoursUntil}h ${d.minutesUntil}m",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = smFont, color = c.text))
                if (d.nextSessionLabel != null)
                    Text(abbrevSession(d.nextSessionLabel), style = TextStyle(fontSize = sc.fxs, color = c.text))
            }
            d.daysUntil > 0 -> {
                Text("${d.daysUntil}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = bigFont, color = c.text))
                Text("days", style = TextStyle(fontSize = smFont, color = c.text))
                if (d.nextSessionLabel != null) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text(abbrevSession(d.nextSessionLabel), style = TextStyle(fontSize = sc.fxs, color = c.text))
                }
            }
            else -> Text("R${d.round ?: "?"}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = bigFont, color = c.text))
        }
    }
}

// ── SPRINT BADGE ──────────────────────────────────────────────────
@Composable
private fun SprintBadge(c: F1Clr, sc: WScale) {
    Box(GlanceModifier.cornerRadius(3.dp).background(c.card).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text("SPRINT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
    }
}

// ── RACE ROW ──────────────────────────────────────────────────────
@Composable
private fun RaceRow(
    race: ScheduleRow, c: F1Clr, sc: WScale,
    showDate: Boolean = false, showLocality: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val bgColor   = when { race.isNext -> c.red; race.isPast -> c.bg; else -> c.card }
    val textColor = if (race.isPast) c.sub else c.text
    Box(
        GlanceModifier.fillMaxWidth().then(modifier).cornerRadius(sc.cornerSm)
            .background(bgColor).padding(horizontal = sc.padSm, vertical = sc.spaceXs + 1.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Round pill
            Box(
                GlanceModifier.cornerRadius(3.dp)
                    .background(if (race.isNext) c.card else if (race.isPast) c.divider else c.pill)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("R${race.round}", style = TextStyle(
                    fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                    color = if (race.isNext) c.text else c.sub,
                    fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal,
                ))
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text(race.flag, style = TextStyle(fontSize = sc.iconSm))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Column(GlanceModifier.defaultWeight()) {
                Text(cleanRaceName(race.raceName).take(24),
                    style = TextStyle(
                        fontWeight = if (race.isNext) FontWeight.Bold else FontWeight.Medium,
                        fontSize = sc.fsm, color = textColor,
                        fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal,
                    ), maxLines = 1)
                if (showLocality && race.locality.isNotEmpty())
                    Text(race.locality, style = TextStyle(fontSize = sc.fxs, color = c.sub,
                        fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal), maxLines = 1)
            }
            if (race.hasSprint) {
                Box(GlanceModifier.cornerRadius(3.dp)
                    .background(if (race.isNext) c.card else c.red)
                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("S", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                }
                Spacer(GlanceModifier.width(sc.spaceXs))
            }
            if (showDate) {
                // Use race's own raceTime (available for all entries now)
                val localTime = if (!race.isPast) fmtLocalTime(race.raceDate, race.raceTime) else ""
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtLongDate(race.raceDate), style = TextStyle(fontSize = sc.fxs,
                        color = if (race.isNext) c.text else c.sub,
                        fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal))
                    if (localTime.isNotEmpty())
                        Text(localTime, style = TextStyle(fontSize = sc.fxs,
                            color = if (race.isNext) c.text else c.sub))
                }
            }
        }
    }
}

// ── HELPERS ───────────────────────────────────────────────────────
private fun cleanRaceName(n: String) = n.removePrefix("Grand Prix of ").removePrefix("Formula 1 ").trim()

private fun abbrevSession(label: String): String = when {
    label.startsWith("Sprint Quali", ignoreCase = true) -> "SQ"
    label.startsWith("Sprint",       ignoreCase = true) -> "SPR"
    label.startsWith("Qualifying",   ignoreCase = true) -> "QUALI"
    label.startsWith("Race",         ignoreCase = true) -> "RACE"
    label.startsWith("FP",           ignoreCase = true) -> label.take(3).uppercase()
    else -> label.take(5).uppercase()
}

private fun fmtLocalTime(dateStr: String?, timeStr: String?): String {
    if (dateStr == null || timeStr == null) return ""
    return try {
        val clean = timeStr.trimEnd('Z')
        val dt    = java.time.LocalDateTime.parse("${dateStr}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }
}

private fun fmtLongDate(dateStr: String): String = try {
    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("EEE d MMM"))
} catch (_: Exception) { dateStr }

