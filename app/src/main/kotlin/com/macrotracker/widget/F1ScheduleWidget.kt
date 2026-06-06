package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme

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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F1 Schedule Widget
 */
class F1ScheduleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1ScheduleRoot(data) } }
    }
}

// ——— Root —————————————————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun F1ScheduleRoot(data: F1WidgetData) {
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        ScheduleFull(data, c, sc)
    }
}

// ——— Header ——————————————————————————————————————————————————————————————————————————————————————————————————————
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

// ——— FULL —————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun ScheduleFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val w = 368.dp // Fixed width for fallback scaling
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("Formula 1    ${LocalDate.now().year}", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(com.macrotracker.R.drawable.ic_car),
                    contentDescription = null,
                    colorFilter = androidx.glance.ColorFilter.tint(c.text),
                    modifier = GlanceModifier.size(32.dp)
                )
                Spacer(GlanceModifier.height(sc.spaceSm))
                Text(f1WidgetEmptyMessage(d, "No schedule data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            if (next != null) {
                HeroCardFull(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Show last race + quali cards when there's no hero to show and we have data
            if (next == null) {
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
            val nextRace = d.schedule.find { it.isNext }
            val lastPastRace = d.schedule.filter { it.isPast }.lastOrNull()
            val toShow = d.schedule.filter { it != nextRace && it != lastPastRace }.sortedBy { it.round }
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(toShow) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp)
                    Spacer(GlanceModifier.height(8.dp)) // Increased spacing
                }
            }
        }
    }
}

// ——— LAST RACE CARD ——————————————————————————————————————————————————————————————————————————————————————
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
                if (d.lastRaceFlag == null) {
                    Image(
                        provider = ImageProvider(com.macrotracker.R.drawable.ic_flag),
                        contentDescription = null,
                        colorFilter = androidx.glance.ColorFilter.tint(c.text),
                        modifier = GlanceModifier.size(16.dp)
                    )
                } else {
                    Text(d.lastRaceFlag, style = TextStyle(fontSize = sc.iconSm))
                }
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

// ——— QUALIFYING GRID CARD ——————————————————————————————————————————————————————————————————————————————————
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
                if (d.lastRaceFlag == null) {
                    Image(
                        provider = ImageProvider(com.macrotracker.R.drawable.ic_flag),
                        contentDescription = null,
                        colorFilter = androidx.glance.ColorFilter.tint(c.text),
                        modifier = GlanceModifier.size(16.dp)
                    )
                } else {
                    Text(d.lastRaceFlag, style = TextStyle(fontSize = sc.iconSm))
                }
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

// ——— SESSION PILL STRIP —————————————————————————————————————————————————————————————————
/** Compact single-row of pills: gold = next session, dim = future, invisible = past. */
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

// ——— COMPACT HERO CARD (MEDIUM layout) ———————————————————————————————
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

// ——— FULL HERO CARD (FULL layout) —————————————————————————————————————————————————————————————————————————
@Composable
private fun HeroCardFull(race: ScheduleRow, d: F1WidgetData, c: F1Clr, sc: WScale) {
    val glassBg = androidx.compose.ui.graphics.Color(0x80000000) // Premium dark glass
    Box(
        GlanceModifier.fillMaxWidth().height(120.dp).cornerRadius(sc.cornerSm).background(c.bg)
    ) {
        // Flag Banner Background
        val flagToShow = race.flagBitmap ?: d.flagBitmap
        if (flagToShow != null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Image(
                    provider = ImageProvider(flagToShow),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Dark tint overlay for readability
                Box(GlanceModifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x4D000000))) {}
                
                // Dark Fade effect (right to left)
                Image(
                    provider = ImageProvider(com.macrotracker.R.drawable.f1_surface_fade),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        Row(
            GlanceModifier.fillMaxSize().padding(sc.padSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glassy Race Info + Countdown
            Row(
                GlanceModifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Tile: Race, Locality and Session times
                Box(
                    GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(sc.cornerSm)
                        .background(androidx.glance.unit.ColorProvider(glassBg)).padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.cornerRadius(2.dp).background(c.red).padding(horizontal = 3.dp, vertical = 1.dp)) {
                                Text("R${race.round}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 8.sp, color = c.text))
                            }
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Text(cleanRaceName(race.raceName).take(24),
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.text), maxLines = 1)
                        }
                        
                        Text(race.locality.uppercase(),
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 8.sp, color = c.sub), maxLines = 1)
                        
                        Spacer(GlanceModifier.height(2.dp))
                        
                        // All sessions for this weekend
                        d.weekendSessions.forEach { s ->
                            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val tc = if (s.isNext) c.gold else if (s.isPast) c.sub else c.text
                                val weight = if (s.isNext) FontWeight.Bold else FontWeight.Medium
                                Text(abbrevSession(s.label), style = TextStyle(fontWeight = weight, fontSize = 8.sp, color = tc))
                                Spacer(GlanceModifier.width(6.dp))
                                Text(fmtLongDate(s.date).split(" ").take(2).joinToString(" "), style = TextStyle(fontSize = 8.sp, color = tc))
                                Spacer(GlanceModifier.defaultWeight())
                                Text(fmtLocalTime(s.date, s.time), style = TextStyle(fontWeight = weight, fontSize = 8.sp, color = tc))
                            }
                        }
                    }
                }

                Spacer(GlanceModifier.width(sc.spaceSm))

                // Countdown Tile - Increased width to prevent squishing
                Box(
                    GlanceModifier.width(82.dp).fillMaxHeight().cornerRadius(sc.cornerSm)
                        .background(androidx.glance.unit.ColorProvider(glassBg)).padding(sc.padSm),
                    contentAlignment = Alignment.Center
                ) {
                    CountdownBlock(d, c, sc, compact = true)
                }
            }
        }
    }
}

// ——— COUNTDOWN BLOCK ——————————————————————————————————————————————————————————————————————————————————————
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

// ——— SPRINT BADGE ————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun SprintBadge(c: F1Clr, sc: WScale) {
    Box(GlanceModifier.cornerRadius(3.dp).background(c.card).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text("SPRINT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
    }
}

// ——— RACE ROW ————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun RaceRow(
    race: ScheduleRow, c: F1Clr, sc: WScale,
    showDate: Boolean = false, showLocality: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val bgColor   = when { race.isNext -> c.red; else -> c.bg }
    val textColor = if (race.isPast) c.sub else c.text
    val cardBg    = if (race.isNext) c.card else c.cardAlt
    val glassBg   = androidx.compose.ui.graphics.Color(0x80000000)

    Box(
        GlanceModifier.fillMaxWidth().then(modifier).cornerRadius(sc.cornerSm)
            .background(bgColor),
    ) {
        // Flag Banner Background
        if (race.flagBitmap != null) {
            Box(GlanceModifier.width(140.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Image(
                    provider = ImageProvider(race.flagBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Fade effect: Transparent (left) -> Current bgColor (right)
                val fadeRes = when {
                    race.isNext -> com.macrotracker.R.drawable.f1_banner_fade
                    else -> com.macrotracker.R.drawable.f1_surface_fade
                }
                Image(
                    provider = ImageProvider(fadeRes),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        Row(
            GlanceModifier.fillMaxWidth().height(76.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to show background flag
            Spacer(GlanceModifier.width(60.dp))

            // Main Data Tile
            Box(
                GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(sc.cornerSm)
                    .background(if (race.isNext) androidx.glance.unit.ColorProvider(glassBg) else cardBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // Left Column: Round and Name
                    Column(GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                GlanceModifier.cornerRadius(2.dp)
                                    .background(if (race.isNext) c.text else c.red)
                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                            ) {
                                Text("R${race.round}", style = TextStyle(
                                    fontWeight = FontWeight.Bold, fontSize = 8.sp,
                                    color = if (race.isNext) c.red else c.text
                                ))
                            }
                            Spacer(GlanceModifier.width(6.dp))
                            Text(cleanRaceName(race.raceName).take(20),
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp, color = textColor,
                                    fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal,
                                ), maxLines = 1)
                        }
                        
                        Spacer(GlanceModifier.height(4.dp))

                        // Session times grid
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sessions = mutableListOf<Pair<String, String?>>()
                            sessions.add("Q" to race.qualiTime)
                            if (race.hasSprint) sessions.add("S" to race.sprintTime)
                            sessions.add("R" to race.raceTime)
                            
                            sessions.forEachIndexed { i, (label, time) ->
                                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                                val localT = fmtLocalTime(race.raceDate, time)
                                if (localT.isNotEmpty()) {
                                    Text("$label $localT", style = TextStyle(
                                        fontSize = 8.sp, 
                                        color = if (label == "R" && !race.isPast) (if (race.isNext) c.gold else c.red) else textColor,
                                        fontWeight = if (label == "R") FontWeight.Bold else FontWeight.Normal
                                    ))
                                }
                            }
                        }
                    }

                    // Right Column: Date
                    Column(horizontalAlignment = Alignment.End) {
                        val dateParts = fmtLongDate(race.raceDate).split(" ")
                        Text(dateParts.take(2).joinToString(" "), 
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = textColor))
                        if (dateParts.size > 2) {
                            Text(dateParts.last(), 
                                style = TextStyle(fontSize = 8.sp, color = c.sub))
                        }
                    }
                }
            }
        }
    }
}

// ——— HELPERS ——————————————————————————————————————————————————————————————————————————————————————————————
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

