package com.macrotracker.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.macrotracker.data.f1.*
import com.macrotracker.R
import com.macrotracker.ui.theme.*
import com.macrotracker.ui.util.LastUpdatedText
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
private val F1Gold     = Color(0xFFFFD700)
private val F1Silver   = Color(0xFFC0C0C0)
private val F1Bronze   = Color(0xFFCD7F32)
private val CardBg     = Color(0xFF0E1320)
private val SprintPink = Color(0xFFFF87BC)
private val FL_Purple  = Color(0xFFBF2FCE)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun medalColor(pos: Int) = when (pos) { 1 -> F1Gold; 2 -> F1Silver; 3 -> F1Bronze; else -> null }
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
private enum class F1Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NEWS("FEED",       Icons.Outlined.NewReleases),
    DRIVERS("DRIVERS", Icons.Default.Person),
    TEAMS("TEAMS",     Icons.Default.Flag),
    BATTLE("BATTLE",   Icons.Default.Leaderboard),
    SCHEDULE("CAL",    Icons.Outlined.CalendarMonth),
    QUALI("QUALI",     Icons.Outlined.Timer),
    RACE("RACE",       Icons.Default.EmojiEvents),
}

// ── TeamLogo composable ───────────────────────────────────────────────────────
@Composable
private fun TeamLogo(url: String?, teamName: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(teamName.take(2).uppercase(), color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        return
    }
    val request = remember(url) {
        ImageRequest.Builder(context).data(url).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).crossfade(true).build()
    }
    SubcomposeAsyncImage(model = request, contentDescription = teamName, modifier = modifier, contentScale = contentScale) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = TextSecondary.copy(alpha = 0.4f))
            }
            is AsyncImagePainter.State.Error -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(teamName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

// ── DriverHeadshot composable ─────────────────────────────────────────────────
@Composable
private fun DriverHeadshot(url: String?, driverName: String, driverAcronym: String, driverNumber: String?, teamColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // Build a list of potential headshot URLs to try as fallbacks
    val headshotUrls = remember(url, driverName) {
        val list = mutableListOf<String>()
        val nameLower = driverName.lowercase()
        
        // Manual overrides for drivers where the standard formula often fails
        if (nameLower.contains("antonelli")) {
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/K/KIMANT01_Kimi_Antonelli/kimant01.png.transform/1col/image.png")
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ANDANT01_Andrea_Kimi_Antonelli/andant01.png.transform/1col/image.png")
        } else if (nameLower.contains("lindblad")) {
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png")
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png")
        } else if (nameLower.contains("bortoleto")) {
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GABBOR01_Gabriel_Bortoleto/gabbor01.png.transform/1col/image.png")
        } else if (nameLower.contains("bearman")) {
            list.add("https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OLIBEA01_Oliver_Bearman/olibea01.png.transform/1col/image.png")
        }
        
        // Add the provided URL from the API as a primary or fallback
        if (!url.isNullOrBlank()) {
            list.add(url)
        }
        
        list.distinct()
    }

    var urlIndex by remember(headshotUrls) { mutableStateOf(0) }
    val activeUrl = headshotUrls.getOrNull(urlIndex)

    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(teamColor.copy(alpha = 0.08f))) {
        if (activeUrl == null) {
            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
        } else {
            val request = remember(activeUrl) {
                ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .size(CoilSize.ORIGINAL)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = driverName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(
                        modifier = Modifier.fillMaxSize().background(teamColor.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = teamColor.copy(alpha = 0.4f))
                    }
                    is AsyncImagePainter.State.Error -> {
                        // If one URL fails, try the next one in the list
                        if (urlIndex < headshotUrls.size - 1) {
                            LaunchedEffect(activeUrl) {
                                urlIndex++
                            }
                            // Show loading while switching
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = teamColor.copy(alpha = 0.2f))
                            }
                        } else {
                            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
        // Team color bottom stripe
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter).background(teamColor.copy(alpha = 0.85f)))
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
fun F1Card(state: F1UiState, onRefresh: () -> Unit) {
    val haptics = rememberHaptics()
    var selectedTab by rememberSaveable { mutableStateOf(F1Tab.NEWS) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Logic to fix image loading and visibility
    val successState = state as? F1UiState.Success
    MacroCard(
        borderColor = F1Red.copy(alpha = 0.22f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header (always visible) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: F1 branding (non-clickable)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_f1_logo),
                        contentDescription = "Formula 1",
                        modifier = Modifier.height(28.dp),
                        contentScale = ContentScale.FillHeight,
                    )
                    Column {
                        Text("HUB", fontSize = 17.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = (-0.5).sp)
                        Text("2026 SEASON", fontSize = 8.sp, fontWeight = FontWeight.Black, color = F1Red.copy(alpha = 0.85f), letterSpacing = 1.5.sp)
                    }
                }
                // Right: action buttons + clickable chevron
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val successState = state as? F1UiState.Success
                    LastUpdatedText(
                        lastUpdatedAt = successState?.lastUpdatedAt,
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
                    val chevronRot by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "f1_hdr_chevron",
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                expanded = !expanded
                                if (expanded) haptics.toggleOn() else haptics.toggleOff()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = if (expanded) F1Red.copy(alpha = 0.75f) else TextSecondary.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp).rotate(chevronRot),
                        )
                    }
                }
            }

            // ── Compact content — always visible ─────────────────────────
            when (state) {
                is F1UiState.Loading -> {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = F1Red, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    }
                }
                is F1UiState.Error -> {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(F1Red.copy(alpha = 0.08f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Text("Lost telemetry uplink", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                is F1UiState.Success -> {
                    F1CollapsedWidget(state.f1Data)
                }
            }

            // ── Expanded extra content — slides in below compact view ─────
            AnimatedVisibility(
                visible = expanded,
                enter = MacroMotion.expandEnter,
                exit = MacroMotion.expandExit,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))

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

                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tabs.forEach { tab ->
                            val active = selectedTab == tab
                            val bg by animateColorAsState(if (active) F1Red else Surface.copy(alpha = 0.5f), tween(180), label = "bg")
                            val fg by animateColorAsState(if (active) Color.White else TextSecondary.copy(alpha = 0.7f), tween(180), label = "fg")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bg)
                                    .clickable { haptics.tick(); selectedTab = tab }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(tab.icon, null, tint = fg, modifier = Modifier.size(11.dp))
                                    Text(tab.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.4.sp)
                                }
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
                    AnimatedContent(
                        targetState = f1StateKey,
                        label = "f1Body",
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 10 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 10 })
                        }
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

                    Spacer(Modifier.height(4.dp))
                    WidgetExpandBar(
                        expanded = true,
                        onToggle = { expanded = false; haptics.toggleOff() },
                        accentColor = F1Red,
                        collapseLabel = "Show less",
                    )
                }
            }

            // ── Expand bar (collapsed state) ──────────────────────────────
            if (!expanded) {
                Spacer(Modifier.height(4.dp))
                WidgetExpandBar(
                    expanded = false,
                    onToggle = { expanded = true; haptics.toggleOn() },
                    accentColor = F1Red,
                    expandLabel = "Full Hub",
                )
            }
        }
    }
}

// ── Collapsed compact widget ──────────────────────────────────────────────────
@Composable
private fun F1CollapsedWidget(data: F1Standings) {
    val next = data.schedule.filter { !isPast(it.raceDate) }.minByOrNull { daysUntil(it.raceDate) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))

        // ── Dynamic info strip (same as expanded top) ─────────────────────
        DynamicInfoStrip(data)

        // ── Next race banner (same as expanded top) ───────────────────────
        if (next != null) {
            NextRaceBanner(next, daysUntil(next.raceDate), data.schedule)
        }
    }
}

// ── Dynamic info strip ────────────────────────────────────────────────────────
@Composable
private fun DynamicInfoStrip(data: F1Standings) {
    val leader          = data.driverStandings.firstOrNull()
    val p2              = data.driverStandings.getOrNull(1)
    val constructorLeader = data.constructorStandings.firstOrNull()
    val totalRounds     = data.schedule.size
    val completedRounds = data.schedule.count { isPast(it.raceDate) }
    val lastWinner      = data.lastRaceResults?.firstOrNull()

    // Build the list of chips to render
    data class ChipData(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val iconColor: Color,
        val label: String,
        val value: String,
        val sub: String,
        val accentColor: Color,
        val isSeason: Boolean = false,
        val seasonCompleted: Int = 0,
        val seasonTotal: Int = 0,
    )

    val chips = buildList {
        if (leader != null) {
            val tc = safeTeamColor(leader.teamColor)
            add(ChipData(Icons.Default.EmojiEvents, F1Gold, "WDC LEAD", leader.driverAcronym, "${leader.points.toInt()} PTS", tc))
        }
        if (leader != null && p2 != null) {
            val gap = (leader.points - p2.points).toInt()
            add(ChipData(Icons.Default.CompareArrows, F1Silver, "GAP P1→P2", "+$gap", "PTS LEAD", F1Silver))
        }
        if (constructorLeader != null) {
            val tc = safeTeamColor(constructorLeader.teamColor)
            add(ChipData(Icons.Default.Flag, tc, "WCC LEAD", constructorLeader.constructorName.split(" ").first().take(9), "${constructorLeader.points.toInt()} PTS", tc))
        }
        if (totalRounds > 0) {
            add(ChipData(Icons.Default.Speed, Primary, "SEASON", "${(completedRounds * 100 / totalRounds)}%", "R$completedRounds / $totalRounds", Primary, isSeason = true, seasonCompleted = completedRounds, seasonTotal = totalRounds))
        }
        if (lastWinner != null) {
            val winnerName = lastWinner.driverAcronym ?: lastWinner.driverName.split(" ").last().take(6).uppercase()
            val raceShort  = data.lastRaceName?.replace(" Grand Prix", " GP") ?: lastWinner.constructorName.split(" ").first().take(9)
            add(ChipData(Icons.Default.SportsScore, F1Red, "LAST WIN", winnerName, raceShort, F1Red))
        }
    }

    if (chips.isEmpty()) return

    // Split chips into rows of equal size
    val cols  = when (chips.size) { 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 2; else -> 3 }
    val rows  = chips.chunked(cols)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { chip ->
                    DynamicChip(
                        modifier    = Modifier.weight(1f),
                        icon        = chip.icon,
                        iconColor   = chip.iconColor,
                        label       = chip.label,
                        value       = chip.value,
                        sub         = chip.sub,
                        accentColor = chip.accentColor,
                        isSeason    = chip.isSeason,
                        seasonCompleted = chip.seasonCompleted,
                        seasonTotal     = chip.seasonTotal,
                    )
                }
                // Fill incomplete last row with invisible spacers so chips stay same width
                repeat(cols - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DynamicChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    sub: String,
    accentColor: Color,
    isSeason: Boolean = false,
    seasonCompleted: Int = 0,
    seasonTotal: Int = 0,
) {
    val animatedPct by animateFloatAsState(
        targetValue = if (isSeason && seasonTotal > 0) seasonCompleted.toFloat() / seasonTotal else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "chipSeasonPct",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = if (isSeason) 0.09f else 0.07f))
            .border(0.5.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // Label row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icon, null, tint = iconColor.copy(alpha = 0.85f), modifier = Modifier.size(10.dp))
                    Text(label, color = TextSecondary.copy(alpha = 0.7f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                // Main value
                Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Sub text
                Text(sub, color = accentColor.copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Season progress bar pinned to bottom (only for season chip)
            if (isSeason && seasonTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accentColor.copy(alpha = 0.15f))) {
                    Box(modifier = Modifier.fillMaxWidth(animatedPct).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.6f)))))
                }
            }
        }
    }
}

// ── Live Race Countdown ────────────────────────────────────────────────────────
@Composable
private fun LiveCountdown(dateStr: String, timeStr: String?, accentColor: Color) {
    var secondsLeft by remember { mutableLongStateOf(secondsUntilRace(dateStr, timeStr)) }
    LaunchedEffect(dateStr, timeStr) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft = secondsUntilRace(dateStr, timeStr)
        }
    }
    if (secondsLeft <= 0 || secondsLeft > 7 * 24 * 3600) return

    val days  = secondsLeft / 86400
    val hours = (secondsLeft % 86400) / 3600
    val mins  = (secondsLeft % 3600) / 60
    val secs  = secondsLeft % 60

    // Toggle at 600ms interval — avoids the 60fps recompose from rememberInfiniteTransition
    var dotVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600L)
            dotVisible = !dotVisible
        }
    }
    val dotAlpha = if (dotVisible) 1f else 0.5f

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(0.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).scale(dotAlpha).clip(CircleShape).background(accentColor))
        Spacer(Modifier.width(8.dp))
        Text("RACE IN  ", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        if (days > 0) {
            Text("${days}D ", color = accentColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Text(hours.toString().padStart(2, '0'), color = accentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(":", color = accentColor.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(mins.toString().padStart(2, '0'), color = accentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(":", color = accentColor.copy(alpha = 0.5f), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(secs.toString().padStart(2, '0'), color = accentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

// ── Next-race banner ──────────────────────────────────────────────────────────
@Composable
private fun NextRaceBanner(race: RaceScheduleEntry, days: Long, allRaces: List<RaceScheduleEntry>) {
    val isSoon = days <= 7
    val col = if (isSoon) F1Red else Primary
    var showTrack by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.07f)).border(0.5.dp, col.copy(alpha = 0.15f), RoundedCornerShape(12.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { haptics.tick(); showTrack = !showTrack }.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(race.countryCode ?: "🏁", fontSize = 24.sp)
                Column {
                    Text("NEXT: ${shortGP(race.raceName).uppercase()}", color = col, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    Text("${race.locality} · ${race.circuitName}", color = TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val localRaceTime = remember(race.raceDate, race.raceTime) { formatLocalTime(race.raceDate, race.raceTime) }
                    val tz = remember { getLocalTimezone() }
                    if (localRaceTime.isNotEmpty()) {
                        Text("Race: ${formatShort(race.raceDate)} · $localRaceTime ($tz)", color = TextSecondary, fontSize = 9.sp)
                    } else {
                        Text("Race: ${formatShort(race.raceDate)}", color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when { days == 0L -> "TODAY"; days < 0 -> "PAST"; else -> "${days}D" },
                    color = if (isSoon) F1Red else TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp
                )
                if (race.sprintDate != null) Text("SPRINT WKD", color = SprintPink, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Icon(if (showTrack) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = col.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
        }

        // Live countdown only when within 7 days
        if (isSoon && days >= 0) {
            Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                LiveCountdown(race.raceDate, race.raceTime, col)
            }
        }

        AnimatedVisibility(showTrack, enter = expandVertically(tween(250)) + fadeIn(), exit = shrinkVertically(tween(200)) + fadeOut()) {
            Column(
                modifier = Modifier.fillMaxWidth().background(col.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = col.copy(alpha = 0.2f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    race.laps?.let { CircuitStat("LAPS", "$it") }
                    race.lapRecord?.let { CircuitStat("LAP REC", it) }
                    CircuitStat("ROUND", "${race.round}/${allRaces.size}")
                    race.qualifyingDate?.let { CircuitStat("QUALI", formatShort(it)) }
                }
                TrackVisualization(circuitId = race.circuitId ?: "", accentColor = col, raceName = shortGP(race.raceName))
                race.fp1Date?.let    { SessionRow("FP1",   it, null,                Primary.copy(alpha = 0.7f)) }
                race.fp2Date?.let    { SessionRow("FP2",   it, null,                Primary.copy(alpha = 0.7f)) }
                race.fp3Date?.let    { SessionRow("FP3",   it, null,                Primary.copy(alpha = 0.7f)) }
                race.qualifyingDate?.let { SessionRow("QUALI", it, race.qualifyingTime, Primary) }
                race.sprintDate?.let     { SessionRow("SPRINT", it, race.sprintTime, SprintPink) }
                SessionRow("RACE", race.raceDate, race.raceTime, col, bold = true)
            }
        }
    }
}

@Composable
private fun CircuitStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        Text(value, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            Text("CIRCUIT MAP", color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("· ${raceName.uppercase()}", color = TextSecondary, fontSize = 7.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF080D14))) {
            if (svgUrl != null) {
                val request = remember(svgUrl) {
                    ImageRequest.Builder(context)
                        .data(svgUrl)
                        .crossfade(true)
                        .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                SubcomposeAsyncImage(model = request, contentDescription = "$raceName circuit map", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accentColor.copy(alpha = 0.7f))
                                Text("LOADING CIRCUIT...", color = accentColor.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                        is AsyncImagePainter.State.Error -> TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            } else {
                TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
            }
            Text(raceName.uppercase(), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), color = Color.White.copy(alpha = 0.08f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
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
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = F1Red, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text("LOADING TELEMETRY...", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun F1Error(onRefresh: () -> Unit, haptics: com.macrotracker.ui.util.HapticHelper) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BOX BOX BOX!", color = F1Red, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text("Lost telemetry uplink", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { haptics.confirm(); onRefresh() }, colors = ButtonDefaults.buttonColors(containerColor = F1Red), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("RECONNECT", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

// ── News feed ─────────────────────────────────────────────────────────────────
@Composable
fun F1NewsFeed(news: List<F1NewsArticle>) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    if (news.isEmpty()) { EmptyF1State("No transmissions from the paddock."); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        news.forEachIndexed { i, article ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 50L); visible = true }
            AnimatedVisibility(visible, enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { 20 }) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Surface.copy(alpha = 0.5f))
                        .clickable {
                            haptics.tick()
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri())) } catch (_: Exception) {}
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(F1Red))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(F1Red.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text(article.category.take(12), color = F1Red, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(article.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                        if (article.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(article.description, color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary.copy(alpha = 0.35f), modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
                }
            }
        }
    }
}

// ── Driver standings ──────────────────────────────────────────────────────────
@Composable
fun DriverStandingsList(standings: List<SeasonDriverStanding>) {
    if (standings.isEmpty()) { EmptyF1State("No championship data found."); return }
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val leader = standings.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        standings.take(20).forEachIndexed { idx, driver ->
            val tc = safeTeamColor(driver.teamColor)
            val medal = medalColor(driver.position)
            val isExp = expanded == driver.driverAcronym
            val isLeader = driver.position == 1
            val gapToLeader = if (leader != null && !isLeader) (leader.points - driver.points).toInt() else null
            val scl by animateFloatAsState(if (isExp) 1.01f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "scl")

            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(idx * 30L); visible = true }

            AnimatedVisibility(visible, enter = fadeIn(tween(180)) + slideInHorizontally(tween(200)) { -20 }) {
                Column(
                    modifier = Modifier.fillMaxWidth().scale(scl)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isLeader) Brush.horizontalGradient(listOf(tc.copy(alpha = 0.14f), Surface.copy(alpha = 0.5f)))
                            else Brush.horizontalGradient(listOf(Surface.copy(0.42f), Surface.copy(0.42f)))
                        )
                        .clickable { haptics.tick(); expanded = if (isExp) null else driver.driverAcronym }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(medal ?: tc.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text(driver.position.toString(), color = if (medal != null) Color.White else tc, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        DriverHeadshot(url = driver.headshotUrl, driverName = driver.driverName, driverAcronym = driver.driverAcronym, driverNumber = driver.driverNumber, teamColor = tc, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                driver.driverNumber?.let { Text("#$it", color = tc, fontWeight = FontWeight.Black, fontSize = 10.sp) }
                                Text(driver.driverAcronym, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                                driver.nationality?.let { Text(it, fontSize = 12.sp) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TeamLogo(url = driver.teamLogoUrl, teamName = driver.constructorName, modifier = Modifier.size(width = 20.dp, height = 13.dp))
                                Text(driver.constructorName, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (gapToLeader != null) {
                                Text("-$gapToLeader pts", color = TextSecondary.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${driver.points.toInt()}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("PTS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            if (driver.wins > 0) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(10.dp))
                                    Text("${driver.wins}", color = F1Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    AnimatedVisibility(isExp, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(150)) + fadeOut()) {
                        Column(modifier = Modifier.fillMaxWidth().background(tc.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            HorizontalDivider(color = tc.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                StatChip("FULL NAME", driver.driverName.split(" ").last().uppercase(), tc)
                                StatChip("TEAM", driver.constructorName.split(" ").first(), tc)
                                StatChip("WINS", driver.wins.toString(), tc)
                                StatChip("PTS", driver.points.toInt().toString(), tc)
                            }
                            if (driver.podiums > 0 || driver.fastestLaps > 0) {
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    if (driver.podiums > 0) StatChip("PODIUMS", driver.podiums.toString(), F1Bronze)
                                    if (driver.fastestLaps > 0) StatChip("FASTEST LAPS", driver.fastestLaps.toString(), FL_Purple)
                                }
                            }
                            // Animated points bar vs leader
                            if (leader != null && leader.points > 0) {
                                Spacer(Modifier.height(8.dp))
                                val ratio = (driver.points / leader.points).toFloat().coerceIn(0f, 1f)
                                val bar by animateFloatAsState(ratio, tween(700, easing = FastOutSlowInEasing), label = "dbar")
                                Text("POINTS VS LEADER", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(tc.copy(alpha = 0.15f))) {
                                    Box(modifier = Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(tc, tc.copy(alpha = 0.5f)))))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Constructor standings ─────────────────────────────────────────────────────
@Composable
fun ConstructorStandingsList(teams: List<SeasonConstructorStanding>) {
    if (teams.isEmpty()) { EmptyF1State("No constructor standings available."); return }
    val maxPts = teams.maxOfOrNull { it.points } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        teams.forEachIndexed { i, team ->
            val tc = safeTeamColor(team.teamColor)
            val ratio = (team.points / maxPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(700 + i * 60, easing = FastOutSlowInEasing), label = "bar$i")
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 50L); visible = true }
            AnimatedVisibility(visible, enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { -20 }) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface.copy(alpha = 0.5f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(medalColor(team.position) ?: tc.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text("${team.position}", color = if (medalColor(team.position) != null) Color.White else tc, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(Background.copy(alpha = 0.8f)).border(0.5.dp, tc.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).padding(3.dp), contentAlignment = Alignment.Center) {
                        TeamLogo(url = team.teamLogoUrl, teamName = team.constructorName, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(team.constructorName.uppercase(), color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.3.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(tc.copy(alpha = 0.15f))) {
                            Box(modifier = Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(tc, tc.copy(alpha = 0.5f)))))
                        }
                        Spacer(Modifier.height(3.dp))
                        if (team.wins > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(9.dp))
                                Text("${team.wins} WINS", color = F1Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${team.points.toInt()}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("PTS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── Mode selector ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface.copy(alpha = 0.4f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("DRIVERS", "TEAMS", "DUELS").forEachIndexed { i, label ->
                val active = battleMode == i
                val bg by animateColorAsState(if (active) F1Red else Color.Transparent, tween(200), label = "bmode$i")
                val fg by animateColorAsState(if (active) Color.White else TextSecondary, tween(200), label = "fmode$i")
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(bg)
                        .clickable { haptics.tick(); battleMode = i }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
        }

        AnimatedContent(
            targetState = battleMode,
            label = "battleContent",
            transitionSpec = {
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { if (targetState > initialState) it / 8 else -it / 8 })
                    .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { if (targetState > initialState) -it / 8 else it / 8 })
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── Top 2 face-off card ───────────────────────────────────────────
        if (drivers.size >= 2) {
            val d1 = drivers[0]; val d2 = drivers[1]
            val tc1 = safeTeamColor(d1.teamColor); val tc2 = safeTeamColor(d2.teamColor)
            val gap = (d1.points - d2.points).toInt()

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(tc1.copy(alpha = 0.22f), CardBg.copy(alpha = 0.6f), tc2.copy(alpha = 0.22f))))
                    .border(0.5.dp, Brush.horizontalGradient(listOf(tc1.copy(alpha = 0.35f), tc2.copy(alpha = 0.35f))), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(F1Red.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = F1Red, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("WDC BATTLE", color = F1Red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Driver 1 (leader)
                        DriverBattleCard(d1, 1, tc1, isLeader = true)
                        // Gap badge
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                            Box(modifier = Modifier.clip(CircleShape).background(F1Red).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("VS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("+$gap", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text("pts gap", color = TextSecondary.copy(alpha = 0.5f), fontSize = 7.sp)
                        }
                        // Driver 2
                        DriverBattleCard(d2, 2, tc2, isLeader = false)
                    }
                    // Gap bar
                    val total = d1.points + d2.points
                    val ratio1 = if (total > 0) (d1.points / total).toFloat() else 0.5f
                    val animRatio by animateFloatAsState(ratio1, tween(900, easing = FastOutSlowInEasing), label = "gapbar")
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(tc2.copy(alpha = 0.3f))) {
                        Box(modifier = Modifier.fillMaxWidth(animRatio).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(tc1, tc1.copy(alpha = 0.6f)))))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${d1.points.toInt()} PTS", color = tc1, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${d2.points.toInt()} PTS", color = tc2, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // ── Standings bars for top 5 ──────────────────────────────────────
        SectionHeader("FULL TOP 5", Icons.Default.Leaderboard, TextSecondary)
        top5.forEachIndexed { i, d ->
            val tc = safeTeamColor(d.teamColor)
            val ratio = (d.points / maxPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(800 + i * 80, easing = FastOutSlowInEasing), label = "wdc$i")
            val isSelected = selectedDriver == d.driverAcronym

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) tc.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { haptics.tick(); selectedDriver = if (isSelected) null else d.driverAcronym }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(medalColor(d.position) ?: tc.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text("${d.position}", color = if (medalColor(d.position) != null) Color.White else tc, fontWeight = FontWeight.Black, fontSize = 9.sp)
                    }
                    DriverHeadshot(url = d.headshotUrl, driverName = d.driverName, driverAcronym = d.driverAcronym, driverNumber = d.driverNumber, teamColor = tc, modifier = Modifier.size(30.dp))
                    Text(d.driverAcronym, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp, modifier = Modifier.width(36.dp))
                    Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(tc.copy(alpha = 0.1f))) {
                        Box(modifier = Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(tc, tc.copy(alpha = 0.55f)))))
                        Text("${d.points.toInt()}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                    }
                    if (d.wins > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(9.dp))
                            Text("${d.wins}", color = F1Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                AnimatedVisibility(isSelected) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (d.podiums > 0) StatChip("PODIUMS", "${d.podiums}", F1Bronze)
                        if (d.fastestLaps > 0) StatChip("FL", "${d.fastestLaps}", FL_Purple)
                        StatChip("TEAM", d.constructorName.split(" ").first(), tc)
                        d.nationality?.let { StatChip("NAT", it, TextSecondary) }
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(88.dp)
    ) {
        if (isLeader) {
            Text("👑", fontSize = 14.sp)
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Box(
            modifier = Modifier.size(width = 68.dp, height = 78.dp)
                .border(1.dp, tc.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            DriverHeadshot(
                url = driver.headshotUrl,
                driverName = driver.driverName,
                driverAcronym = driver.driverAcronym,
                driverNumber = driver.driverNumber,
                teamColor = tc,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                .clip(RoundedCornerShape(4.dp)).background(tc.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text("P$pos", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(driver.driverAcronym, color = tc, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
        Text(driver.constructorName.split(" ").first().take(8).uppercase(), color = TextSecondary, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WCCBattleContent(constructors: List<SeasonConstructorStanding>, haptics: com.macrotracker.ui.util.HapticHelper) {
    val maxTeamPts = constructors.maxOfOrNull { it.points } ?: 1.0
    var selectedTeam by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Leader spotlight
        constructors.firstOrNull()?.let { leader ->
            val tc = safeTeamColor(leader.teamColor)
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(tc.copy(alpha = 0.20f), CardBg.copy(alpha = 0.7f))))
                    .border(0.5.dp, tc.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(width = 60.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Background.copy(alpha = 0.8f))
                        .border(0.5.dp, tc.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(4.dp),
                        contentAlignment = Alignment.Center) {
                        TeamLogo(url = leader.teamLogoUrl, teamName = leader.constructorName, modifier = Modifier.fillMaxSize())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🏆 WCC LEADER", color = F1Gold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(leader.constructorName.uppercase(), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.3.sp)
                        if (leader.wins > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(10.dp))
                            Text("${leader.wins} WINS", color = F1Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${leader.points.toInt()}", color = tc, fontWeight = FontWeight.Black, fontSize = 28.sp)
                        Text("PTS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionHeader("CONSTRUCTORS' CHAMPIONSHIP", Icons.Default.Flag, F1Red)
        constructors.take(10).forEachIndexed { i, t ->
            val tc = safeTeamColor(t.teamColor)
            val ratio = (t.points / maxTeamPts).toFloat().coerceIn(0f, 1f)
            val bar by animateFloatAsState(ratio, tween(800 + i * 80, easing = FastOutSlowInEasing), label = "wcc$i")
            val isSelected = selectedTeam == t.constructorName
            val isTop3 = t.position <= 3

            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 60L); visible = true }
            AnimatedVisibility(visible, enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { -20 }) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) tc.copy(alpha = 0.1f) else if (isTop3) tc.copy(alpha = 0.05f) else Color.Transparent)
                        .clickable { haptics.tick(); selectedTeam = if (isSelected) null else t.constructorName }
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(medalColor(t.position) ?: tc.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("${t.position}", color = if (medalColor(t.position) != null) Color.White else tc, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                        Box(modifier = Modifier.size(width = 36.dp, height = 24.dp).clip(RoundedCornerShape(5.dp))
                            .background(Background.copy(alpha = 0.8f)).border(0.5.dp, tc.copy(alpha = 0.25f), RoundedCornerShape(5.dp)).padding(2.dp),
                            contentAlignment = Alignment.Center) {
                            TeamLogo(url = t.teamLogoUrl, teamName = t.constructorName, modifier = Modifier.fillMaxSize())
                        }
                        Text(t.constructorName.split(" ").first().take(9), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.width(58.dp), overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(tc.copy(alpha = 0.1f))) {
                            Box(modifier = Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                .background(Brush.horizontalGradient(listOf(tc, tc.copy(alpha = 0.55f)))))
                            Text("${t.points.toInt()}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                        }
                        if (t.wins > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(9.dp))
                                Text("${t.wins}", color = F1Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamDuelsContent(drivers: List<SeasonDriverStanding>, @Suppress("UNUSED_PARAMETER") haptics: com.macrotracker.ui.util.HapticHelper) {
    val teamsMap = drivers.groupBy { it.constructorName }
    val duelTeams = teamsMap.entries.filter { it.value.size >= 2 }
        .sortedBy { entry -> drivers.indexOfFirst { it.constructorName == entry.key } }

    if (duelTeams.isEmpty()) { EmptyF1State("No teammate duel data available."); return }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("TEAMMATE BATTLES", Icons.Default.CompareArrows, F1Silver)
        Text("Head-to-head points comparison within each team", color = TextSecondary, fontSize = 9.sp)

        duelTeams.forEach { (teamName, teamDrivers) ->
            val sorted = teamDrivers.sortedByDescending { it.points }
            val d1 = sorted[0]; val d2 = sorted[1]
            val tc = safeTeamColor(d1.teamColor)
            val total = d1.points + d2.points
            val ratio1 = if (total > 0) (d1.points / total).toFloat() else 0.5f
            val aRatio by animateFloatAsState(ratio1, tween(900, easing = FastOutSlowInEasing), label = "tm_${d1.driverAcronym}")
            val ptsDiff = (d1.points - d2.points).toInt()

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(tc.copy(alpha = 0.14f), CardBg.copy(alpha = 0.6f), tc.copy(alpha = 0.07f))))
                    .border(0.5.dp, tc.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Team header
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(width = 28.dp, height = 18.dp).clip(RoundedCornerShape(4.dp))
                            .background(Background.copy(alpha = 0.8f)).border(0.5.dp, tc.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(2.dp),
                            contentAlignment = Alignment.Center) {
                            TeamLogo(url = d1.teamLogoUrl, teamName = teamName, modifier = Modifier.fillMaxSize())
                        }
                        Text(teamName.uppercase(), color = tc, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.5.sp)
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tc.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("+$ptsDiff pts", color = tc, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Driver face-off row
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Driver 1
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                            DriverHeadshot(url = d1.headshotUrl, driverName = d1.driverName, driverAcronym = d1.driverAcronym,
                                driverNumber = d1.driverNumber, teamColor = tc, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(d1.driverAcronym, color = tc, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                            Text("${d1.points.toInt()} pts", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        // Duel bar
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Spacer(Modifier.height(8.dp))
                            // Wins comparison
                            if (d1.wins > 0 || d2.wins > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(9.dp))
                                        Text("${d1.wins}", color = F1Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Text("WINS", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${d2.wins}", color = if (d2.wins > 0) F1Gold else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        Icon(Icons.Default.EmojiEvents, null, tint = if (d2.wins > 0) F1Gold else TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(9.dp))
                                    }
                                }
                            }
                            // Points bar
                            Text("POINTS", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(tc.copy(alpha = 0.12f))) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.fillMaxWidth(aRatio).fillMaxHeight()
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = if (aRatio >= 0.98f) 8.dp else 0.dp, bottomEnd = if (aRatio >= 0.98f) 8.dp else 0.dp))
                                        .background(tc.copy(alpha = 0.8f)))
                                }
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(aRatio * 100).toInt()}%", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp))
                                    Text("${((1f - aRatio) * 100).toInt()}%", color = if (aRatio < 0.95f) TextPrimary else Color.White.copy(alpha = 0.3f), fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 6.dp))
                                }
                            }
                            // Podiums comparison
                            if (d1.podiums > 0 || d2.podiums > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${d1.podiums}", color = F1Bronze, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    Text("PODIUMS", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    Text("${d2.podiums}", color = F1Bronze, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // Driver 2
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                            DriverHeadshot(url = d2.headshotUrl, driverName = d2.driverName, driverAcronym = d2.driverAcronym,
                                driverNumber = d2.driverNumber, teamColor = tc, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(d2.driverAcronym, color = tc.copy(alpha = 0.75f), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                            Text("${d2.points.toInt()} pts", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

// ── Race schedule ─────────────────────────────────────────────────────────────
@Composable
fun RaceScheduleList(schedule: List<RaceScheduleEntry>) {
    if (schedule.isEmpty()) { EmptyF1State("Schedule not yet available."); return }
    val haptics = rememberHaptics()
    var expandedRound by rememberSaveable { mutableStateOf<Int?>(null) }
    val nextIdx = schedule.indexOfFirst { !isPast(it.raceDate) }.takeIf { it >= 0 }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        schedule.forEachIndexed { idx, race ->
            val past   = isPast(race.raceDate)
            val days   = daysUntil(race.raceDate)
            val isNext = idx == nextIdx
            val isExp  = expandedRound == race.round
            val sprint = race.sprintDate != null

            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isNext) Brush.horizontalGradient(listOf(F1Red.copy(alpha = 0.13f), CardBg.copy(alpha = 0.8f)))
                        else Brush.horizontalGradient(listOf(Surface.copy(if (past) 0.22f else 0.5f), Surface.copy(if (past) 0.22f else 0.5f)))
                    )
                    .clickable { haptics.tick(); expandedRound = if (isExp) null else race.round }
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp).clip(RoundedCornerShape(8.dp)).background(if (isNext) F1Red else Surface).padding(vertical = 4.dp)
                    ) {
                        Text(formatMonth(race.raceDate), color = if (isNext) Color.White.copy(alpha = 0.8f) else TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(formatDay(race.raceDate), color = if (isNext) Color.White else TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(race.countryCode ?: "🏁", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("R${race.round}", color = if (isNext) F1Red else TextSecondary.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                if (sprint) Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(SprintPink.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("SPRINT", color = SprintPink, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                }
                            }
                            Text(shortGP(race.raceName), color = if (past) TextSecondary else TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(race.locality, color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            past       -> Text("DONE", color = TextSecondary.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            days == 0L -> Text("TODAY", color = F1Red, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            days <= 7L -> Text("${days}D", color = F1Red, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            else       -> Text("${days}D", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    }
                }
                AnimatedVisibility(isExp, enter = expandVertically(tween(200)) + fadeIn(), exit = shrinkVertically(tween(150)) + fadeOut()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isNext) F1Red.copy(alpha = 0.07f) else Surface.copy(alpha = 0.3f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        HorizontalDivider(color = if (isNext) F1Red.copy(alpha = 0.3f) else Border.copy(alpha = 0.3f))
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(race.circuitName, color = TextSecondary, fontSize = 10.sp)
                            Text("Times: ${getLocalTimezone()}", color = TextSecondary.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            race.laps?.let { CircuitStat("LAPS", "$it") }
                            race.lapRecord?.let { CircuitStat("LAP REC", it) }
                            race.lapRecordHolder?.let { CircuitStat("HELD BY", it.split(" (").first()) }
                        }
                        Spacer(Modifier.height(2.dp))
                        if (!race.circuitId.isNullOrBlank()) {
                            TrackVisualization(circuitId = race.circuitId, accentColor = if (isNext) F1Red else Primary, raceName = shortGP(race.raceName))
                            Spacer(Modifier.height(4.dp))
                        }
                        race.fp1Date?.let    { SessionRow("FP1",   it, null,                Primary.copy(alpha = 0.7f)) }
                        race.fp2Date?.let    { SessionRow("FP2",   it, null,                Primary.copy(alpha = 0.7f)) }
                        race.fp3Date?.let    { SessionRow("FP3",   it, null,                Primary.copy(alpha = 0.7f)) }
                        race.qualifyingDate?.let { SessionRow("QUALI", it, race.qualifyingTime, Primary) }
                        race.sprintDate?.let     { SessionRow("SPRINT", it, race.sprintTime, SprintPink) }
                        SessionRow("RACE", race.raceDate, race.raceTime, if (isNext) F1Red else TextPrimary, bold = true)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = 0.7f)))
            Text(label, color = color, fontSize = if (bold) 11.sp else 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (localTimeStr.isNotEmpty()) "${formatShort(date)} · $localTimeStr" else formatShort(date),
                color = if (bold) TextPrimary else TextSecondary,
                fontSize = if (bold) 11.sp else 10.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
            if (utcTimeStr.isNotEmpty() && localTimeStr.isNotEmpty()) {
                Text("$utcTimeStr UTC", color = TextSecondary.copy(alpha = 0.45f), fontSize = 8.sp)
            }
        }
    }
}

// ── Qualifying Results ────────────────────────────────────────────────────────
@Composable
fun QualiResultsList(results: List<QualiResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No qualifying data available."); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        raceName?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(FL_Purple.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("🔵 QUALI · ${shortGP(it).uppercase()}", color = FL_Purple, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        // Pole position spotlight
        results.firstOrNull()?.let { pole ->
            val poleTC = safeTeamColor(pole.teamColor)
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(FL_Purple.copy(alpha = 0.18f), CardBg.copy(alpha = 0.6f))))
                .border(0.5.dp, FL_Purple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(FL_Purple.copy(alpha = 0.22f)).padding(horizontal = 7.dp, vertical = 4.dp)) {
                            Text("POLE", color = FL_Purple, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        DriverHeadshot(
                            url = pole.headshotUrl,
                            driverName = pole.driverName,
                            driverAcronym = pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                            driverNumber = null,
                            teamColor = poleTC,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                            color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 0.5.sp)
                        Text(pole.constructorName, color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(pole.q3Time ?: pole.q1Time ?: "--:--.---", color = FL_Purple, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("POLE TIME", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        val q3Drivers = results.filter { it.q3Time != null }
        val q2Only = results.filter { it.q2Time != null && it.q3Time == null }
        val q1Only = results.filter { it.q1Time != null && it.q2Time == null }

        if (q3Drivers.isNotEmpty()) {
            QualiGroupHeader("Q3 — POLE BATTLE", FL_Purple)
            q3Drivers.forEach { r -> QualiRow(r, bestTime = r.q3Time, accentColor = if (r.position == 1) FL_Purple else safeTeamColor(r.teamColor)) }
        }
        if (q2Only.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            QualiGroupHeader("Q2 — ELIMINATED", Primary.copy(alpha = 0.85f))
            q2Only.forEach { r -> QualiRow(r, bestTime = r.q2Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        if (q1Only.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            QualiGroupHeader("Q1 — ELIMINATED", TextSecondary.copy(alpha = 0.7f))
            q1Only.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
        // If no segments (older format), show flat list
        if (q3Drivers.isEmpty() && q2Only.isEmpty() && q1Only.isEmpty()) {
            results.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
        }
    }
}

@Composable
private fun QualiGroupHeader(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun QualiRow(result: QualiResult, bestTime: String?, accentColor: Color) {
    val isPole = result.position == 1
    val tc = safeTeamColor(result.teamColor)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(result.position * 20L); visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(150)) + slideInHorizontally(tween(170)) { 20 }) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isPole) accentColor.copy(alpha = 0.10f) else Surface.copy(alpha = 0.38f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(medalColor(result.position) ?: accentColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text("${result.position}", color = if (medalColor(result.position) != null) Color.White else accentColor, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(Modifier.width(6.dp))
            DriverHeadshot(
                url = result.headshotUrl,
                driverName = result.driverName,
                driverAcronym = result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
                driverNumber = null,
                teamColor = tc,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
                Text(result.constructorName, color = TextSecondary, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(bestTime ?: "--:--.---", color = if (isPole) accentColor else TextPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                if (result.gapToP1 != null) Text(result.gapToP1, color = TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(accentColor.copy(alpha = 0.5f)))
        }
    }
}

// ── Last race results ─────────────────────────────────────────────────────────
@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val flDriver = results.firstOrNull { it.fastestLap }
    val dnfCount = results.count { it.status != null && it.time == null && it.status != "Finished" }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        raceName?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(F1Red.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("🏁 ${shortGP(it).uppercase()}", color = F1Red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        // Quick stats strip
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface.copy(alpha = 0.35f)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WINNER", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Text(results.firstOrNull()?.driverAcronym ?: "---", color = F1Gold, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(28.dp).background(Border.copy(alpha = 0.4f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = TextSecondary, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(2.dp))
                Text("FASTEST", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(28.dp).background(Border.copy(alpha = 0.4f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FINISHERS", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Text("${results.size - dnfCount}/${results.size}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            if (dnfCount > 0) {
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(Border.copy(alpha = 0.4f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DNF", color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Text("$dnfCount", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }

        val podium = results.filter { it.position in 1..3 }
        if (podium.size >= 3) {
            PodiumDisplay(podium[0], podium[1], podium[2])
            Spacer(Modifier.height(4.dp))
        }
        results.drop(if (podium.size >= 3) 3 else 0).take(17).forEachIndexed { i, r ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 25L); visible = true }
            AnimatedVisibility(visible, enter = fadeIn(tween(160)) + slideInHorizontally(tween(180)) { 20 }) {
                RaceResultRow(r)
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (isPoints) Surface.copy(alpha = 0.48f) else Surface.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("P${r.position}", color = if (isPoints) TextPrimary else TextSecondary.copy(alpha = 0.4f), fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.width(26.dp))
        DriverHeadshot(
            url = r.headshotUrl,
            driverName = r.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            acronym,
            color = if (isPoints) TextPrimary else TextSecondary, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(34.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(r.constructorName, color = TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.grid != null && r.grid > 0) Text("Grid P${r.grid}", color = TextSecondary.copy(alpha = 0.45f), fontSize = 7.sp)
        }
        if (posGained != null && posGained != 0) {
            val gainColor = if (posGained > 0) Color(0xFF22C55E) else Color(0xFFEF4444)
            val gainIcon = if (posGained > 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 3.dp)) {
                Icon(gainIcon, null, tint = gainColor, modifier = Modifier.size(11.dp))
                Text("${kotlin.math.abs(posGained)}", color = gainColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
        if (r.fastestLap) {
            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(FL_Purple.copy(alpha = 0.18f)).padding(horizontal = 4.dp, vertical = 2.dp).padding(end = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = FL_Purple, modifier = Modifier.size(10.dp))
                    Text("FL", color = FL_Purple, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.time ?: r.status ?: "+?", color = if (r.time != null) TextPrimary else TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
            if (r.points > 0) Text("+${r.points.toInt()} pts", color = F1Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PodiumDisplay(p1: RaceResult, p2: RaceResult, p3: RaceResult) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(CardBg.copy(alpha = 0.8f), CardBg.copy(alpha = 0.5f))))
        .border(0.5.dp, F1Gold.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.EmojiEvents, null, tint = F1Gold, modifier = Modifier.size(11.dp))
                    Text("RACE PODIUM", color = F1Gold.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                PodiumDriver(p2, 2, 52.dp)
                PodiumDriver(p1, 1, 76.dp)
                PodiumDriver(p3, 3, 38.dp)
            }
        }
    }
}

@Composable
private fun PodiumDriver(result: RaceResult, pos: Int, height: Dp) {
    val medal = medalColor(pos)!!
    val tc = safeTeamColor(result.teamColor)
    val acronym = result.driverAcronym ?: result.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    val headshotSize = if (pos == 1) 64.dp else 50.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.width(96.dp)) {
        if (result.fastestLap) {
            Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(FL_Purple.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = FL_Purple, modifier = Modifier.size(10.dp))
                    Text("FL", color = FL_Purple, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
            }
        } else if (pos == 1) {
            Text("👑", fontSize = 14.sp)
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Box(
            modifier = Modifier.size(headshotSize)
                .border(1.5.dp, medal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        ) {
            DriverHeadshot(
                url = result.headshotUrl,
                driverName = result.driverName,
                driverAcronym = acronym,
                driverNumber = null,
                teamColor = tc,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
        }
        Text(acronym, color = medal, fontWeight = FontWeight.Black, fontSize = if (pos == 1) 13.sp else 10.sp, letterSpacing = 0.5.sp)
        Text(result.constructorName.split(" ").first().take(8).uppercase(), color = TextSecondary, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (result.time != null && pos > 1) Text(result.time, color = TextSecondary, fontSize = 7.sp)
        if (pos == 1 && result.time != null) Text(result.time, color = F1Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier.width(76.dp).height(height).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = if (pos == 3) 6.dp else 0.dp, bottomEnd = if (pos == 3) 6.dp else 0.dp))
                .background(Brush.verticalGradient(listOf(medal.copy(alpha = 0.3f), medal.copy(alpha = 0.1f)))),
            contentAlignment = Alignment.TopCenter
        ) {
            Text("P$pos", color = medal.copy(alpha = 0.7f), fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
fun EmptyF1State(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Flag, null, tint = TextSecondary.copy(alpha = 0.22f), modifier = Modifier.size(36.dp))
            Text(message, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

