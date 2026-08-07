package com.macrotracker.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.NewReleases
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

// ── Palette ──────────────────────────────────────────────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Gold     = Color(0xFFD4AF37)
private val F1Silver   = Color(0xFFA8B0BC)
private val F1Bronze   = Color(0xFFB87333)
private val CardBg     = Color(0xFF0C121C)
private val SprintPink = Color(0xFFE879B8)
private val FL_Purple  = Color(0xFFA855F7)
private val RowSurface = Color(0xFF141B28)
private val Hairline   = Color(0xFF1E2A3C)

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
private enum class F1Tab(val label: String) {
    NEWS("Feed"),
    DRIVERS("Drivers"),
    TEAMS("Teams"),
    BATTLE("Battle"),
    SCHEDULE("Schedule"),
    QUALI("Quali"),
    RACE("Race"),
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
    var selectedTab by rememberSaveable { mutableStateOf(F1Tab.NEWS) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    MacroCard(
        borderColor = F1Red.copy(alpha = 0.14f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header (always visible) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_f1_logo),
                    contentDescription = "Formula 1",
                    modifier = Modifier.height(20.dp),
                    contentScale = ContentScale.FillHeight,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Formula 1",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    val seasonYear = (state as? F1UiState.Success)?.f1Data?.schedule?.firstOrNull()?.raceDate
                        ?.take(4)
                        ?: java.time.Year.now().value.toString()
                    Text(
                        "$seasonYear season",
                        fontSize = 11.sp,
                        color = TextSecondary,
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

                    // ── Tab bar ──────────────────────────────────────────
                    val showRaceTab  = state is F1UiState.Success && !state.f1Data.lastRaceResults.isNullOrEmpty()
                    val showQualiTab = state is F1UiState.Success && !state.f1Data.lastQualiResults.isNullOrEmpty()
                    val tabs = F1Tab.entries.filter { t ->
                        when (t) {
                            F1Tab.RACE  -> showRaceTab
                            F1Tab.QUALI -> showQualiTab
                            else -> true
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tabs.forEach { tab ->
                            val active = selectedTab == tab
                            val fg by animateColorAsState(
                                if (active) TextPrimary else TextSecondary.copy(alpha = 0.7f),
                                tween(160),
                                label = "fg",
                            )
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { haptics.tick(); selectedTab = tab }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    tab.label,
                                    color = fg,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (active) F1Red else Color.Transparent),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp)
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
                                F1Tab.NEWS     -> F1NewsFeed(s.f1Data.news)
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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        if (next != null) {
            NextRaceBanner(next, daysUntil(next.raceDate), data.schedule)
        }
        CompactStandingsPreview(data.driverStandings)
    }
}

@Composable
private fun CompactStandingsPreview(standings: List<SeasonDriverStanding>) {
    if (standings.isEmpty()) return
    val leader = standings.firstOrNull()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        standings.take(3).forEachIndexed { index, driver ->
            val tc = safeTeamColor(driver.teamColor)
            val gap = if (leader != null && driver.position > 1) {
                "-${(leader.points - driver.points).toInt()}"
            } else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tc),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${driver.position}",
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.width(18.dp),
                )
                DriverHeadshot(
                    url = driver.headshotUrl,
                    driverName = driver.driverName,
                    driverAcronym = driver.driverAcronym,
                    driverNumber = driver.driverNumber,
                    teamColor = tc,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        driver.driverAcronym,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        driver.constructorName,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${driver.points.toInt()}",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        gap ?: "pts",
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
            if (index < 2) {
                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 13.dp))
            }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Starts in", color = TextSecondary, fontSize = 12.sp)
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
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(unit, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun CountdownSep() {
    Text("·", color = TextSecondary.copy(alpha = 0.4f), fontSize = 14.sp)
}

// ── Next-race banner ──────────────────────────────────────────────────────────
@Composable
private fun NextRaceBanner(race: RaceScheduleEntry, days: Long, allRaces: List<RaceScheduleEntry>) {
    val isSoon = days <= 7
    val col = if (isSoon) F1Red else TextPrimary
    var showTrack by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val localRaceTime = remember(race.raceDate, race.raceTime) { formatLocalTime(race.raceDate, race.raceTime) }
    val tz = remember { getLocalTimezone() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RowSurface)
            .clickable { haptics.tick(); showTrack = !showTrack }
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Next race",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    shortGP(race.raceName),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        countryLabel(race.countryCode).takeIf { it != "—" },
                        race.locality,
                    ).joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatShort(race.raceDate))
                        if (localRaceTime.isNotEmpty()) append(" · $localRaceTime ($tz)")
                    },
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        days == 0L -> "Today"
                        days < 0 -> "Past"
                        else -> "${days}d"
                    },
                    color = col,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                if (race.sprintDate != null) {
                    Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Icon(
                    if (showTrack) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (isSoon && days >= 0) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Hairline, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
            LiveCountdown(race.raceDate, race.raceTime, col)
        }

        AnimatedVisibility(showTrack, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(color = Hairline, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    race.laps?.let { CircuitStat("Laps", "$it") }
                    race.lapRecord?.let { CircuitStat("Lap rec", it) }
                    CircuitStat("Round", "${race.round}/${allRaces.size}")
                    race.qualifyingDate?.let { CircuitStat("Quali", formatShort(it)) }
                }
                TrackVisualization(circuitId = race.circuitId ?: "", accentColor = col, raceName = shortGP(race.raceName))
                race.fp1Date?.let    { SessionRow("FP1",   it, null,                TextSecondary) }
                race.fp2Date?.let    { SessionRow("FP2",   it, null,                TextSecondary) }
                race.fp3Date?.let    { SessionRow("FP3",   it, null,                TextSecondary) }
                race.qualifyingDate?.let { SessionRow("Quali", it, race.qualifyingTime, TextPrimary) }
                race.sprintDate?.let     { SessionRow("Sprint", it, race.sprintTime, SprintPink) }
                SessionRow("Race", race.raceDate, race.raceTime, col, bold = true)
            }
        }
    }
}

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

// ── News feed ─────────────────────────────────────────────────────────────────
@Composable
fun F1NewsFeed(news: List<F1NewsArticle>) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    if (news.isEmpty()) { EmptyF1State("No recent Formula 1 news."); return }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        news.forEachIndexed { index, article ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri())) } catch (_: Exception) {}
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!article.imageUrl.isNullOrBlank()) {
                    val request = remember(article.imageUrl) {
                        ImageRequest.Builder(context)
                            .data(article.imageUrl)
                            .size(160)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 76.dp, height = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBg),
                        contentScale = ContentScale.Crop,
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Error,
                            is AsyncImagePainter.State.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(RowSurface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.NewReleases,
                                        null,
                                        tint = TextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (article.category.isNotBlank()) {
                        Text(
                            article.category,
                            color = F1Red.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(
                        article.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                    if (article.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            article.description,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
            if (index < news.lastIndex) {
                HorizontalDivider(color = Hairline, thickness = 0.5.dp)
            }
        }
    }
}

// ── Driver standings ──────────────────────────────────────────────────────────
@Composable
fun DriverStandingsList(standings: List<SeasonDriverStanding>) {
    if (standings.isEmpty()) { EmptyF1State("No championship data yet."); return }
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val leader = standings.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        // 2026 grid is 22 drivers (11 teams × 2); show the full championship list.
        standings.take(22).forEachIndexed { index, driver ->
            val tc = safeTeamColor(driver.teamColor)
            val isExp = expanded == driver.driverAcronym
            val isLeader = driver.position == 1
            val gapToLeader = if (leader != null && !isLeader) (leader.points - driver.points).toInt() else null

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptics.tick(); expanded = if (isExp) null else driver.driverAcronym }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${driver.position}",
                        color = medalColor(driver.position) ?: TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(22.dp),
                    )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                driver.driverAcronym,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            driver.driverNumber?.let {
                                Text("#$it", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Text(
                            driver.constructorName,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${driver.points.toInt()}",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            when {
                                isLeader -> "pts"
                                gapToLeader != null -> "-$gapToLeader"
                                else -> "pts"
                            },
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
                AnimatedVisibility(
                    isExp,
                    enter = expandVertically(tween(200)) + fadeIn(),
                    exit = shrinkVertically(tween(150)) + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 13.dp, end = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatChip("Wins", driver.wins.toString(), tc)
                        if (driver.podiums > 0) StatChip("Podiums", driver.podiums.toString(), F1Bronze)
                        if (driver.fastestLaps > 0) StatChip("FL", driver.fastestLaps.toString(), FL_Purple)
                        if (leader != null && leader.points > 0) {
                            val ratio = (driver.points / leader.points).toFloat().coerceIn(0f, 1f)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("vs leader", color = TextSecondary, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(tc.copy(alpha = 0.15f)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(ratio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(tc.copy(alpha = 0.85f)),
                                    )
                                }
                            }
                        }
                    }
                }
                if (index < standings.take(22).lastIndex) {
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 13.dp))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Constructor standings ─────────────────────────────────────────────────────
@Composable
fun ConstructorStandingsList(teams: List<SeasonConstructorStanding>) {
    if (teams.isEmpty()) { EmptyF1State("No constructor standings available."); return }
    val maxPts = teams.maxOfOrNull { it.points } ?: 1.0
    Column(modifier = Modifier.fillMaxWidth()) {
        teams.forEachIndexed { i, team ->
            val tc = safeTeamColor(team.teamColor)
            val ratio = (team.points / maxPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(500 + i * 40, easing = FastOutSlowInEasing), label = "bar$i")
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${team.position}",
                        color = medalColor(team.position) ?: TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.width(22.dp),
                    )
                    TeamLogo(
                        url = team.teamLogoUrl,
                        teamName = team.constructorName,
                        modifier = Modifier.size(width = 40.dp, height = 24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            team.constructorName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (team.wins > 0) {
                            Text(
                                "${team.wins} win${if (team.wins == 1) "" else "s"}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${team.points.toInt()}",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text("pts", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 13.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tc.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bar)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc.copy(alpha = 0.8f)),
                    )
                }
            }
            if (i < teams.lastIndex) {
                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 13.dp))
            }
        }
    }
}

// ── Championship Battle Tab ───────────────────────────────────────────────────
@Composable
fun ChampionshipBattleTab(drivers: List<SeasonDriverStanding>, constructors: List<SeasonConstructorStanding>) {
    if (drivers.isEmpty()) { EmptyF1State("No championship battle data."); return }
    val haptics = rememberHaptics()
    var battleMode by rememberSaveable { mutableStateOf(0) } // 0=WDC, 1=WCC, 2=Duels

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Drivers", "Teams", "Duels").forEachIndexed { i, label ->
                val active = battleMode == i
                val fg by animateColorAsState(
                    if (active) TextPrimary else TextSecondary.copy(alpha = 0.7f),
                    tween(160),
                    label = "bmode$i",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { haptics.tick(); battleMode = i }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (active) F1Red else Color.Transparent),
                    )
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RowSurface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Championship lead", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DriverBattleCard(d1, 1, tc1, isLeader = true)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+$gap", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("pts", color = TextSecondary, fontSize = 11.sp)
                    }
                    DriverBattleCard(d2, 2, tc2, isLeader = false)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tc2.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc1.copy(alpha = 0.9f)),
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
    Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

// ── Race schedule ─────────────────────────────────────────────────────────────
@Composable
fun RaceScheduleList(schedule: List<RaceScheduleEntry>) {
    if (schedule.isEmpty()) { EmptyF1State("Schedule not yet available."); return }
    val haptics = rememberHaptics()
    var expandedRound by rememberSaveable { mutableStateOf<Int?>(null) }
    val nextIdx = schedule.indexOfFirst { !isPast(it.raceDate) }.takeIf { it >= 0 }

    Column {
        schedule.forEachIndexed { idx, race ->
            val past   = isPast(race.raceDate)
            val days   = daysUntil(race.raceDate)
            val isNext = idx == nextIdx
            val isExp  = expandedRound == race.round
            val sprint = race.sprintDate != null

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptics.tick(); expandedRound = if (isExp) null else race.round }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp),
                    ) {
                        Text(
                            formatMonth(race.raceDate),
                            color = if (isNext) F1Red else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            formatDay(race.raceDate),
                            color = if (isNext) TextPrimary else if (past) TextSecondary else TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "R${race.round}",
                                color = if (isNext) F1Red else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (sprint) {
                                Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(
                            shortGP(race.raceName),
                            color = if (past) TextSecondary else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
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
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                past -> "Done"
                                days == 0L -> "Today"
                                else -> "${days}d"
                            },
                            color = when {
                                past -> TextSecondary.copy(alpha = 0.55f)
                                days <= 7L -> F1Red
                                else -> TextSecondary
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                AnimatedVisibility(isExp, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(150)) + fadeOut()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp, end = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(race.circuitName, color = TextSecondary, fontSize = 12.sp)
                            Text(getLocalTimezone(), color = TextSecondary.copy(alpha = 0.55f), fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            race.laps?.let { CircuitStat("Laps", "$it") }
                            race.lapRecord?.let { CircuitStat("Lap rec", it) }
                            race.lapRecordHolder?.let { CircuitStat("Held by", it.split(" (").first()) }
                        }
                        if (!race.circuitId.isNullOrBlank()) {
                            TrackVisualization(circuitId = race.circuitId, accentColor = if (isNext) F1Red else TextPrimary, raceName = shortGP(race.raceName))
                        }
                        race.fp1Date?.let    { SessionRow("FP1",   it, null,                TextSecondary) }
                        race.fp2Date?.let    { SessionRow("FP2",   it, null,                TextSecondary) }
                        race.fp3Date?.let    { SessionRow("FP3",   it, null,                TextSecondary) }
                        race.qualifyingDate?.let { SessionRow("Quali", it, race.qualifyingTime, TextPrimary) }
                        race.sprintDate?.let     { SessionRow("Sprint", it, race.sprintTime, SprintPink) }
                        SessionRow("Race", race.raceDate, race.raceTime, if (isNext) F1Red else TextPrimary, bold = true)
                    }
                }
                if (idx < schedule.lastIndex) {
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
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
        Text(label, color = color, fontSize = if (bold) 13.sp else 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (localTimeStr.isNotEmpty()) "${formatShort(date)} · $localTimeStr" else formatShort(date),
                color = if (bold) TextPrimary else TextSecondary,
                fontSize = if (bold) 12.sp else 11.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (utcTimeStr.isNotEmpty() && localTimeStr.isNotEmpty()) {
                Text("$utcTimeStr UTC", color = TextSecondary.copy(alpha = 0.45f), fontSize = 10.sp)
            }
        }
    }
}

// ── Qualifying Results ────────────────────────────────────────────────────────
@Composable
fun QualiResultsList(results: List<QualiResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No qualifying data available."); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        raceName?.let {
            Text(
                "Qualifying · ${shortGP(it)}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        results.firstOrNull()?.let { pole ->
            val poleTC = safeTeamColor(pole.teamColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RowSurface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DriverHeadshot(
                    url = pole.headshotUrl,
                    driverName = pole.driverName,
                    driverAcronym = pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                    driverNumber = null,
                    teamColor = poleTC,
                    modifier = Modifier.size(48.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pole", color = FL_Purple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(pole.constructorName, color = TextSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        pole.q3Time ?: pole.q1Time ?: "--:--.---",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text("best", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        val q3Drivers = results.filter { it.q3Time != null }
        val q2Only = results.filter { it.q2Time != null && it.q3Time == null }
        val q1Only = results.filter { it.q1Time != null && it.q2Time == null }

        if (q3Drivers.isNotEmpty()) {
            QualiGroupHeader("Q3")
            q3Drivers.forEach { r -> QualiRow(r, bestTime = r.q3Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q2Only.isNotEmpty()) {
            QualiGroupHeader("Q2")
            q2Only.forEach { r -> QualiRow(r, bestTime = r.q2Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q1Only.isNotEmpty()) {
            QualiGroupHeader("Q1")
            q1Only.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q3Drivers.isEmpty() && q2Only.isEmpty() && q1Only.isEmpty()) {
            results.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
    }
}

@Composable
private fun QualiGroupHeader(label: String) {
    Text(
        label,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun QualiRow(result: QualiResult, bestTime: String?, accentColor: Color) {
    val isPole = result.position == 1
    val tc = safeTeamColor(result.teamColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isPole) FL_Purple else accentColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${result.position}",
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.width(20.dp),
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(result.constructorName, color = TextSecondary, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bestTime ?: "--:--.---",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (result.gapToP1 != null) {
                Text(result.gapToP1, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ── Last race results ─────────────────────────────────────────────────────────
@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val dnfCount = results.count { it.status != null && it.time == null && it.status != "Finished" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        raceName?.let {
            Text(shortGP(it), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Winner", color = TextSecondary, fontSize = 11.sp)
                Text(results.firstOrNull()?.driverAcronym ?: "—", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Finishers", color = TextSecondary, fontSize = 11.sp)
                Text("${results.size - dnfCount}/${results.size}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            if (dnfCount > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("DNF", color = TextSecondary, fontSize = 11.sp)
                    Text("$dnfCount", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        val podium = results.filter { it.position in 1..3 }
        if (podium.size >= 3) {
            PodiumDisplay(podium[0], podium[1], podium[2])
        }
        val remainingResults = remember(results) {
            results.drop(if (podium.size >= 3) 3 else 0).take(19)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            remainingResults.forEachIndexed { index, result ->
                RaceResultRow(result)
                if (index < remainingResults.lastIndex) {
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 13.dp))
                }
            }
        }
    }
}

@Composable
private fun RaceResultRow(r: RaceResult) {
    val posGained = r.positionsGained
    val isPoints = r.points > 0
    val tc = safeTeamColor(r.teamColor)
    val acronym = r.driverAcronym ?: r.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tc.copy(alpha = if (isPoints) 1f else 0.45f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${r.position}",
            color = if (isPoints) TextPrimary else TextSecondary.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.width(22.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (r.fastestLap) {
                    Text("FL", color = FL_Purple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(r.constructorName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (posGained != null && posGained != 0) {
            val gainColor = if (posGained > 0) Color(0xFF22C55E) else Color(0xFFEF4444)
            Text(
                if (posGained > 0) "+$posGained" else "$posGained",
                color = gainColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                r.time ?: r.status ?: "+?",
                color = if (r.time != null) TextPrimary else TextSecondary.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            if (r.points > 0) {
                Text("+${r.points.toInt()} pts", color = TextSecondary, fontSize = 11.sp)
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
            .background(RowSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Podium", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            PodiumDriver(p2, 2)
            PodiumDriver(p1, 1)
            PodiumDriver(p3, 3)
        }
    }
}

@Composable
private fun PodiumDriver(result: RaceResult, pos: Int) {
    val medal = medalColor(pos)!!
    val tc = safeTeamColor(result.teamColor)
    val acronym = result.driverAcronym ?: result.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    val headshotSize = if (pos == 1) 60.dp else 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(92.dp),
    ) {
        Text("P$pos", color = medal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(headshotSize),
        )
        Text(acronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = if (pos == 1) 14.sp else 12.sp)
        Text(
            result.constructorName.split(" ").first().take(10),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (result.time != null) {
            Text(result.time, color = TextSecondary, fontSize = 11.sp)
        }
        if (result.fastestLap) {
            Text("Fastest lap", color = FL_Purple, fontSize = 10.sp)
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

