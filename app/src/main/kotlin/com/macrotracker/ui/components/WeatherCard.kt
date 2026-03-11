package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.WeatherUiState

private enum class TimeOfDay { DAY, NIGHT, TWILIGHT }

private fun parseTimeOfDay(symbolCode: String): TimeOfDay = when {
    symbolCode.contains("_night") -> TimeOfDay.NIGHT
    symbolCode.contains("_polartwilight") -> TimeOfDay.TWILIGHT
    else -> TimeOfDay.DAY
}

private fun weatherGradient(symbolCode: String): Brush {
    val base = symbolCode
        .replace("_day", "")
        .replace("_night", "")
        .replace("_polartwilight", "")
    val tod = parseTimeOfDay(symbolCode)

    return when {
        base == "clearsky" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFF81D4FA)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF1A237E)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFFE65100)),
            )
        }
        base == "fair" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1976D2), Color(0xFF42A5F5), Color(0xFF90CAF9)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B3A5C), Color(0xFF263238)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF283593), Color(0xFF5C3D8F), Color(0xFFBF360C)),
            )
        }
        base.startsWith("partlycloudy") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1520), Color(0xFF1A2733), Color(0xFF263238)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2040), Color(0xFF37474F), Color(0xFF5D4037)),
            )
        }
        base == "cloudy" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF607D8B)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0A1015), Color(0xFF1A2333), Color(0xFF263040)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1A2E), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        base == "fog" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF455A64), Color(0xFF607D8B), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1C2530), Color(0xFF2A3540), Color(0xFF384550)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF2E2E3A), Color(0xFF455A64), Color(0xFF5D4037)),
            )
        }
        base.contains("thunder") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1530), Color(0xFF311B92), Color(0xFF4A148C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D0A1A), Color(0xFF1A1530), Color(0xFF2D1F4A)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1530), Color(0xFF2D1F4A), Color(0xFF4E342E)),
            )
        }
        base.contains("rain") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF455A64)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0A1520), Color(0xFF15202D), Color(0xFF1E3040)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2030), Color(0xFF2E3545), Color(0xFF3E2723)),
            )
        }
        base.contains("sleet") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1820), Color(0xFF1A2530), Color(0xFF304558)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2530), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        base.contains("snow") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1520), Color(0xFF1E2838), Color(0xFF2A3B50)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2030), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        else -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFF42A5F5)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF0D1B2A), Color(0xFF111827), Color(0xFF1A2438)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A237E), Color(0xFF311B92), Color(0xFFBF360C)),
            )
        }
    }
}

private fun weatherAccentColor(symbolCode: String): Color {
    val base = symbolCode
        .replace("_day", "")
        .replace("_night", "")
        .replace("_polartwilight", "")
    val tod = parseTimeOfDay(symbolCode)

    return when {
        base == "clearsky" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFFFC107)       // golden sun
            TimeOfDay.NIGHT -> Color(0xFFB0BEC5)     // moonlight silver
            TimeOfDay.TWILIGHT -> Color(0xFFFF8A65)   // sunset orange
        }
        base == "fair" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFFFD54F)
            TimeOfDay.NIGHT -> Color(0xFF90A4AE)
            TimeOfDay.TWILIGHT -> Color(0xFFFFAB91)
        }
        base.startsWith("partlycloudy") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF90CAF9)
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFCE93D8)
        }
        base == "cloudy" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF90A4AE)
            TimeOfDay.NIGHT -> Color(0xFF546E7A)
            TimeOfDay.TWILIGHT -> Color(0xFF8D6E63)
        }
        base == "fog" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFB0BEC5)
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFA1887F)
        }
        base.contains("thunder") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFCE93D8)
            TimeOfDay.NIGHT -> Color(0xFFB388FF)
            TimeOfDay.TWILIGHT -> Color(0xFFEA80FC)
        }
        base.contains("rain") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF64B5F6)
            TimeOfDay.NIGHT -> Color(0xFF5C6BC0)
            TimeOfDay.TWILIGHT -> Color(0xFF7986CB)
        }
        base.contains("snow") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFE0E0E0)
            TimeOfDay.NIGHT -> Color(0xFFB0BEC5)
            TimeOfDay.TWILIGHT -> Color(0xFFCFD8DC)
        }
        else -> when (tod) {
            TimeOfDay.DAY -> Primary
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFFF8A65)
        }
    }
}

private val LocationAccent = Color(0xFF4CAF50)

// Stable discriminant so AnimatedContent only transitions between loading/success/error —
// not on every internal field change within a Success state.
private enum class WeatherStateKey { LOADING, SUCCESS, PERMISSION, APPROXIMATE, ERROR }
private fun WeatherUiState.toKey() = when (this) {
    is WeatherUiState.Loading           -> WeatherStateKey.LOADING
    is WeatherUiState.Success           -> WeatherStateKey.SUCCESS
    is WeatherUiState.PermissionRequired -> WeatherStateKey.PERMISSION
    is WeatherUiState.ApproximateLocation -> WeatherStateKey.APPROXIMATE
    is WeatherUiState.Error             -> WeatherStateKey.ERROR
}

@Composable
fun WeatherCard(
    state: WeatherUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onExpand: () -> Unit = {},
    onRequestPreciseLocation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    // Snapshot the current state so that inner composables always read the
    // latest value without triggering AnimatedContent re-targeting.
    AnimatedContent(
        targetState = state.toKey(),
        transitionSpec = { MacroMotion.contentEnter togetherWith MacroMotion.contentExit },
        label = "weatherContent",
        modifier = modifier,
    ) { stateKey ->
        // Re-read the live state inside each branch — this is safe because
        // `state` is a parameter captured by the lambda and the branch only
        // renders when the key matches.
        val currentState = state
        when (stateKey) {
            WeatherStateKey.LOADING -> {
                MacroCard(delayMs = 50) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading weather…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }

            WeatherStateKey.SUCCESS -> {
                val successState = currentState as? WeatherUiState.Success ?: return@AnimatedContent
                val weather = successState.weather
                val gradient = weatherGradient(weather.symbolCode)
                val accent = weatherAccentColor(weather.symbolCode)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Border.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(gradient),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            // Header row — title left, actions right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Left: title + location stacked, timestamp below
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Weather", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        if (weather.locationName.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                if (successState.isPrecise) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                                                contentDescription = null,
                                                tint = if (successState.isPrecise) accent else Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                weather.locationName,
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    LastUpdatedText(
                                        lastUpdatedAt = successState.lastUpdatedAt,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                                // Right: refresh + chevron
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                    }
                                    // Clickable rotating chevron
                                    val weatherChevronRot by animateFloatAsState(
                                        targetValue = if (expanded) 180f else 0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "weather_hdr_chevron",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                val wasExpanded = expanded
                                                expanded = !expanded
                                                if (!wasExpanded) { haptics.toggleOn(); onExpand() }
                                                else haptics.toggleOff()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ExpandMore,
                                            contentDescription = if (expanded) "Collapse" else "Expand",
                                            tint = Color.White.copy(alpha = if (expanded) 0.75f else 0.50f),
                                            modifier = Modifier.size(22.dp).rotate(weatherChevronRot),
                                        )
                                    }
                                }
                            }

                            // Approximate location nudge banner
                            if (!successState.isPrecise) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onRequestPreciseLocation() }
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Approximate location — tap to enable precise location",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Current weather
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = weather.icon, fontSize = 48.sp)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("${weather.temperature.toInt()}°C", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(weather.description, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Air, contentDescription = "Wind", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("${weather.windSpeed.toInt()} m/s", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }

                            // Expandable forecast
                            AnimatedVisibility(
                                visible = expanded,
                                enter = MacroMotion.expandEnter,
                                exit = MacroMotion.expandExit,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // AI Summary — first thing in expanded, right under current weather
                                    if (successState.aiSummaryLoading || successState.aiSummary != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (successState.aiSummaryLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    color = accent,
                                                    strokeWidth = 2.dp,
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Generating summary…",
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                )
                                            } else if (successState.aiSummary != null) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = successState.aiSummary,
                                                        fontSize = 13.sp,
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        lineHeight = 19.sp,
                                                    )
                                                    if (successState.aiSummaryUpdatedAt != null) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LastUpdatedText(
                                                            lastUpdatedAt = successState.aiSummaryUpdatedAt,
                                                            color = Color.White,
                                                            modifier = Modifier.align(Alignment.End),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Clothing recommendation
                                    if (!successState.aiSummaryLoading && !successState.aiClothingRecommendation.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                        ) {
                                            Text("👔", fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "What to wear",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = accent,
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = successState.aiClothingRecommendation,
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    lineHeight = 19.sp,
                                                )
                                            }
                                        }
                                    }

                                    if (weather.hourlyForecasts.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Hourly", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            weather.hourlyForecasts.forEach { hourly ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .width(56.dp)
                                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                ) {
                                                    Text(hourly.time, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(hourly.icon, fontSize = 20.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("${hourly.temperature.toInt()}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    if (weather.dailyForecasts.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Daily", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 8.dp))
                                        weather.dailyForecasts.forEach { daily ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(daily.date, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.width(44.dp))
                                                Text(daily.icon, fontSize = 20.sp)
                                                Text(daily.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                                Row {
                                                    Text("${daily.minTemp.toInt()}°", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Text(" / ", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f))
                                                    Text("${daily.maxTemp.toInt()}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }


                                    Spacer(modifier = Modifier.height(4.dp))
                                    WidgetExpandBar(
                                        expanded = true,
                                        onToggle = { expanded = false; haptics.toggleOff() },
                                        accentColor = Color.White,
                                        collapseLabel = "Show less",
                                    )
                                }
                            }

                            if (!expanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                WidgetExpandBar(
                                    expanded = false,
                                    onToggle = { expanded = true; haptics.toggleOn(); onExpand() },
                                    accentColor = Color.White,
                                    expandLabel = "Forecast & more",
                                )
                            }
                        }
                    }
                }
            }

            WeatherStateKey.PERMISSION -> {
                MacroCard(delayMs = 50) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Weather",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Allow location access to see weather",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestPermission)
                                .background(LocationAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = LocationAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Enable",
                                color = LocationAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            WeatherStateKey.APPROXIMATE -> {
                MacroCard(delayMs = 50) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Weather",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                "Using approximate location — enable precise location for accurate weather",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestPreciseLocation)
                                .background(LocationAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = LocationAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Precise",
                                color = LocationAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            WeatherStateKey.ERROR -> {
                val errorState = currentState as? WeatherUiState.Error
                MacroCard(delayMs = 50) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Weather", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = errorState?.message ?: "", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        MacroButton(text = "Retry", onClick = onRetry, variant = ButtonVariant.SECONDARY)
                    }
                }
            }
        }
    }
}
