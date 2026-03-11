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
 * F1 Countdown Widget — complete visual revamp.
 *
 * The entire widget IS the countdown. Bold segmented D / HH / MM / SS blocks
 * dominate every layout, with race context (flag, name, circuit, next session)
 * shown as supporting info above/below.
 *
 * TINY    (2×2)   — flag + HH:MM:SS (or D days) centred
 * COMPACT (*×2)   — header + full segmented timer + race subtitle
 * MEDIUM  (2-3×3) — header + big timer card + session list
 * FULL    (4-5×3) — header + race info row + giant segmented timer + session list
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
            WSize.TINY    -> CdTiny(data, c, sc)
            WSize.COMPACT -> CdCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> CdMedium(data, c, sc)
            WSize.FULL    -> CdFull(data, c, sc, sz.width)
        }
    }
}

// ── Shared header with inline status + refresh ────────────────────
@Composable
private fun CdHeader(title: String, data: F1WidgetData, c: F1Clr, sc: WScale) {
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
                Text(statusText, style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Medium,
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

// ════════════════════════════════════════════════════════
//  TINY  2×2 — flag + countdown + race context
// ════════════════════════════════════════════════════════
@Composable
private fun CdTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
        Spacer(GlanceModifier.height(sc.spaceXs))
        when {
            d.isLoading && d.nextRaceName == null ->
                Text("SYNC", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub))
            d.daysUntil < 0 ->
                Text("🏎", style = TextStyle(fontSize = sc.fxl))
            isLive(d) -> {
                Text("🏁", style = TextStyle(fontSize = sc.fxl))
                Text("LIVE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.gold))
            }
            d.secondsUntil >= 0 -> {
                Text(fmtHms(d.hoursUntil, d.minutesUntil, d.secondsUntil),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.red))
                if (d.daysUntil > 0)
                    Text("${d.daysUntil}d left", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                else
                    Text(d.nextSessionLabel?.let { abbrev(it) } ?: "away", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
            else -> {
                Text("${d.daysUntil}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.red))
                Text("days", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
        }
        // Race name + round for context
        if (d.nextRaceName != null) {
            Spacer(GlanceModifier.height(sc.spaceXs))
            val label = buildString {
                if (d.round != null) { append("R"); append(d.round); append("·") }
                append(cleanRaceName(d.nextRaceName).take(10))
            }
            Text(label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

// ════════════════════════════════════════════════════════
//  COMPACT  *×2 — header + countdown takes full height
// ════════════════════════════════════════════════════════
@Composable
private fun CdCompact(d: F1WidgetData, c: F1Clr, sc: WScale, @Suppress("UNUSED_PARAMETER") w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        CdHeader("Formula 1", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card),
                contentAlignment = Alignment.Center) {
                Text(f1WidgetEmptyMessage(d, "No race data"),
                    style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            // Countdown card fills remaining space
            Box(GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(sc.cornerSm)
                .background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
                if (isLive(d)) {
                    LiveBanner(d, c, sc)
                } else {
                    Column(GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        // Race label at top
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconSm))
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Column(GlanceModifier.defaultWeight()) {
                                Text(cleanRaceName(d.nextRaceName),
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                                val locality = d.schedule.firstOrNull { it.isNext }?.locality ?: ""
                                val subLine = buildString {
                                    if (d.round != null) { append("R"); append(d.round) }
                                    if (locality.isNotEmpty()) { if (isNotEmpty()) append("  ·  "); append(locality) }
                                }
                                if (subLine.isNotEmpty())
                                    Text(subLine, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                            }
                            if (d.nextSessionLabel != null) {
                                Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text(abbrev(d.nextSessionLabel),
                                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                }
                            }
                        }
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        // THE COUNTDOWN — centred, big
                        TimerBlocks(d, c, sc, large = false)
                        if (d.nextSessionDate != null) {
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            val lt = fmtLocalTimeStr(d.nextSessionDate, d.nextSessionTime)
                            Text(buildString {
                                append(fmtLongDate(d.nextSessionDate))
                                if (lt.isNotEmpty()) { append("  ·  "); append(lt) }
                            }, style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                        // Last race winner mini-strip
                        if (d.lastRaceResults.isNotEmpty()) {
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            val winner = d.lastRaceResults.first()
                            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(GlanceModifier.cornerRadius(3.dp).background(c.gold)
                                    .padding(horizontal = 3.dp, vertical = 1.dp)) {
                                    Text("🏆", style = TextStyle(fontSize = (sc.fxs.value * 0.85f).sp))
                                }
                                Spacer(GlanceModifier.width(sc.spaceXs))
                                Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                                    .background(teamColorProvider(winner.teamColor))) {}
                                Spacer(GlanceModifier.width(sc.spaceXs))
                                Text(winner.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                if (d.lastRaceName != null) {
                                    Spacer(GlanceModifier.width(sc.spaceXs))
                                    Text("·  ${cleanRaceName(d.lastRaceName).take(14)}",
                                        style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  MEDIUM  2-3×3 — header + big timer card + sessions
// ════════════════════════════════════════════════════════
@Composable
private fun CdMedium(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        CdHeader("F1 Countdown", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text(f1WidgetEmptyMessage(d, "No race data"), style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            // Big countdown hero card
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
                if (isLive(d)) {
                    LiveBanner(d, c, sc)
                } else {
                    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Race info row
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Column(GlanceModifier.defaultWeight()) {
                                Text(cleanRaceName(d.nextRaceName ?: "—"),
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
                                val locality = d.schedule.firstOrNull { it.isNext }?.locality ?: ""
                                val circuitLine = buildString {
                                    if (locality.isNotEmpty()) append(locality)
                                    if (d.circuitName != null) { if (isNotEmpty()) append("  ·  "); append(d.circuitName) }
                                }
                                if (circuitLine.isNotEmpty())
                                    Text(circuitLine, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                            }
                            if (d.round != null) { RoundBadge(d.round, c, sc) }
                        }
                        Spacer(GlanceModifier.height(sc.spaceMd))
                        // Big timer blocks centred
                        TimerBlocks(d, c, sc, large = true)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        // Next session label
                        if (d.nextSessionLabel != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                                    Text(abbrev(d.nextSessionLabel),
                                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                }
                                Spacer(GlanceModifier.width(sc.spaceSm))
                                val lt = fmtLocalTimeStr(d.nextSessionDate, d.nextSessionTime)
                                Text(buildString {
                                    append(fmtLongDate(d.nextSessionDate))
                                    if (lt.isNotEmpty()) { append("  ·  "); append(lt) }
                                }, style = TextStyle(fontSize = sc.fxs, color = c.sub))
                            }
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (d.weekendSessions.isNotEmpty()) {
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    // Qualifying grid — shown when quali has happened this race weekend
                    if (d.isRaceWeekend && d.lastQualiResults.isNotEmpty()) {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdQualiGrid(d, c, sc, compact = true)
                                Spacer(GlanceModifier.height(sc.spaceSm))
                            }
                        }
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("SESSIONS", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    } else {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("WEEKEND SESSIONS", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    }
                    items(d.weekendSessions) { s ->
                        CdSessionRow(s, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else {
                val hasStats = d.laps != null || d.lapRecord != null
                if (hasStats) {
                    CircuitStatsRow(d, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceSm))
                }
                // Show qualifying grid if available, else last race podium
                if (d.lastQualiResults.isNotEmpty()) {
                    CdQualiGrid(d, c, sc, compact = true)
                } else if (d.lastRaceResults.isNotEmpty()) {
                    LastRacePodium(d, c, sc, compact = true)
                } else if (!hasStats) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  FULL  4-5×3 — header + race info + giant timer + sessions
// ════════════════════════════════════════════════════════
@Composable
private fun CdFull(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        CdHeader("Next Grand Prix", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = TextStyle(fontSize = sc.iconHero))
                Text(f1WidgetEmptyMessage(d, "No race data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (isLive(d)) {
                Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.red)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceMd)) {
                    LiveBanner(d, c, sc)
                }
            } else {
                // Race info card (left) + giant timer (right) in one row
                Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Left: race details
                        Column(GlanceModifier.defaultWeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
                                Spacer(GlanceModifier.width(sc.spaceSm))
                                Column {
                                    Text(cleanRaceName(d.nextRaceName ?: "—"),
                                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                                    val locality = d.schedule.firstOrNull { it.isNext }?.locality ?: ""
                                    val circuitLine = buildString {
                                        if (locality.isNotEmpty()) append(locality)
                                        if (d.circuitName != null) { if (isNotEmpty()) append("  ·  "); append(d.circuitName) }
                                    }
                                    if (circuitLine.isNotEmpty())
                                        Text(circuitLine, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                                }
                            }
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (d.round != null) { RoundBadge(d.round, c, sc); Spacer(GlanceModifier.width(sc.spaceSm)) }
                                if (d.isRaceWeekend) {
                                    Box(GlanceModifier.cornerRadius(3.dp).background(c.gold)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text("RACE WEEKEND", style = TextStyle(fontWeight = FontWeight.Bold,
                                            fontSize = sc.fxs, color = c.bg))
                                    }
                                } else if (d.nextRaceDate != null) {
                                    val raceLocalT = fmtLocalTimeStr(d.nextRaceDate, d.nextRaceTime)
                                    Text(buildString {
                                        append(fmtLongDate(d.nextRaceDate))
                                        if (raceLocalT.isNotEmpty()) { append("  ·  Race "); append(raceLocalT) }
                                    }, style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                }
                            }
                            if (d.nextSessionLabel != null) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Next: ", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                    Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(abbrev(d.nextSessionLabel),
                                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                    }
                                    val lt = fmtLocalTimeStr(d.nextSessionDate, d.nextSessionTime)
                                    if (lt.isNotEmpty()) {
                                        Spacer(GlanceModifier.width(sc.spaceXs))
                                        Text(lt, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                    }
                                }
                            }
                        }
                        Spacer(GlanceModifier.width(sc.spaceMd))
                        // Right: segmented timer blocks
                        TimerBlocks(d, c, sc, large = true)
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Circuit stats row if no weekend sessions
            val hasStats = d.laps != null || d.lapRecord != null || d.lapRecordHolder != null
            if (d.weekendSessions.isEmpty() && hasStats) {
                CircuitStatsRow(d, c, sc, showHolder = w >= 340.dp)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            if (d.weekendSessions.isNotEmpty()) {
                // Qualifying grid + session schedule in one scrollable list
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    if (d.isRaceWeekend && d.lastQualiResults.isNotEmpty()) {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdQualiGrid(d, c, sc, compact = true)
                                Spacer(GlanceModifier.height(sc.spaceSm))
                            }
                        }
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("WEEKEND SCHEDULE", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    } else {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("WEEKEND SCHEDULE", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    }
                    items(d.weekendSessions) { s ->
                        CdSessionRow(s, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else if (d.lastQualiResults.isNotEmpty()) {
                // Between races: show qualifying grid first, then last race podium
                CdQualiGrid(d, c, sc, compact = false)
            } else if (d.lastRaceResults.isNotEmpty()) {
                LastRacePodium(d, c, sc, compact = false)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  THE COUNTDOWN TIMER — main visual component
// ════════════════════════════════════════════════════════

/**
 * Segmented D : HH : MM : SS blocks. The primary countdown visual.
 * [large] = bigger font tokens for MEDIUM/FULL; false for COMPACT.
 */
@Composable
private fun TimerBlocks(d: F1WidgetData, c: F1Clr, sc: WScale, large: Boolean) {
    val numFont  = if (large) sc.fxxl else sc.fxl
    val lblFont  = (if (large) sc.fsm.value else sc.fxs.value).coerceAtLeast(7f).sp
    val sepFont  = if (large) sc.fxl else sc.fmd

    when {
        d.daysUntil < 0 ->
            Text("—", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = numFont, color = c.sub))
        // Only days known (no session time yet)
        d.secondsUntil < 0 -> {
            Row(verticalAlignment = Alignment.Bottom) {
                TimerCell(value = "${d.daysUntil}", label = "DAYS", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
            }
        }
        else -> {
            val showSecs = d.daysUntil == 0L
            Row(verticalAlignment = Alignment.Bottom) {
                // Days — only shown when > 0
                if (d.daysUntil > 0) {
                    TimerCell(value = "${d.daysUntil}", label = "D", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                    TimerColon(sepFont, c)
                }
                TimerCell(value = d.hoursUntil.toString().padStart(2, '0'), label = "HH", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                TimerColon(sepFont, c)
                TimerCell(value = d.minutesUntil.toString().padStart(2, '0'), label = "MM", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                if (showSecs) {
                    TimerColon(sepFont, c)
                    TimerCell(value = d.secondsUntil.toString().padStart(2, '0'), label = "SS", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                }
            }
        }
    }
}

@Composable
private fun TimerCell(
    value: String, label: String,
    numFont: androidx.compose.ui.unit.TextUnit,
    lblFont: androidx.compose.ui.unit.TextUnit,
    c: F1Clr, sc: WScale,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier
                .cornerRadius(6.dp)
                .background(c.cardAlt)
                .padding(horizontal = (sc.spaceSm.value + 3).dp, vertical = (sc.spaceXs.value + 1).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = numFont, color = c.text))
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(label, style = TextStyle(fontSize = lblFont, color = c.sub))
    }
}

@Composable
private fun TimerColon(sepFont: androidx.compose.ui.unit.TextUnit, c: F1Clr) {
    // Nudged down slightly so it aligns with the number boxes
    Box(GlanceModifier.padding(horizontal = 2.dp).padding(bottom = 10.dp)) {
        Text(":", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sepFont, color = c.red))
    }
}

// ── LIVE BANNER ───────────────────────────────────────────────────
@Composable
private fun LiveBanner(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(d.nextRaceFlag ?: "🏁", style = TextStyle(fontSize = sc.iconMd))
        Spacer(GlanceModifier.width(sc.spaceSm))
        Column(GlanceModifier.defaultWeight()) {
            Text(cleanRaceName(d.nextRaceName ?: ""),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
            if (d.nextSessionLabel != null)
                Text(d.nextSessionLabel, style = TextStyle(fontSize = sc.fxs, color = c.sub))
        }
        Box(GlanceModifier.cornerRadius(4.dp).background(c.red).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("🔴  LIVE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
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

// ── QUALIFYING GRID (full detail rows) ────────────────────────────
/**
 * Shows the qualifying grid with team colors, best times and gaps.
 * [compact] = top 3 rows, false = top 5.
 */
@Composable
private fun CdQualiGrid(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean = true) {
    if (d.lastQualiResults.isEmpty()) return
    Column(GlanceModifier.fillMaxWidth()) {
        CdDivider(buildString {
            append("QUALIFYING GRID")
            if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
            if (d.lastRaceName != null) { append("  ·  "); append(cleanRaceName(d.lastRaceName).take(14)) }
        }, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        d.lastQualiResults.take(if (compact) 3 else 5).forEachIndexed { i, qr ->
            if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
            val tcColor = teamColorProvider(qr.teamColor)
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Position medal
                Box(GlanceModifier.cornerRadius(3.dp)
                    .background(podiumColor(qr.position, c))
                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("${qr.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                        color = if (qr.position <= 3) c.bg else c.text))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Team stripe
                Box(GlanceModifier.width(3.dp).height((sc.fsm.value + sc.spaceXs.value * 2).dp)
                    .cornerRadius(2.dp).background(tcColor)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Acronym
                Text(qr.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                // POLE badge for P1
                if (qr.position == 1) {
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("POLE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // Time / gap to pole
                val timeText = if (qr.position == 1) qr.bestTime else qr.gapToP1
                if (!timeText.isNullOrEmpty()) {
                    Text(timeText.take(10), style = TextStyle(fontSize = sc.fxs,
                        color = if (qr.position == 1) c.gold else c.sub))
                }
            }
        }
    }
}

// ── LAST RACE PODIUM ──────────────────────────────────────────────
/**
 * Shows the top finishers from the last completed race.
 * [compact] = 3 rows, false = up to 5 rows for FULL size.
 */
@Composable
private fun LastRacePodium(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean = true) {
    Column(GlanceModifier.fillMaxWidth()) {
        CdDivider(buildString {
            append("LAST RACE")
            if (d.lastRaceName != null) { append("  ·  "); append(cleanRaceName(d.lastRaceName).take(22)) }
            if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
        }, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        val results = d.lastRaceResults.take(if (compact) 3 else 5)
        results.forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
            val tcColor = teamColorProvider(row.teamColor)
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Medal badge
                Box(
                    GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(row.position, c))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("P${row.position}", style = TextStyle(
                        fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                        color = if (row.position <= 3) c.bg else c.text))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Team color stripe
                Box(GlanceModifier.width(3.dp).height((sc.fsm.value + sc.spaceXs.value * 2).dp)
                    .cornerRadius(2.dp).background(tcColor)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Driver acronym
                Text(row.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                // Fastest lap badge
                if (row.fastestLap) {
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.accent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("FL", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // Time / status
                if (!row.timeOrStatus.isNullOrEmpty()) {
                    Text(row.timeOrStatus.take(10), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                }
            }
        }
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
                StatPill("🏆", d.lapRecordHolder.split(" ").last(), "Holder", c, sc, GlanceModifier.defaultWeight())
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

// ── SESSION LIST ROW ─────────────────────────────────────────────
@Composable
private fun CdSessionRow(s: SessionRow, c: F1Clr, sc: WScale) {
    val bg = if (s.isPast) c.bg else c.card
    val tc = when { s.isNext -> c.red; s.isPast -> c.sub; else -> c.text }
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(bg)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(5.dp).height(5.dp).cornerRadius(3.dp)
            .background(when { s.isNext -> c.red; s.isPast -> c.divider; else -> c.sub })) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Box(GlanceModifier.cornerRadius(3.dp)
            .background(if (s.isNext) c.red else c.pill)
            .padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(abbrev(s.label), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                color = if (s.isNext) c.text else c.sub))
        }
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(s.label, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
            fontSize = sc.fsm, color = tc))
        Spacer(GlanceModifier.defaultWeight())
        val localT = fmtLocalTimeStr(s.date, s.time)
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtLongDate(s.date), style = TextStyle(fontSize = sc.fxs, color = if (s.isNext) c.text else c.sub))
            if (localT.isNotEmpty())
                Text(localT, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                    fontSize = sc.fxs, color = if (s.isNext) c.red else c.sub))
        }
    }
}

// ── DIVIDER LABEL ─────────────────────────────────────────────────
@Composable
private fun CdDivider(label: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.sub)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub), maxLines = 1)
    }
}

// ── HELPERS ───────────────────────────────────────────────────────
private fun isLive(d: F1WidgetData) =
    d.daysUntil == 0L && d.hoursUntil == 0L && d.minutesUntil == 0L && d.secondsUntil >= 0

private fun fmtHms(h: Long, m: Long, s: Long): String {
    val hs = h.coerceAtLeast(0).toString().padStart(2, '0')
    val ms = m.coerceAtLeast(0).toString().padStart(2, '0')
    return if (s >= 0) "$hs:$ms:${s.toString().padStart(2, '0')}" else "$hs:$ms"
}

private fun cleanRaceName(n: String) =
    n.removePrefix("Grand Prix of ").removePrefix("Formula 1 ").trim()

private fun fmtLocalTimeStr(dateStr: String?, timeStr: String?): String {
    if (dateStr == null || timeStr == null) return ""
    return try {
        val clean = timeStr.trimEnd('Z')
        val dt = java.time.LocalDateTime.parse("${dateStr}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }
}

private fun fmtLongDate(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        java.time.LocalDate.parse(dateStr)
            .format(DateTimeFormatter.ofPattern("EEE d MMM"))
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
