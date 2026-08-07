package com.macrotracker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.macrotracker.data.f1.*
import com.macrotracker.R
import com.macrotracker.ui.theme.*
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.F1UiState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── F1 broadcast / timing-tower palette ───────────────────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Gold     = Color(0xFFF5C518)
private val F1Silver   = Color(0xFFC8CDD8)
private val F1Bronze   = Color(0xFFCD7F32)
private val CardBg     = Color(0xFF070B12)
private val SprintPink = Color(0xFFFF4DA6)
private val FL_Purple  = Color(0xFFB24BFF)
private val TimingTeal = Color(0xFF00D2BE)
private val SectorYellow = Color(0xFFFFE066)
private val PosGain    = Color(0xFF39FF14)
private val PosLoss    = Color(0xFFFF3B3B)
private val RowSurface = Color(0xFF101722)
private val PanelDeep  = Color(0xFF0C121C)
private val Hairline   = Color(0xFF243044)
private val TimingMono = FontFamily.Monospace

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun medalColor(pos: Int) = when (pos) { 1 -> F1Gold; 2 -> F1Silver; 3 -> F1Bronze; else -> null }
private fun countryLabel(code: String?): String =
    code?.trim()?.takeIf { it.isNotBlank() && it != "🏁" }?.uppercase() ?: "—"
private fun formatMonth(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("MMM").withLocale(java.util.Locale.ENGLISH)).uppercase() } catch (_: Exception) { "" }
private fun formatDay(d: String)   = try { LocalDate.parse(d).dayOfMonth.toString() } catch (_: Exception) { "" }
private fun formatShort(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM")) } catch (_: Exception) { d }
private fun daysUntil(d: String)   = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)) } catch (_: Exception) { Long.MAX_VALUE }
private fun isPast(d: String)      = try { LocalDate.parse(d).isBefore(LocalDate.now()) } catch (_: Exception) { false }
private fun shortGP(name: String)  = name.replace(" Grand Prix", " GP")
private fun safeTeamColor(hex: String): Color = try { Color("#$hex".toColorInt()) } catch (_: Exception) { F1Red }

private fun formatLocalTime(dateStr: String, timeStr: String?): String {
    return try {
        if (timeStr.isNullOrBlank()) return ""
        val timeClean = timeStr.replace("Z", "")
        val utcDt = LocalDateTime.parse("${dateStr}T$timeClean").atOffset(ZoneOffset.UTC)
        val localDt = utcDt.atZoneSameInstant(java.util.TimeZone.getDefault().toZoneId())
        val hour = localDt.hour
        val min = localDt.minute.toString().padStart(2, '0')
        val amPm = if (hour < 12) "AM" else "PM"
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        "$h12:$min $amPm"
    } catch (_: Exception) { "" }
}

private fun getLocalTimezone(): String {
    return try {
        val tz = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val offset = tz.getOffset(now) / 3600000
        val sign = if (offset >= 0) "+" else ""
        "UTC$sign$offset"
    } catch (_: Exception) { "Local" }
}

private fun secondsUntilRace(dateStr: String, timeStr: String?): Long {
    return try {
        val timeClean = timeStr?.replace("Z", "") ?: "13:00:00"
        val dt = LocalDateTime.parse("${dateStr}T$timeClean")
        val nowEpoch = System.currentTimeMillis() / 1000
        val raceEpoch = dt.toEpochSecond(ZoneOffset.UTC)
        (raceEpoch - nowEpoch).coerceAtLeast(0L)
    } catch (_: Exception) { -1L }
}

// ── Tabs ──────────────────────────────────────────────────────────────────────
private enum class F1Tab(val label: String, val accent: Long) {
    DRIVERS("Drivers", 0xFFF5C518),
    TEAMS("Teams", 0xFF00D2BE),
    BATTLE("Battle", 0xFFE10600),
    SCHEDULE("Calendar", 0xFF4F7CFF),
    QUALI("Quali", 0xFFB24BFF),
    RACE("Race", 0xFFFF4DA6),
}

// ── TeamLogo composable ───────────────────────────────────────────────────────
@Composable
private fun TeamLogo(url: String?, teamName: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                teamName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
            )
        }
        return
    }
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .setHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .build()
    }
    SubcomposeAsyncImage(model = request, contentDescription = teamName, modifier = modifier, contentScale = contentScale) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = TextSecondary.copy(alpha = 0.4f))
            }
            is AsyncImagePainter.State.Error -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(
                    teamName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

// ── DriverHeadshot composable ─────────────────────────────────────────────────
@Composable
private fun DriverHeadshot(
    url: String?,
    driverName: String,
    driverAcronym: String,
    driverNumber: String?,
    teamColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // API may stash multiple candidates as "url1|url2|…" for Coil fallback.
    val headshotUrls = remember(url) {
        url.orEmpty()
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    var urlIndex by remember(headshotUrls) { mutableIntStateOf(0) }
    val activeUrl = headshotUrls.getOrNull(urlIndex)

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(teamColor.copy(alpha = 0.08f))) {
        if (activeUrl == null) {
            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
        } else {
            val request = remember(activeUrl) {
                ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .setHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    )
                    .size(256)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = driverName,
                modifier = Modifier.fillMaxSize(),
                // Prefer the top of the frame so full-body fallbacks still show faces.
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(
                        modifier = Modifier.fillMaxSize().background(teamColor.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = teamColor.copy(alpha = 0.4f),
                        )
                    }
                    is AsyncImagePainter.State.Error -> {
                        if (urlIndex < headshotUrls.size - 1) {
                            LaunchedEffect(activeUrl) { urlIndex++ }
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.dp,
                                    color = teamColor.copy(alpha = 0.2f),
                                )
                            }
                        } else {
                            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
        // Team color accent stripe
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .background(teamColor.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun DriverPlaceholder(driverAcronym: String, driverNumber: String?, teamColor: Color) {
    Box(modifier = Modifier.fillMaxSize().background(teamColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(driverAcronym.take(3), color = teamColor, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            if (driverNumber != null) Text("#$driverNumber", color = teamColor.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 7.sp)
        }
    }
}

// ── Root card ─────────────────────────────────────────────────────────────────
@Composable
fun F1Card(
    state: F1UiState,
    onRefresh: () -> Unit,
    isVisible: Boolean = true,
) {
    val haptics = rememberHaptics()
    var selectedTabName by rememberSaveable { mutableStateOf(F1Tab.DRIVERS.name) }
    val selectedTab = F1Tab.entries.find { it.name == selectedTabName } ?: F1Tab.DRIVERS
    var expanded by rememberSaveable { mutableStateOf(false) }

    MacroCard(
        borderColor = F1Red.copy(alpha = 0.22f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header (always visible) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(F1Red.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, F1Red.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_f1_logo),
                        contentDescription = "Formula 1",
                        modifier = Modifier.height(16.dp),
                        contentScale = ContentScale.FillHeight,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FORMULA 1",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.2.sp,
                    )
                    val seasonYear = (state as? F1UiState.Success)?.f1Data?.schedule?.firstOrNull()?.raceDate
                        ?.take(4)
                        ?: java.time.Year.now().value.toString()
                    Text(
                        "$seasonYear · LIVE HUB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = F1Red,
                        letterSpacing = 0.8.sp,
                    )
                }
                val headerSuccess = state as? F1UiState.Success
                LastUpdatedText(
                    lastUpdatedAt = headerSuccess?.lastUpdatedAt,
                    color = TextSecondary,
                )
                if (expanded) {
                    IconButton(
                        onClick = { haptics.click(); onRefresh() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                WidgetExpandChevron(
                    expanded = expanded,
                    onClick = {
                        expanded = !expanded
                        if (expanded) haptics.toggleOn() else haptics.toggleOff()
                    },
                    accentColor = F1Red,
                )
            }

            if (isVisible) {
            // ── Compact content — visible widgets only ───────────────────
            when (state) {
                is F1UiState.Loading -> {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = F1Red, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
                is F1UiState.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Couldn’t load standings. Pull to refresh.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                is F1UiState.Success -> {
                    F1CollapsedWidget(state.f1Data)
                }
            }

            if (!expanded) {
                WidgetExpandFooter(
                    expanded = false,
                    onToggle = { expanded = true },
                    accentColor = F1Red,
                    expandLabel = "Open hub",
                )
            }

            WidgetExpandSection(visible = expanded && isVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))

                    // ── Timing-tower tab rail ─────────────────────────────
                    val showRaceTab  = state is F1UiState.Success && !state.f1Data.lastRaceResults.isNullOrEmpty()
                    val showQualiTab = state is F1UiState.Success && !state.f1Data.lastQualiResults.isNullOrEmpty()
                    val tabs = F1Tab.entries.filter { t ->
                        when (t) {
                            F1Tab.RACE  -> showRaceTab
                            F1Tab.QUALI -> showQualiTab
                            else -> true
                        }
                    }
                    if (selectedTab !in tabs) {
                        LaunchedEffect(tabs) { selectedTabName = tabs.firstOrNull()?.name ?: F1Tab.DRIVERS.name }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelDeep)
                            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(12.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tabs.forEach { tab ->
                            val active = selectedTab == tab
                            val accent = Color(tab.accent)
                            val bg by animateColorAsState(
                                if (active) accent.copy(alpha = 0.18f) else Color.Transparent,
                                tween(180),
                                label = "tabBg",
                            )
                            val fg by animateColorAsState(
                                if (active) TextPrimary else TextSecondary.copy(alpha = 0.75f),
                                tween(180),
                                label = "tabFg",
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(bg)
                                    .then(
                                        if (active) Modifier.border(
                                            BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                                            RoundedCornerShape(9.dp),
                                        ) else Modifier
                                    )
                                    .clickable { haptics.tick(); selectedTabName = tab.name }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (active) accent else Hairline),
                                )
                                Text(
                                    tab.label.uppercase(),
                                    color = fg,
                                    fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.7.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Content ──────────────────────────────────────────
                    // Key on state discriminant (not the full Success object) + tab so that
                    // lastUpdatedAt / data refreshes inside Success don't re-trigger the slide.
                    val f1StateKey = when (state) {
                        is F1UiState.Loading -> -1
                        is F1UiState.Error   -> -2
                        is F1UiState.Success -> selectedTab.ordinal
                    }
                    WidgetStateSwitch(
                        targetState = f1StateKey,
                        label = "f1Body",
                    ) { key ->
                        // Read live `state` for content — `key` determines which branch
                        // is entered so transitions fire correctly on type/tab changes.
                        val s = state
                        when {
                            key == -1 || s is F1UiState.Loading -> F1Loading()
                            key == -2 || s is F1UiState.Error   -> F1Error(onRefresh, haptics)
                            s is F1UiState.Success -> when (selectedTab) {
                                F1Tab.DRIVERS  -> DriverStandingsList(s.f1Data.driverStandings)
                                F1Tab.TEAMS    -> ConstructorStandingsList(s.f1Data.constructorStandings)
                                F1Tab.BATTLE   -> ChampionshipBattleTab(s.f1Data.driverStandings, s.f1Data.constructorStandings)
                                F1Tab.SCHEDULE -> RaceScheduleList(s.f1Data.schedule)
                                F1Tab.QUALI    -> QualiResultsList(s.f1Data.lastQualiResults ?: emptyList(), s.f1Data.lastRaceName)
                                F1Tab.RACE     -> LastRaceResultsList(s.f1Data.lastRaceResults ?: emptyList(), s.f1Data.lastRaceName)
                            }
                            else -> Unit
                        }
                    }

                    WidgetExpandFooter(
                        expanded = true,
                        onToggle = { expanded = false },
                        accentColor = F1Red,
                        collapseLabel = "Show less",
                    )
                }
            }
            }
        }
    }
}

// ── Collapsed compact widget ──────────────────────────────────────────────────
@Composable
private fun F1CollapsedWidget(data: F1Standings) {
    val next = remember(data.schedule) {
        data.schedule.filter { !isPast(it.raceDate) }.minByOrNull { daysUntil(it.raceDate) }
    }
    val completedRounds = remember(data.schedule) { data.schedule.count { isPast(it.raceDate) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (next != null) {
            CompactNextRace(
                race = next,
                days = daysUntil(next.raceDate),
                totalRounds = data.schedule.size,
                completedRounds = completedRounds,
            )
        }
        CompactSnapshotStrip(data)
        CompactStandingsPreview(data.driverStandings)
        CompactLastRaceRow(data)
    }
}

@Composable
private fun CompactNextRace(
    race: RaceScheduleEntry,
    days: Long,
    totalRounds: Int,
    completedRounds: Int,
) {
    val isSoon = days in 0..7
    val accent = if (isSoon) F1Red else TimingTeal
    val localRaceTime = remember(race.raceDate, race.raceTime) { formatLocalTime(race.raceDate, race.raceTime) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.18f), RowSurface, PanelDeep),
                ),
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("NEXT", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
                    Text("R${race.round}/$totalRounds", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TimingMono)
                    if (race.sprintDate != null) {
                        Text("SPRINT", color = SprintPink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
                    }
                }
                Text(
                    shortGP(race.raceName),
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        val place = listOfNotNull(
                            countryLabel(race.countryCode).takeIf { it != "—" },
                            race.locality,
                        ).joinToString(" · ")
                        append(place)
                        if (place.isNotBlank()) append(" · ")
                        append(formatShort(race.raceDate))
                        if (localRaceTime.isNotEmpty()) append(" · $localRaceTime")
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        days == 0L -> "TODAY"
                        days < 0 -> "—"
                        else -> "${days}D"
                    },
                    color = accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    fontFamily = TimingMono,
                )
                if (completedRounds > 0 && totalRounds > 0) {
                    Text(
                        "$completedRounds DONE",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
        if (isSoon) {
            HorizontalDivider(color = Hairline, thickness = 0.5.dp)
            LiveCountdown(race.raceDate, race.raceTime, accent)
        }
    }
}

@Composable
private fun CompactSnapshotStrip(data: F1Standings) {
    val leader = data.driverStandings.firstOrNull()
    val p2 = data.driverStandings.getOrNull(1)
    val wcc = data.constructorStandings.firstOrNull()
    val totalRounds = data.schedule.size
    val completedRounds = data.schedule.count { isPast(it.raceDate) }
    val lastWinner = data.lastRaceResults?.firstOrNull()

    data class Snap(
        val label: String,
        val value: String,
        val sub: String,
        val accent: Color,
    )

    val snaps = remember(
        leader?.driverAcronym,
        leader?.points,
        p2?.points,
        wcc?.constructorName,
        wcc?.points,
        totalRounds,
        completedRounds,
        lastWinner?.driverAcronym,
        data.lastRaceName,
    ) {
        buildList {
            if (leader != null) {
                add(
                    Snap(
                        "WDC",
                        leader.driverAcronym,
                        "${leader.points.toInt()} pts",
                        safeTeamColor(leader.teamColor),
                    ),
                )
            }
            if (leader != null && p2 != null) {
                val gap = (leader.points - p2.points).toInt()
                add(Snap("GAP", "+$gap", "to P2", F1Silver))
            }
            if (wcc != null) {
                add(
                    Snap(
                        "WCC",
                        wcc.constructorName.split(" ").first().take(8),
                        "${wcc.points.toInt()} pts",
                        safeTeamColor(wcc.teamColor),
                    ),
                )
            }
            if (totalRounds > 0) {
                add(
                    Snap(
                        "SEASON",
                        "R$completedRounds/$totalRounds",
                        if (totalRounds > 0) "${(completedRounds * 100 / totalRounds)}%" else "—",
                        F1Red,
                    ),
                )
            } else if (lastWinner != null) {
                add(
                    Snap(
                        "LAST",
                        lastWinner.driverAcronym ?: lastWinner.driverName.split(" ").last().take(3).uppercase(),
                        shortGP(data.lastRaceName ?: ""),
                        F1Red,
                    ),
                )
            }
        }
    }

    if (snaps.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowSurface)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
    ) {
        snaps.forEachIndexed { index, snap ->
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(snap.label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    snap.value,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snap.sub,
                    color = snap.accent.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < snaps.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(36.dp)
                        .background(Hairline),
                )
            }
        }
    }
}

@Composable
private fun CompactStandingsPreview(standings: List<SeasonDriverStanding>) {
    if (standings.isEmpty()) return
    val leader = standings.firstOrNull()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Drivers", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("Top ${standings.take(5).size}", color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        standings.take(5).forEachIndexed { index, driver ->
            val tc = safeTeamColor(driver.teamColor)
            val gap = if (leader != null && driver.position > 1) {
                "-${(leader.points - driver.points).toInt()}"
            } else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tc),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${driver.position}",
                    color = medalColor(driver.position) ?: TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(16.dp),
                )
                DriverHeadshot(
                    url = driver.headshotUrl,
                    driverName = driver.driverName,
                    driverAcronym = driver.driverAcronym,
                    driverNumber = driver.driverNumber,
                    teamColor = tc,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    driver.driverAcronym,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    driver.constructorName.split(" ").first().take(9),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (driver.wins > 0) {
                    Text(
                        "${driver.wins}W",
                        color = F1Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${driver.points.toInt()}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    if (gap != null) {
                        Text(gap, color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
            if (index < standings.take(5).lastIndex) {
                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 11.dp))
            }
        }
    }
}

@Composable
private fun CompactLastRaceRow(data: F1Standings) {
    val results = data.lastRaceResults ?: return
    if (results.isEmpty()) return
    val podium = results.filter { it.position in 1..3 }
    val fl = results.firstOrNull { it.fastestLap }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(listOf(F1Gold.copy(alpha = 0.12f), RowSurface)),
            )
            .border(BorderStroke(1.dp, F1Gold.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LAST RACE", color = F1Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            data.lastRaceName?.let {
                Text(shortGP(it), color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (podium.size >= 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                podium.take(3).forEach { r ->
                    val tc = safeTeamColor(r.teamColor)
                    val acronym = r.driverAcronym
                        ?: r.driverName.split(" ").lastOrNull()?.take(3)?.uppercase()
                        ?: "—"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "P${r.position}",
                            color = medalColor(r.position) ?: TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tc),
                        )
                        Text(acronym, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (fl != null) {
                    val flAcronym = fl.driverAcronym
                        ?: fl.driverName.split(" ").lastOrNull()?.take(3)?.uppercase()
                        ?: "FL"
                    Text("FL $flAcronym", color = FL_Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val winner = results.first()
            Text(
                "${winner.driverAcronym ?: winner.driverName} · ${winner.constructorName}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Live Race Countdown ────────────────────────────────────────────────────────
@Composable
private fun LiveCountdown(dateStr: String, timeStr: String?, accentColor: Color) {
    val tickersPaused = LocalTickersPaused.current
    var secondsLeft by remember(dateStr, timeStr) { mutableLongStateOf(secondsUntilRace(dateStr, timeStr)) }

    LaunchedEffect(dateStr, timeStr, tickersPaused) {
        if (tickersPaused) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft = secondsUntilRace(dateStr, timeStr)
        }
    }

    if (secondsLeft <= 0 || secondsLeft > 7 * 24 * 3600) return

    val days = secondsLeft / 86400
    val hours = (secondsLeft % 86400) / 3600
    val mins = (secondsLeft % 3600) / 60
    val secs = secondsLeft % 60

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Starts in", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (days > 0) {
            CountdownUnit("${days}", "d", accentColor)
            CountdownSep()
        }
        CountdownUnit(hours.toString().padStart(2, '0'), "h", accentColor)
        CountdownSep()
        CountdownUnit(mins.toString().padStart(2, '0'), "m", accentColor)
        CountdownSep()
        CountdownUnit(secs.toString().padStart(2, '0'), "s", accentColor)
    }
}

@Composable
private fun CountdownUnit(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(unit, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun CountdownSep() {
    Text("·", color = TextSecondary.copy(alpha = 0.4f), fontSize = 13.sp)
}

// ── Shared circuit stat ───────────────────────────────────────────────────────
@Composable
private fun CircuitStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Track SVG URL mapping ─────────────────────────────────────────────────────
private fun getCircuitSvgUrl(circuitId: String): String? = when (circuitId) {
    "albert_park"   -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Australia_Circuit.png.transform/7col/image.png"
    "bahrain"       -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Bahrain_Circuit.png.transform/7col/image.png"
    "jeddah"        -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244990/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Saudi_Arabia_Circuit.png.transform/7col/image.png"
    "shanghai"      -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/China_Circuit.png.transform/7col/image.png"
    "miami"         -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244990/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Miami_Circuit.png.transform/7col/image.png"
    "imola"         -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Emilia_Romagna_Circuit.png.transform/7col/image.png"
    "monaco"        -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Monaco_Circuit.png.transform/7col/image.png"
    "villeneuve"    -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Canada_Circuit.png.transform/7col/image.png"
    "catalunya"     -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Spain_Circuit.png.transform/7col/image.png"
    "red_bull_ring" -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Austria_Circuit.png.transform/7col/image.png"
    "silverstone"   -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Great_Britain_Circuit.png.transform/7col/image.png"
    "hungaroring"   -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Hungary_Circuit.png.transform/7col/image.png"
    "spa"           -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Belgium_Circuit.png.transform/7col/image.png"
    "zandvoort"     -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Netherlands_Circuit.png.transform/7col/image.png"
    "monza"         -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Italy_Circuit.png.transform/7col/image.png"
    "baku"          -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Baku_Circuit.png.transform/7col/image.png"
    "marina_bay"    -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244990/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Singapore_Circuit.png.transform/7col/image.png"
    "suzuka"        -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Japan_Circuit.png.transform/7col/image.png"
    "austin"        -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/USA_Circuit.png.transform/7col/image.png"
    "rodriguez"     -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Mexico_Circuit.png.transform/7col/image.png"
    "interlagos"    -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244988/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Brazil_Circuit.png.transform/7col/image.png"
    "las_vegas"     -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244990/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Las_Vegas_Circuit.png.transform/7col/image.png"
    "losail"        -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Qatar_Circuit.png.transform/7col/image.png"
    "yas_marina"    -> "https://media.formula1.com/image/upload/f_auto/q_auto/v1677244989/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Abu_Dhabi_Circuit.png.transform/7col/image.png"
    else            -> null
}

// ── Track Visualization ───────────────────────────────────────────────────────
@Composable
private fun TrackVisualization(circuitId: String, accentColor: Color, raceName: String) {
    val context = LocalContext.current
    val svgUrl = remember(circuitId) { getCircuitSvgUrl(circuitId) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(accentColor))
            Text("Circuit", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("· $raceName", color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF080D14))) {
            if (svgUrl != null) {
                val request = remember(svgUrl) {
                    ImageRequest.Builder(context)
                        .data(svgUrl)
                        .size(720, 480)
                        .crossfade(false)
                        .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                SubcomposeAsyncImage(model = request, contentDescription = "$raceName circuit map", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = accentColor.copy(alpha = 0.6f))
                        }
                        is AsyncImagePainter.State.Error -> TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            } else {
                TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
            }
        }
    }
}

// ── TrackPoint / path data ─────────────────────────────────────────────────────
private data class TrackPoint(val x: Float, val y: Float, val label: String? = null)

private fun getTrackPath(circuitId: String): List<TrackPoint> = when (circuitId) {
    "albert_park" -> listOf(TrackPoint(0.50f, 0.05f), TrackPoint(0.75f, 0.08f), TrackPoint(0.90f, 0.18f, "T1"), TrackPoint(0.92f, 0.35f), TrackPoint(0.85f, 0.50f, "T6"), TrackPoint(0.90f, 0.65f), TrackPoint(0.85f, 0.80f, "T11"), TrackPoint(0.70f, 0.90f), TrackPoint(0.50f, 0.93f), TrackPoint(0.30f, 0.90f), TrackPoint(0.15f, 0.75f), TrackPoint(0.10f, 0.55f, "T15"), TrackPoint(0.15f, 0.35f), TrackPoint(0.25f, 0.18f, "Hairpin"), TrackPoint(0.40f, 0.07f), TrackPoint(0.50f, 0.05f))
    "monaco" -> listOf(TrackPoint(0.55f, 0.08f, "Sainte Dévote"), TrackPoint(0.70f, 0.12f), TrackPoint(0.78f, 0.25f, "Massenet"), TrackPoint(0.72f, 0.38f, "Casino"), TrackPoint(0.62f, 0.45f), TrackPoint(0.50f, 0.40f, "Mirabeau"), TrackPoint(0.42f, 0.50f, "Fairmont"), TrackPoint(0.30f, 0.55f), TrackPoint(0.20f, 0.65f, "Portier"), TrackPoint(0.18f, 0.78f), TrackPoint(0.25f, 0.88f, "Tunnel exit"), TrackPoint(0.40f, 0.92f), TrackPoint(0.55f, 0.88f, "Nouvelle"), TrackPoint(0.68f, 0.82f), TrackPoint(0.72f, 0.70f, "Rascasse"), TrackPoint(0.65f, 0.62f), TrackPoint(0.60f, 0.52f), TrackPoint(0.55f, 0.38f), TrackPoint(0.52f, 0.22f), TrackPoint(0.55f, 0.08f))
    "silverstone" -> listOf(TrackPoint(0.50f, 0.08f, "Copse"), TrackPoint(0.72f, 0.10f), TrackPoint(0.88f, 0.20f, "Maggotts"), TrackPoint(0.90f, 0.38f, "Becketts"), TrackPoint(0.82f, 0.52f), TrackPoint(0.88f, 0.65f, "Chapel"), TrackPoint(0.82f, 0.78f), TrackPoint(0.70f, 0.88f), TrackPoint(0.55f, 0.92f, "Stowe"), TrackPoint(0.38f, 0.88f), TrackPoint(0.20f, 0.80f, "Vale"), TrackPoint(0.12f, 0.65f, "Club"), TrackPoint(0.14f, 0.48f), TrackPoint(0.22f, 0.32f, "Abbey"), TrackPoint(0.35f, 0.18f), TrackPoint(0.50f, 0.08f))
    "monza" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.68f, 0.08f), TrackPoint(0.82f, 0.14f), TrackPoint(0.88f, 0.28f), TrackPoint(0.80f, 0.42f), TrackPoint(0.85f, 0.56f), TrackPoint(0.78f, 0.70f), TrackPoint(0.65f, 0.82f), TrackPoint(0.60f, 0.70f), TrackPoint(0.55f, 0.82f), TrackPoint(0.45f, 0.88f), TrackPoint(0.30f, 0.82f), TrackPoint(0.20f, 0.70f), TrackPoint(0.15f, 0.55f), TrackPoint(0.18f, 0.40f), TrackPoint(0.25f, 0.26f), TrackPoint(0.35f, 0.14f), TrackPoint(0.50f, 0.06f))
    "spa" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.65f, 0.10f, "La Source"), TrackPoint(0.80f, 0.20f), TrackPoint(0.88f, 0.32f, "Raidillon"), TrackPoint(0.85f, 0.45f), TrackPoint(0.78f, 0.55f), TrackPoint(0.70f, 0.65f, "Les Combes"), TrackPoint(0.60f, 0.72f), TrackPoint(0.50f, 0.78f), TrackPoint(0.38f, 0.82f), TrackPoint(0.25f, 0.78f), TrackPoint(0.15f, 0.65f), TrackPoint(0.12f, 0.50f), TrackPoint(0.18f, 0.36f), TrackPoint(0.28f, 0.22f), TrackPoint(0.38f, 0.12f), TrackPoint(0.50f, 0.08f))
    "suzuka" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.65f, 0.08f), TrackPoint(0.80f, 0.14f, "T1"), TrackPoint(0.88f, 0.25f), TrackPoint(0.85f, 0.38f), TrackPoint(0.78f, 0.48f), TrackPoint(0.70f, 0.42f), TrackPoint(0.62f, 0.48f, "Hairpin"), TrackPoint(0.55f, 0.55f), TrackPoint(0.45f, 0.62f), TrackPoint(0.35f, 0.72f, "Spoon"), TrackPoint(0.25f, 0.80f), TrackPoint(0.18f, 0.70f, "130R"), TrackPoint(0.15f, 0.55f), TrackPoint(0.20f, 0.40f), TrackPoint(0.28f, 0.28f), TrackPoint(0.38f, 0.14f), TrackPoint(0.50f, 0.06f))
    "baku" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.68f, 0.06f), TrackPoint(0.85f, 0.10f), TrackPoint(0.92f, 0.22f), TrackPoint(0.90f, 0.38f), TrackPoint(0.85f, 0.52f), TrackPoint(0.80f, 0.62f), TrackPoint(0.72f, 0.72f), TrackPoint(0.62f, 0.80f), TrackPoint(0.50f, 0.88f), TrackPoint(0.38f, 0.80f), TrackPoint(0.28f, 0.72f), TrackPoint(0.18f, 0.58f), TrackPoint(0.12f, 0.42f), TrackPoint(0.15f, 0.26f), TrackPoint(0.26f, 0.14f), TrackPoint(0.38f, 0.08f), TrackPoint(0.50f, 0.06f))
    "marina_bay" -> listOf(TrackPoint(0.48f, 0.07f), TrackPoint(0.62f, 0.06f), TrackPoint(0.78f, 0.10f), TrackPoint(0.88f, 0.20f), TrackPoint(0.90f, 0.35f), TrackPoint(0.85f, 0.50f), TrackPoint(0.88f, 0.64f), TrackPoint(0.80f, 0.76f), TrackPoint(0.68f, 0.85f), TrackPoint(0.52f, 0.90f), TrackPoint(0.36f, 0.85f), TrackPoint(0.22f, 0.76f), TrackPoint(0.14f, 0.62f), TrackPoint(0.12f, 0.46f), TrackPoint(0.18f, 0.30f), TrackPoint(0.30f, 0.18f), TrackPoint(0.42f, 0.10f), TrackPoint(0.48f, 0.07f))
    "yas_marina" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.65f, 0.06f), TrackPoint(0.80f, 0.12f), TrackPoint(0.90f, 0.24f), TrackPoint(0.88f, 0.40f), TrackPoint(0.80f, 0.52f), TrackPoint(0.85f, 0.65f), TrackPoint(0.80f, 0.78f), TrackPoint(0.65f, 0.88f), TrackPoint(0.50f, 0.92f), TrackPoint(0.35f, 0.88f), TrackPoint(0.20f, 0.78f), TrackPoint(0.12f, 0.62f), TrackPoint(0.14f, 0.45f), TrackPoint(0.20f, 0.30f), TrackPoint(0.32f, 0.16f), TrackPoint(0.44f, 0.09f), TrackPoint(0.50f, 0.08f))
    "bahrain" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.66f, 0.06f), TrackPoint(0.82f, 0.12f), TrackPoint(0.90f, 0.24f), TrackPoint(0.88f, 0.38f), TrackPoint(0.80f, 0.48f), TrackPoint(0.72f, 0.55f), TrackPoint(0.62f, 0.50f), TrackPoint(0.52f, 0.56f, "Hairpin"), TrackPoint(0.42f, 0.50f), TrackPoint(0.32f, 0.42f), TrackPoint(0.20f, 0.48f), TrackPoint(0.14f, 0.60f), TrackPoint(0.16f, 0.74f), TrackPoint(0.26f, 0.84f), TrackPoint(0.38f, 0.90f), TrackPoint(0.50f, 0.92f), TrackPoint(0.62f, 0.88f), TrackPoint(0.72f, 0.78f), TrackPoint(0.68f, 0.66f), TrackPoint(0.60f, 0.68f), TrackPoint(0.52f, 0.76f), TrackPoint(0.42f, 0.70f), TrackPoint(0.35f, 0.60f), TrackPoint(0.36f, 0.48f), TrackPoint(0.42f, 0.38f), TrackPoint(0.48f, 0.26f), TrackPoint(0.50f, 0.08f))
    else -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.72f, 0.10f), TrackPoint(0.88f, 0.25f), TrackPoint(0.92f, 0.50f), TrackPoint(0.88f, 0.75f), TrackPoint(0.72f, 0.90f), TrackPoint(0.50f, 0.94f), TrackPoint(0.28f, 0.90f), TrackPoint(0.12f, 0.75f), TrackPoint(0.08f, 0.50f), TrackPoint(0.12f, 0.25f), TrackPoint(0.28f, 0.10f), TrackPoint(0.50f, 0.06f))
}

@Composable
private fun TrackFallbackCanvas(circuitId: String, accentColor: Color, raceName: String) {
    val trackPoints = remember(circuitId) { getTrackPath(circuitId) }
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(circuitId) { drawProgress.snapTo(0f); drawProgress.animateTo(1f, tween(1200, easing = LinearEasing)) }
    val progress = drawProgress.value
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawTrackPath(trackPoints, accentColor, progress, size) }
        Text(raceName.uppercase(), modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.06f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

private fun DrawScope.drawTrackPath(points: List<TrackPoint>, accentColor: Color, progress: Float, canvasSize: Size) {
    if (points.size < 2) return
    val padding = 12f
    val usableW = canvasSize.width - padding * 2
    val usableH = canvasSize.height - padding * 2
    fun tp(pt: TrackPoint) = Offset(padding + pt.x * usableW, padding + pt.y * usableH)
    val path = Path()
    path.moveTo(tp(points[0]).x, tp(points[0]).y)
    val totalSegments = points.size - 1
    val segmentsToShow = (totalSegments * progress).toInt()
    val partialFraction = (totalSegments * progress) - segmentsToShow
    for (i in 1..minOf(segmentsToShow, points.size - 1)) {
        val prev = tp(points[i - 1]); val curr = tp(points[i])
        path.cubicTo(prev.x + (curr.x - prev.x) * 0.4f, prev.y, prev.x + (curr.x - prev.x) * 0.6f, curr.y, curr.x, curr.y)
    }
    if (segmentsToShow < totalSegments && partialFraction > 0f) {
        val i = segmentsToShow + 1
        if (i < points.size) {
            val prev = tp(points[i - 1]); val curr = tp(points[i])
            path.lineTo(prev.x + (curr.x - prev.x) * partialFraction, prev.y + (curr.y - prev.y) * partialFraction)
        }
    }
    drawPath(path, color = accentColor.copy(alpha = 0.25f), style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color = accentColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (progress > 0.05f) {
        val startPt = tp(points[0])
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 5f, center = startPt)
        drawCircle(color = accentColor, radius = 3f, center = startPt)
    }
}

// ── Loading / Error ───────────────────────────────────────────────────────────
@Composable
private fun F1Loading() {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = F1Red, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun F1Error(onRefresh: () -> Unit, haptics: com.macrotracker.ui.util.HapticHelper) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Unable to load Formula 1 data", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Check your connection and try again", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { haptics.confirm(); onRefresh() }) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp), tint = F1Red)
            Spacer(Modifier.width(6.dp))
            Text("Retry", color = F1Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}


// ── Shared F1 chrome ──────────────────────────────────────────────────────────
@Composable
private fun F1Panel(
    modifier: Modifier = Modifier,
    accent: Color = F1Red,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), RowSurface, PanelDeep),
                ),
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.28f)), RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun TelemetryBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 5.dp,
) {
    val bar by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "telemetry",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bar)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.55f), color),
                    ),
                ),
        )
    }
}

@Composable
private fun PosBadge(position: Int, modifier: Modifier = Modifier) {
    val medal = medalColor(position)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(medal?.copy(alpha = 0.18f) ?: PanelDeep)
            .border(
                BorderStroke(1.dp, medal?.copy(alpha = 0.55f) ?: Hairline),
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$position",
            color = medal ?: TextSecondary,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            fontFamily = TimingMono,
        )
    }
}

@Composable
private fun DataTag(label: String, color: Color) {
    Text(
        label.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.9.sp,
    )
}

// ── Driver standings ──────────────────────────────────────────────────────────
@Composable
fun DriverStandingsList(standings: List<SeasonDriverStanding>) {
    if (standings.isEmpty()) { EmptyF1State("No championship data yet."); return }
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val leader = standings.firstOrNull()
    val maxPts = leader?.points?.takeIf { it > 0 } ?: 1.0

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        leader?.let { d ->
            val tc = safeTeamColor(d.teamColor)
            F1Panel(accent = F1Gold) {
                DataTag("World Champion lead", F1Gold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DriverHeadshot(
                        url = d.headshotUrl,
                        driverName = d.driverName,
                        driverAcronym = d.driverAcronym,
                        driverNumber = d.driverNumber,
                        teamColor = tc,
                        modifier = Modifier.size(56.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.driverAcronym, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp)
                        Text(d.driverName, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.constructorName, color = tc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${d.points.toInt()}", color = F1Gold, fontWeight = FontWeight.Black, fontSize = 28.sp, fontFamily = TimingMono)
                        Text("PTS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                if (d.wins > 0 || d.podiums > 0 || d.fastestLaps > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (d.wins > 0) StatChip("Wins", d.wins.toString(), F1Gold)
                        if (d.podiums > 0) StatChip("Podiums", d.podiums.toString(), F1Bronze)
                        if (d.fastestLaps > 0) StatChip("FL", d.fastestLaps.toString(), FL_Purple)
                    }
                }
            }
        }

        standings.take(22).forEachIndexed { index, driver ->
            val tc = safeTeamColor(driver.teamColor)
            val isExp = expanded == driver.driverAcronym
            val isLeader = driver.position == 1
            val gapToLeader = if (leader != null && !isLeader) (leader.points - driver.points).toInt() else null
            val ratio = (driver.points / maxPts).toFloat()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLeader) F1Gold.copy(alpha = 0.06f) else RowSurface)
                    .border(
                        BorderStroke(1.dp, if (isLeader) F1Gold.copy(alpha = 0.28f) else Hairline.copy(alpha = 0.7f)),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { haptics.tick(); expanded = if (isExp) null else driver.driverAcronym }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc),
                    )
                    Spacer(Modifier.width(8.dp))
                    PosBadge(driver.position)
                    Spacer(Modifier.width(8.dp))
                    DriverHeadshot(
                        url = driver.headshotUrl,
                        driverName = driver.driverName,
                        driverAcronym = driver.driverAcronym,
                        driverNumber = driver.driverNumber,
                        teamColor = tc,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(driver.driverAcronym, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.6.sp)
                            driver.driverNumber?.let {
                                Text("#$it", color = tc, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TimingMono)
                            }
                        }
                        Text(driver.constructorName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${driver.points.toInt()}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp, fontFamily = TimingMono)
                        Text(
                            when {
                                isLeader -> "LEADER"
                                gapToLeader != null -> "−$gapToLeader"
                                else -> "PTS"
                            },
                            color = if (isLeader) F1Gold else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TelemetryBar(ratio = ratio, color = tc)
                AnimatedVisibility(
                    isExp,
                    enter = expandVertically(tween(200)) + fadeIn(),
                    exit = shrinkVertically(tween(150)) + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatChip("Wins", driver.wins.toString(), tc)
                        if (driver.podiums > 0) StatChip("Podiums", driver.podiums.toString(), F1Bronze)
                        if (driver.fastestLaps > 0) StatChip("FL", driver.fastestLaps.toString(), FL_Purple)
                        StatChip("Gap", if (isLeader) "—" else "−${gapToLeader ?: 0}", TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ConstructorStandingsList(teams: List<SeasonConstructorStanding>) {
    if (teams.isEmpty()) { EmptyF1State("No constructor standings available."); return }
    val maxPts = teams.maxOfOrNull { it.points } ?: 1.0
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        teams.firstOrNull()?.let { leader ->
            val tc = safeTeamColor(leader.teamColor)
            F1Panel(accent = TimingTeal) {
                DataTag("Constructors' Championship", TimingTeal)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tc.copy(alpha = 0.15f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TeamLogo(url = leader.teamLogoUrl, teamName = leader.constructorName, modifier = Modifier.fillMaxSize())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(leader.constructorName, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        if (leader.wins > 0) {
                            Text("${leader.wins} race win${if (leader.wins == 1) "" else "s"}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Text("${leader.points.toInt()}", color = TimingTeal, fontWeight = FontWeight.Black, fontSize = 26.sp, fontFamily = TimingMono)
                }
            }
        }

        teams.forEachIndexed { i, team ->
            val tc = safeTeamColor(team.teamColor)
            val ratio = (team.points / maxPts).toFloat().coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(tc.copy(alpha = 0.22f), RowSurface, PanelDeep),
                        ),
                    )
                    .border(BorderStroke(1.dp, tc.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PosBadge(team.position)
                    Spacer(Modifier.width(10.dp))
                    TeamLogo(
                        url = team.teamLogoUrl,
                        teamName = team.constructorName,
                        modifier = Modifier.size(width = 44.dp, height = 26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            team.constructorName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (team.wins > 0) "${team.wins}W · P${team.position}" else "P${team.position}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${team.points.toInt()}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = TimingMono)
                        Text("PTS", color = tc, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                TelemetryBar(ratio = ratio, color = tc, height = 6.dp)
            }
        }
    }
}


@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column {
        Text(label.uppercase(), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = TimingMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChampionshipBattleTab(drivers: List<SeasonDriverStanding>, constructors: List<SeasonConstructorStanding>) {
    if (drivers.isEmpty()) { EmptyF1State("No championship battle data."); return }
    val haptics = rememberHaptics()
    var battleMode by rememberSaveable { mutableStateOf(0) } // 0=WDC, 1=WCC, 2=Duels

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDeep)
                .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf("WDC", "WCC", "DUELS").forEachIndexed { i, label ->
                val active = battleMode == i
                val accent = when (i) { 0 -> F1Gold; 1 -> TimingTeal; else -> F1Red }
                val bg by animateColorAsState(
                    if (active) accent.copy(alpha = 0.18f) else Color.Transparent,
                    tween(160),
                    label = "bmodeBg$i",
                )
                val fg by animateColorAsState(
                    if (active) TextPrimary else TextSecondary.copy(alpha = 0.7f),
                    tween(160),
                    label = "bmode$i",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .then(
                            if (active) Modifier.border(BorderStroke(1.dp, accent.copy(alpha = 0.5f)), RoundedCornerShape(9.dp))
                            else Modifier
                        )
                        .clickable { haptics.tick(); battleMode = i }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
            }
        }

        AnimatedContent(
            targetState = battleMode,
            label = "battleContent",
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { if (targetState > initialState) it / 10 else -it / 10 })
                    .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { if (targetState > initialState) -it / 10 else it / 10 })
            }
        ) { mode ->
            when (mode) {
                0 -> WDCBattleContent(drivers, haptics)
                1 -> WCCBattleContent(constructors, haptics)
                else -> TeamDuelsContent(drivers, haptics)
            }
        }
    }
}

@Composable
private fun WDCBattleContent(drivers: List<SeasonDriverStanding>, haptics: com.macrotracker.ui.util.HapticHelper) {
    val top5 = drivers.take(5)
    val maxPts = top5.maxOfOrNull { it.points } ?: 1.0
    var selectedDriver by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (drivers.size >= 2) {
            val d1 = drivers[0]; val d2 = drivers[1]
            val tc1 = safeTeamColor(d1.teamColor); val tc2 = safeTeamColor(d2.teamColor)
            val gap = (d1.points - d2.points).toInt()
            val total = d1.points + d2.points
            val ratio1 = if (total > 0) (d1.points / total).toFloat() else 0.5f
            val animRatio by animateFloatAsState(ratio1, tween(700, easing = FastOutSlowInEasing), label = "gapbar")

            F1Panel(accent = F1Red) {
                DataTag("Title fight", F1Red)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DriverBattleCard(d1, 1, tc1, isLeader = true)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GAP", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("+$gap", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = TimingMono)
                        Text("PTS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DriverBattleCard(d2, 2, tc2, isLeader = false)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tc2.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(listOf(tc1.copy(alpha = 0.7f), tc1)),
                            ),
                    )
                }
            }
        }

        SectionHeader("Top 5")
        top5.forEachIndexed { i, d ->
            val tc = safeTeamColor(d.teamColor)
            val ratio = (d.points / maxPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(500 + i * 50, easing = FastOutSlowInEasing), label = "wdc$i")
            val isSelected = selectedDriver == d.driverAcronym

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { haptics.tick(); selectedDriver = if (isSelected) null else d.driverAcronym }
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${d.position}", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                    DriverHeadshot(
                        url = d.headshotUrl,
                        driverName = d.driverName,
                        driverAcronym = d.driverAcronym,
                        driverNumber = d.driverNumber,
                        teamColor = tc,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(d.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tc.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bar)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(tc.copy(alpha = 0.85f)),
                        )
                    }
                    Text("${d.points.toInt()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                AnimatedVisibility(isSelected) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (d.wins > 0) StatChip("Wins", "${d.wins}", tc)
                        if (d.podiums > 0) StatChip("Podiums", "${d.podiums}", F1Bronze)
                        if (d.fastestLaps > 0) StatChip("FL", "${d.fastestLaps}", FL_Purple)
                        StatChip("Team", d.constructorName.split(" ").first(), tc)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverBattleCard(driver: SeasonDriverStanding, pos: Int, tc: Color, isLeader: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(88.dp),
    ) {
        Text(
            if (isLeader) "Leader" else "P$pos",
            color = if (isLeader) F1Gold else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        DriverHeadshot(
            url = driver.headshotUrl,
            driverName = driver.driverName,
            driverAcronym = driver.driverAcronym,
            driverNumber = driver.driverNumber,
            teamColor = tc,
            modifier = Modifier.size(56.dp),
        )
        Text(driver.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            driver.constructorName.split(" ").first().take(10),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WCCBattleContent(constructors: List<SeasonConstructorStanding>, haptics: com.macrotracker.ui.util.HapticHelper) {
    val maxTeamPts = constructors.maxOfOrNull { it.points } ?: 1.0
    var selectedTeam by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        constructors.firstOrNull()?.let { leader ->
            val tc = safeTeamColor(leader.teamColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RowSurface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TeamLogo(url = leader.teamLogoUrl, teamName = leader.constructorName, modifier = Modifier.size(width = 52.dp, height = 32.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Constructors lead", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(leader.constructorName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Text("${leader.points.toInt()}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }

        SectionHeader("Standings")
        constructors.take(10).forEachIndexed { i, t ->
            val tc = safeTeamColor(t.teamColor)
            val ratio = (t.points / maxTeamPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(400, easing = FastOutSlowInEasing), label = "wcc$i")
            val isSelected = selectedTeam == t.constructorName

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { haptics.tick(); selectedTeam = if (isSelected) null else t.constructorName }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${t.position}", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                TeamLogo(url = t.teamLogoUrl, teamName = t.constructorName, modifier = Modifier.size(width = 32.dp, height = 20.dp))
                Text(
                    t.constructorName.split(" ").first().take(10),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tc.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bar)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(tc.copy(alpha = 0.85f)),
                    )
                }
                Text("${t.points.toInt()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TeamDuelsContent(drivers: List<SeasonDriverStanding>, @Suppress("UNUSED_PARAMETER") haptics: com.macrotracker.ui.util.HapticHelper) {
    val teamsMap = drivers.groupBy { it.constructorName }
    val duelTeams = teamsMap.entries.filter { it.value.size >= 2 }
        .sortedBy { entry -> drivers.indexOfFirst { it.constructorName == entry.key } }

    if (duelTeams.isEmpty()) { EmptyF1State("No teammate comparisons available."); return }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Teammate battles")
        duelTeams.forEach { (teamName, teamDrivers) ->
            val sorted = teamDrivers.sortedByDescending { it.points }
            val d1 = sorted[0]; val d2 = sorted[1]
            val tc = safeTeamColor(d1.teamColor)
            val total = d1.points + d2.points
            val ratio1 = if (total > 0) (d1.points / total).toFloat() else 0.5f
            val aRatio by animateFloatAsState(ratio1, tween(700, easing = FastOutSlowInEasing), label = "tm_${d1.driverAcronym}")
            val ptsDiff = (d1.points - d2.points).toInt()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RowSurface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TeamLogo(url = d1.teamLogoUrl, teamName = teamName, modifier = Modifier.size(width = 28.dp, height = 18.dp))
                    Text(teamName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("+$ptsDiff pts", color = TextSecondary, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                        DriverHeadshot(
                            url = d1.headshotUrl,
                            driverName = d1.driverName,
                            driverAcronym = d1.driverAcronym,
                            driverNumber = d1.driverNumber,
                            teamColor = tc,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(d1.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${d1.points.toInt()}", color = TextSecondary, fontSize = 11.sp)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(tc.copy(alpha = 0.12f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(aRatio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tc.copy(alpha = 0.85f)),
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(aRatio * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
                            Text("${((1f - aRatio) * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                        DriverHeadshot(
                            url = d2.headshotUrl,
                            driverName = d2.driverName,
                            driverAcronym = d2.driverAcronym,
                            driverNumber = d2.driverNumber,
                            teamColor = tc,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(d2.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${d2.points.toInt()}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(F1Red))
        Text(title.uppercase(), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

// ── Race schedule ─────────────────────────────────────────────────────────────
@Composable
fun RaceScheduleList(schedule: List<RaceScheduleEntry>) {
    if (schedule.isEmpty()) { EmptyF1State("Schedule not yet available."); return }
    val haptics = rememberHaptics()
    val nextIdx = schedule.indexOfFirst { !isPast(it.raceDate) }.takeIf { it >= 0 }
    var expandedRound by rememberSaveable { mutableStateOf(nextIdx?.let { schedule[it].round }) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        schedule.forEachIndexed { idx, race ->
            val past   = isPast(race.raceDate)
            val days   = daysUntil(race.raceDate)
            val isNext = idx == nextIdx
            val isExp  = expandedRound == race.round
            val sprint = race.sprintDate != null
            val accent = when {
                isNext -> F1Red
                past -> TextSecondary.copy(alpha = 0.45f)
                else -> TimingTeal
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Timeline rail
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(22.dp).padding(top = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isNext) F1Red else if (past) Hairline else TimingTeal)
                            .then(
                                if (isNext) Modifier.border(BorderStroke(2.dp, F1Red.copy(alpha = 0.35f)), CircleShape)
                                else Modifier
                            ),
                    )
                    if (idx < schedule.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(if (isExp) 220.dp else 56.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(accent.copy(alpha = 0.55f), Hairline.copy(alpha = 0.3f)),
                                    ),
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isNext) Brush.horizontalGradient(listOf(F1Red.copy(alpha = 0.18f), RowSurface))
                            else Brush.horizontalGradient(listOf(RowSurface, PanelDeep)),
                        )
                        .border(
                            BorderStroke(1.dp, if (isNext) F1Red.copy(alpha = 0.4f) else Hairline.copy(alpha = 0.65f)),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { haptics.tick(); expandedRound = if (isExp) null else race.round }
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                            Text(formatMonth(race.raceDate), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            Text(formatDay(race.raceDate), color = if (past) TextSecondary else TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = TimingMono)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("R${race.round}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = TimingMono)
                                if (isNext) {
                                    Text("NEXT", color = F1Red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                                }
                                if (sprint) {
                                    Text("SPRINT", color = SprintPink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                }
                            }
                            Text(
                                shortGP(race.raceName),
                                color = if (past) TextSecondary else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    countryLabel(race.countryCode).takeIf { it != "—" },
                                    race.locality,
                                ).joinToString(" · "),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                when {
                                    past -> "DONE"
                                    days == 0L -> "TODAY"
                                    else -> "${days}D"
                                },
                                color = when {
                                    past -> TextSecondary.copy(alpha = 0.55f)
                                    days <= 7L -> F1Red
                                    else -> TimingTeal
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = TimingMono,
                            )
                            Icon(
                                if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = TextSecondary.copy(alpha = 0.45f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    AnimatedVisibility(isExp, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(150)) + fadeOut()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(race.circuitName, color = TextSecondary, fontSize = 12.sp)
                                Text(getLocalTimezone(), color = TextSecondary.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = TimingMono)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                race.laps?.let { CircuitStat("Laps", "$it") }
                                race.lapRecord?.let { CircuitStat("Lap rec", it) }
                                race.lapRecordHolder?.let { CircuitStat("Held by", it.split(" (").first()) }
                            }
                            if (!race.circuitId.isNullOrBlank()) {
                                TrackVisualization(circuitId = race.circuitId, accentColor = if (isNext) F1Red else TimingTeal, raceName = shortGP(race.raceName))
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanelDeep)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                DataTag("Weekend sessions", TextSecondary)
                                race.fp1Date?.let    { SessionRow("FP1",   it, null,                TextSecondary) }
                                race.fp2Date?.let    { SessionRow("FP2",   it, null,                TextSecondary) }
                                race.fp3Date?.let    { SessionRow("FP3",   it, null,                TextSecondary) }
                                race.qualifyingDate?.let { SessionRow("Quali", it, race.qualifyingTime, FL_Purple) }
                                race.sprintDate?.let     { SessionRow("Sprint", it, race.sprintTime, SprintPink) }
                                SessionRow("Race", race.raceDate, race.raceTime, if (isNext) F1Red else TextPrimary, bold = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(label: String, date: String, time: String?, color: Color, bold: Boolean = false) {
    val localTimeStr = remember(date, time) { formatLocalTime(date, time) }
    val utcTimeStr = time?.take(5) ?: ""
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = color, fontSize = if (bold) 12.sp else 11.sp, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (localTimeStr.isNotEmpty()) "${formatShort(date)} · $localTimeStr" else formatShort(date),
                color = if (bold) TextPrimary else TextSecondary,
                fontSize = if (bold) 12.sp else 11.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = TimingMono,
            )
            if (utcTimeStr.isNotEmpty() && localTimeStr.isNotEmpty()) {
                Text("$utcTimeStr UTC", color = TextSecondary.copy(alpha = 0.45f), fontSize = 10.sp, fontFamily = TimingMono)
            }
        }
    }
}

@Composable
fun QualiResultsList(results: List<QualiResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No qualifying data available."); return }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        raceName?.let {
            DataTag("Qualifying · ${shortGP(it)}", FL_Purple)
        }

        results.firstOrNull()?.let { pole ->
            val poleTC = safeTeamColor(pole.teamColor)
            F1Panel(accent = FL_Purple) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DriverHeadshot(
                        url = pole.headshotUrl,
                        driverName = pole.driverName,
                        driverAcronym = pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                        driverNumber = null,
                        teamColor = poleTC,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        DataTag("Pole position", FL_Purple)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(pole.constructorName, color = TextSecondary, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            pole.q3Time ?: pole.q1Time ?: "--:--.---",
                            color = TimingTeal,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            fontFamily = TimingMono,
                        )
                        Text("BEST", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    }
                }
            }
        }

        val q3Drivers = results.filter { it.q3Time != null }
        val q2Only = results.filter { it.q2Time != null && it.q3Time == null }
        val q1Only = results.filter { it.q1Time != null && it.q2Time == null }

        if (q3Drivers.isNotEmpty()) {
            QualiGroupHeader("Q3 · Top 10", FL_Purple)
            q3Drivers.forEach { r -> QualiRow(r, bestTime = r.q3Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q2Only.isNotEmpty()) {
            QualiGroupHeader("Q2 · Knockout", SectorYellow)
            q2Only.forEach { r -> QualiRow(r, bestTime = r.q2Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q1Only.isNotEmpty()) {
            QualiGroupHeader("Q1 · First cut", TextSecondary)
            q1Only.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q3Drivers.isEmpty() && q2Only.isEmpty() && q1Only.isEmpty()) {
            results.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
    }
}

@Composable
private fun QualiGroupHeader(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.width(10.dp).height(2.dp).background(color))
        Text(label.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun QualiRow(result: QualiResult, bestTime: String?, accentColor: Color) {
    val isPole = result.position == 1
    val tc = safeTeamColor(result.teamColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPole) FL_Purple.copy(alpha = 0.08f) else RowSurface)
            .border(
                BorderStroke(1.dp, if (isPole) FL_Purple.copy(alpha = 0.35f) else Hairline.copy(alpha = 0.55f)),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isPole) FL_Purple else accentColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "P${result.position}",
            color = if (isPole) FL_Purple else TextSecondary,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            fontFamily = TimingMono,
            modifier = Modifier.width(28.dp),
        )
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(result.constructorName, color = TextSecondary, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bestTime ?: "--:--.---",
                color = if (isPole) TimingTeal else TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                fontFamily = TimingMono,
            )
            if (result.gapToP1 != null) {
                Text(result.gapToP1, color = TextSecondary, fontSize = 11.sp, fontFamily = TimingMono)
            }
        }
    }
}

@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val dnfCount = results.count { it.status != null && it.time == null && it.status != "Finished" }
    val fl = results.firstOrNull { it.fastestLap }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        raceName?.let {
            DataTag("Race classification · ${shortGP(it)}", F1Red)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaceStatTile("WINNER", results.firstOrNull()?.driverAcronym ?: "—", F1Gold, Modifier.weight(1f))
            RaceStatTile("CLASSIFIED", "${results.size - dnfCount}/${results.size}", TimingTeal, Modifier.weight(1f))
            if (dnfCount > 0) {
                RaceStatTile("DNF", "$dnfCount", PosLoss, Modifier.weight(1f))
            } else if (fl != null) {
                RaceStatTile("FL", fl.driverAcronym ?: "—", FL_Purple, Modifier.weight(1f))
            }
        }

        val podium = results.filter { it.position in 1..3 }
        if (podium.size >= 3) {
            PodiumDisplay(podium[0], podium[1], podium[2])
        }
        val remainingResults = remember(results) {
            results.drop(if (podium.size >= 3) 3 else 0).take(19)
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            remainingResults.forEach { result ->
                RaceResultRow(result)
            }
        }
    }
}

@Composable
private fun RaceStatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RowSurface)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = TimingMono)
    }
}

@Composable
private fun RaceResultRow(r: RaceResult) {
    val posGained = r.positionsGained
    val isPoints = r.points > 0
    val isDnf = r.time == null && r.status != null && r.status != "Finished"
    val tc = safeTeamColor(r.teamColor)
    val acronym = r.driverAcronym ?: r.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDnf) PosLoss.copy(alpha = 0.06f) else RowSurface)
            .border(
                BorderStroke(1.dp, if (isDnf) PosLoss.copy(alpha = 0.25f) else Hairline.copy(alpha = 0.55f)),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tc.copy(alpha = if (isPoints) 1f else 0.4f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "P${r.position}",
            color = if (isPoints) TextPrimary else TextSecondary.copy(alpha = 0.55f),
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            fontFamily = TimingMono,
            modifier = Modifier.width(30.dp),
        )
        DriverHeadshot(
            url = r.headshotUrl,
            driverName = r.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    acronym,
                    color = if (isPoints) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                if (r.fastestLap) {
                    Text("FL", color = FL_Purple, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
            Text(r.constructorName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (posGained != null && posGained != 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    if (posGained > 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = if (posGained > 0) PosGain else PosLoss,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "${kotlin.math.abs(posGained)}",
                    color = if (posGained > 0) PosGain else PosLoss,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = TimingMono,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                r.time ?: r.status ?: "+?",
                color = if (r.time != null) TextPrimary else if (isDnf) PosLoss else TextSecondary.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontFamily = TimingMono,
                fontWeight = FontWeight.SemiBold,
            )
            if (r.points > 0) {
                Text("+${r.points.toInt()} PTS", color = TimingTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PodiumDisplay(p1: RaceResult, p2: RaceResult, p3: RaceResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(F1Gold.copy(alpha = 0.12f), RowSurface, PanelDeep)),
            )
            .border(BorderStroke(1.dp, F1Gold.copy(alpha = 0.28f)), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DataTag("Podium", F1Gold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            PodiumDriver(p2, 2)
            PodiumDriver(p1, 1)
            PodiumDriver(p3, 3)
        }
        // Stepped podium bases
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(2 to 36.dp, 1 to 52.dp, 3 to 28.dp).forEach { (pos, h) ->
                val c = medalColor(pos)!!
                Box(
                    modifier = Modifier
                        .width(78.dp)
                        .height(h)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            Brush.verticalGradient(listOf(c.copy(alpha = 0.45f), c.copy(alpha = 0.12f))),
                        )
                        .border(BorderStroke(1.dp, c.copy(alpha = 0.4f)), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("P$pos", color = c, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = TimingMono)
                }
            }
        }
    }
}

@Composable
private fun PodiumDriver(result: RaceResult, pos: Int) {
    val medal = medalColor(pos)!!
    val tc = safeTeamColor(result.teamColor)
    val acronym = result.driverAcronym ?: result.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    val headshotSize = if (pos == 1) 64.dp else 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(92.dp),
    ) {
        Text("P$pos", color = medal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(headshotSize),
        )
        Text(acronym, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = if (pos == 1) 15.sp else 12.sp, letterSpacing = 0.5.sp)
        Text(
            result.constructorName.split(" ").first().take(10),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (result.time != null) {
            Text(result.time, color = TextSecondary, fontSize = 11.sp, fontFamily = TimingMono)
        }
        if (result.fastestLap) {
            Text("FASTEST LAP", color = FL_Purple, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
fun EmptyF1State(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

