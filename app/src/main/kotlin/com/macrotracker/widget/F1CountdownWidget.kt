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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F1 Countdown Widget — 2×2 to 5×3.
 *
 * TINY    (2×2)   — flag + big countdown number only
 * COMPACT (*×2)   — header + race hero card (fills height) + session pills strip
 * MEDIUM  (2-3×3) — header + race hero card + circuit stats + scrollable sessions
 * FULL    (4-5×3) — header + race hero card + circuit stats row + scrollable sessions
 *
 * All session lists are scrollable via LazyColumn.
 * Refresh button is interactive (actionRunCallback).
 * Tapping the widget opens the app.
 */
class F1CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_SMALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1CountdownRoot(data) } }
    }
}

// ── Root ──────────────────────────────────────────────────────────
@Composable
private fun F1CountdownRoot(data: F1WidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> F1CountdownTiny(data, c, sc)
            WSize.COMPACT -> F1CountdownCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> F1CountdownMedium(data, c, sc)
            WSize.FULL    -> F1CountdownFull(data, c, sc, sz.width)
        }
    }
}

// ── Shared header ─────────────────────────────────────────────────
@Composable
private fun F1Header(title: String, c: F1Clr, sc: WScale) {
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

// ── TINY: 2×2 — flag + countdown number ──────────────────────────
@Composable
private fun F1CountdownTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconHero))
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
            else -> Text("🏎", style = TextStyle(fontSize = sc.fxxl))
        }
    }
}

// ── COMPACT: *×2 — header + race card (fills height) + pill strip bottom ─
@Composable
private fun F1CountdownCompact(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        F1Header("Formula 1", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card), contentAlignment = Alignment.Center) {
                Text("No race data", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            // Race card fills remaining height
            Box(
                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconHero))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(d.nextRaceName,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
                        if (d.circuitName != null)
                            Text(d.circuitName, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                        if (d.nextSessionLabel != null) {
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            val sessionLine = buildSessionLine(d.nextSessionLabel, d.nextSessionDate, d.nextSessionTime)
                            Text(sessionLine, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.red), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    // Countdown right column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            d.daysUntil == 0L -> {
                                Text("TODAY", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.red))
                                if (d.hoursUntil >= 0) {
                                    Text("${d.hoursUntil}h", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.red))
                                    Text("${d.minutesUntil}m", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                }
                            }
                            d.daysUntil > 0 -> {
                                Text("${d.daysUntil}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                                Text("days", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                if (w >= 260.dp && d.nextRaceDate != null)
                                    Text(fmtRaceDate(d.nextRaceDate), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                            }
                        }
                    }
                }
            }
            // Session pill strip at the bottom when there are future sessions
            val futureSessions = d.weekendSessions.filter { !it.isPast }
            if (futureSessions.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                CdSessionPillStrip(futureSessions, c, sc)
            }
        }
    }
}

// ── MEDIUM: 2-3×3 — header + hero + stats + scrollable sessions ───
@Composable
private fun F1CountdownMedium(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        F1Header("Next Race", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text("No race data", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            // Race hero card
            RaceHeroCard(d, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Circuit stats strip (lap count + record)
            val hasStats = d.laps != null || d.lapRecord != null
            if (hasStats) {
                CircuitStatsRow(d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Scrollable session list
            if (d.weekendSessions.isNotEmpty()) {
                SectionDividerLabel("WEEKEND SESSIONS", c, sc)
                Spacer(GlanceModifier.height(sc.spaceXs))
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(d.weekendSessions) { session ->
                        SessionRowItem(session, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── FULL: 4-5×3 — header + hero + stats row + scrollable sessions ─
@Composable
private fun F1CountdownFull(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        F1Header("Next Grand Prix", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text("No race data", style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            // Full hero banner
            RaceHeroCardFull(d, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Circuit stats row
            val hasStats = d.laps != null || d.lapRecord != null || d.lapRecordHolder != null
            if (hasStats) {
                CircuitStatsRow(d, c, sc, showHolder = w >= 340.dp)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Scrollable session list
            if (d.weekendSessions.isNotEmpty()) {
                SectionDividerLabel("WEEKEND SCHEDULE", c, sc)
                Spacer(GlanceModifier.height(sc.spaceXs))
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(d.weekendSessions) { session ->
                        SessionRowItem(session, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── RACE HERO CARD (compact — MEDIUM) ────────────────────────────
@Composable
private fun RaceHeroCard(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column {
                        Text(d.nextRaceName ?: "", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
                        if (d.circuitName != null) Text(d.circuitName, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    }
                }
                if (d.round != null) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBadge(d.round, c, sc)
                        if (d.nextRaceDate != null) {
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Text(fmtRaceDate(d.nextRaceDate), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                        if (d.nextRaceTime != null) {
                            Spacer(GlanceModifier.width(sc.spaceXs))
                            val lt = fmtLocalTimeStr(d.nextRaceDate, d.nextRaceTime)
                            if (lt.isNotEmpty()) Text("· $lt", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
            CountdownNumbers(d, c, sc)
        }
    }
}

// ── RACE HERO CARD (full — FULL layout) ──────────────────────────
@Composable
private fun RaceHeroCardFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(d.nextRaceName ?: "", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                }
                Spacer(GlanceModifier.height(sc.spaceXs))
                if (d.circuitName != null) Text(d.circuitName, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                if (d.round != null) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBadge(d.round, c, sc)
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Text("2026 Season", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        if (d.isRaceWeekend) {
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("WEEKEND", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                            }
                        }
                    }
                }
            }
            Spacer(GlanceModifier.width(sc.spaceMd))
            CountdownNumbers(d, c, sc)
        }
    }
}

// ── COUNTDOWN NUMBERS ────────────────────────────────────────────
@Composable
private fun CountdownNumbers(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            d.daysUntil == 0L -> {
                Text("TODAY", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                if (d.hoursUntil >= 0)
                    Text("${d.hoursUntil}h ${d.minutesUntil}m",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.sub))
            }
            d.daysUntil > 0 -> {
                Text("${d.daysUntil}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                Text("days", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
        }
    }
}

// ── ROUND BADGE ───────────────────────────────────────────────────
@Composable
private fun RoundBadge(round: Int, c: F1Clr, sc: WScale) {
    Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text("R$round", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
    }
}

// ── CIRCUIT STATS ROW ─────────────────────────────────────────────
@Composable
private fun CircuitStatsRow(d: F1WidgetData, c: F1Clr, sc: WScale, showHolder: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth()) {
        if (d.laps != null) {
            StatPill("🔄", "${d.laps}", "Laps", c, sc, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        if (d.lapRecord != null) {
            StatPill("⏱", d.lapRecord, "Lap record", c, sc, GlanceModifier.defaultWeight())
            if (showHolder && d.lapRecordHolder != null) {
                Spacer(GlanceModifier.width(sc.spaceSm))
                StatPill("🏆", d.lapRecordHolder.split(" ").last(), "Record holder", c, sc, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun StatPill(icon: String, value: String, label: String, c: F1Clr, sc: WScale, modifier: GlanceModifier) {
    Box(modifier.cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = TextStyle(fontSize = sc.iconSm))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Column {
                Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                Text(label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
    }
}

// ── SESSION PILL STRIP (compact row, for COMPACT layout bottom) ───
@Composable
private fun CdSessionPillStrip(sessions: List<SessionRow>, c: F1Clr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.cardAlt)
            .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            sessions.take(5).forEachIndexed { i, s ->
                if (i > 0) Spacer(GlanceModifier.width(sc.spaceXs))
                val tinyFont = (sc.fxs.value * 0.82f).coerceAtLeast(7f).sp
                val localT   = fmtLocalTimeStr(s.date, s.time)
                Box(
                    GlanceModifier.cornerRadius(4.dp)
                        .background(when { s.isNext -> c.gold; s.isPast -> c.divider; else -> c.pill })
                        .padding(horizontal = sc.spaceXs + 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(abbrev(s.label), style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                            fontSize = tinyFont, color = if (s.isNext) c.bg else c.sub), maxLines = 1)
                        if (localT.isNotEmpty())
                            Text(localT, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                                fontSize = tinyFont, color = if (s.isNext) c.bg else c.text), maxLines = 1)
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

// ── SESSION LIST ROW ─────────────────────────────────────────────
@Composable
private fun SessionRowItem(s: SessionRow, c: F1Clr, sc: WScale) {
    val bgColor    = if (s.isPast) c.bg else c.card
    val labelColor = when { s.isNext -> c.red; s.isPast -> c.sub; else -> c.text }
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(bgColor)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(GlanceModifier.width(5.dp).height(5.dp).cornerRadius(3.dp)
            .background(when { s.isNext -> c.red; s.isPast -> c.divider; else -> c.sub })) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(s.label, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
            fontSize = sc.fsm, color = labelColor))
        Spacer(GlanceModifier.defaultWeight())
        Text(fmtRaceDate(s.date), style = TextStyle(fontSize = sc.fxs, color = c.sub))
        if (s.time != null) {
            val localT = fmtLocalTimeStr(s.date, s.time)
            if (localT.isNotEmpty()) {
                Spacer(GlanceModifier.width(sc.spaceXs))
                Text(localT, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                    fontSize = sc.fxs, color = if (s.isNext) c.red else c.sub))
            }
        }
        if (s.isNext) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("NEXT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
            }
        }
    }
}

// ── SECTION DIVIDER LABEL ─────────────────────────────────────────
@Composable
private fun SectionDividerLabel(label: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.sub)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
    }
}

// ── HELPERS ───────────────────────────────────────────────────────
private fun fmtLocalTimeStr(dateStr: String?, timeStr: String?): String {
    if (dateStr == null || timeStr == null) return ""
    return try {
        val clean = timeStr.trimEnd('Z')
        val dt    = java.time.LocalDateTime.parse("${dateStr}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }
}

private fun buildSessionLine(label: String, dateStr: String?, timeStr: String?): String {
    val datePart = if (dateStr != null) fmtRaceDate(dateStr) else ""
    val timePart = fmtLocalTimeStr(dateStr, timeStr)
    return when {
        datePart.isNotEmpty() && timePart.isNotEmpty() -> "$label · $datePart · $timePart"
        datePart.isNotEmpty() -> "$label · $datePart"
        else -> label
    }
}

private fun fmtRaceDate(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        java.time.LocalDate.parse(dateStr)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
    } catch (_: Exception) { dateStr }
}

private fun abbrev(label: String): String = when {
    label.startsWith("Sprint Quali", ignoreCase = true) -> "SQ"
    label.startsWith("Sprint",       ignoreCase = true) -> "SPR"
    label.startsWith("Qualifying",   ignoreCase = true) -> "QUALI"
    label.startsWith("Race",         ignoreCase = true) -> "RACE"
    label.startsWith("FP",           ignoreCase = true) -> label.take(3).uppercase()
    else -> label.take(5).uppercase()
}
