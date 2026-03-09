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
private fun ScheduleHeader(title: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.flg.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.flg, color = c.text), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Box(
            GlanceModifier.width(sc.btnSize).height(sc.btnSize).cornerRadius(sc.btnCorner)
                .background(c.card).clickable(actionRunCallback<RefreshF1WidgetAction>()).padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) { Text("↻", style = TextStyle(fontSize = sc.fmd, fontWeight = FontWeight.Bold, color = c.sub)) }
    }
}

// ── TINY: 2×2 — flag + countdown ─────────────────────────────────
@Composable
private fun ScheduleTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val next = d.schedule.firstOrNull { it.isNext } ?: d.schedule.firstOrNull { !it.isPast }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(next?.flag ?: "🏁", style = TextStyle(fontSize = sc.iconHero))
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
            else -> {
                Text("R${next?.round ?: "?"}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                Text(next?.locality?.take(10) ?: "F1", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
    }
}

// ── COMPACT: *×2 — header → pills → full scroll race list ─────────
@Composable
private fun ScheduleCompact(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("F1 2026", c, sc)
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card), contentAlignment = Alignment.Center) {
                Text("No data", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                SessionPillStrip(futureSessions, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.schedule.filter { !it.isPast }) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp,
                        raceTime = if (race.isNext) d.nextRaceTime else null)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── MEDIUM: 2-3×3 — header → pills → compact hero → scroll list ───
@Composable
private fun ScheduleMedium(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("2026 Calendar", c, sc)
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text("No schedule data", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                SessionPillStrip(futureSessions, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (next != null) {
                HeroCardCompact(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            val rest = d.schedule.filter { !it.isPast && !it.isNext }
            if (rest.isNotEmpty()) {
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(rest) { race ->
                        RaceRow(race, c, sc, showDate = true)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── FULL: 4-5×3 — header → pills → full hero → full scroll calendar ─
@Composable
private fun ScheduleFull(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("Formula 1  ·  2026", c, sc)
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text("No schedule data", style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                SessionPillStrip(futureSessions, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (next != null) {
                HeroCardFull(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text("FULL CALENDAR", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            val past   = d.schedule.filter { it.isPast }.takeLast(1)
            val shown  = (past + d.schedule.filter { !it.isPast }).sortedBy { it.round }
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(shown) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp,
                        raceTime = if (race.isNext) d.nextRaceTime else null)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
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
            sessions.take(6).forEachIndexed { i, s ->
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
                    Text("${race.locality}  ·  ${fmtShortDate(race.raceDate)}",
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
                val raceLocal = fmtLocalTime(race.raceDate, d.nextRaceTime)
                Text(buildString {
                    append(race.locality); append("  ·  "); append(fmtShortDate(race.raceDate))
                    if (raceLocal.isNotEmpty()) { append("  ·  Race "); append(raceLocal) }
                }, style = TextStyle(fontSize = sc.fxs, color = c.text), maxLines = 1)
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
    raceTime: String? = null, modifier: GlanceModifier = GlanceModifier,
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
                val localTime = if (raceTime != null) fmtLocalTime(race.raceDate, raceTime) else ""
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtShortDate(race.raceDate), style = TextStyle(fontSize = sc.fxs,
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

private fun fmtShortDate(dateStr: String): String = try {
    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("d MMM"))
} catch (_: Exception) { dateStr }
