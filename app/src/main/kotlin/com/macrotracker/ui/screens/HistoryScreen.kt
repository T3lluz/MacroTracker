package com.macrotracker.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HistoryViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val rangeDays by viewModel.rangeDays.collectAsState()
    val metric by viewModel.metric.collectAsState()
    val macroHistory by viewModel.macroHistory.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedLogs by viewModel.selectedLogs.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) { viewModel.loadData() }

    val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dates = (0 until rangeDays).map { i ->
        LocalDate.now().minusDays((rangeDays - 1 - i).toLong()).format(dateFormat)
    }

    val metricValues = dates.map { date ->
        val day = macroHistory.find { it.date == date }
        if (metric == "calories") day?.totalCalories ?: 0 else day?.totalProtein ?: 0
    }
    val maxValue = (metricValues.maxOrNull() ?: 1).coerceAtLeast(1)

    val selectedMacro = macroHistory.find { it.date == selectedDate }
    val listState = rememberLazyListState()
    val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }

    CompositionLocalProvider(LocalTickersPaused provides tickersPaused) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
    ) {
        item(key = "header") {
            Spacer(modifier = Modifier.height(48.dp))
            Text("History", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item(key = "trends") {
        // Macro Trends Card
        MacroCard(delayMs = 70) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Macro Trends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // Range filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(7, 14, 30).forEach { option ->
                    val isActive = option == rangeDays
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) Primary else Background)
                            .clickable {
                                haptics.tick()
                                viewModel.setRangeDays(option)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("${option}d", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) Color.White else TextSecondary)
                    }
                }
            }

            // Metric toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf("calories" to "Calories", "protein" to "Protein").forEach { (key, label) ->
                    val isActive = metric == key
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) Surface else Background)
                            .clickable {
                                haptics.tick()
                                viewModel.setMetric(key)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) TextPrimary else TextSecondary)
                    }
                }
            }

            // Bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dates.forEachIndexed { index, date ->
                    val value = metricValues[index]
                    val targetHeight = (14 + (value.toFloat() / maxValue) * 96).dp
                    val isSelected = date == selectedDate
                    val dayLabel = try {
                        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    } catch (_: Exception) { "?" }

                    Column(
                        modifier = Modifier.width(28.dp).clickable {
                            haptics.tick()
                            viewModel.selectDate(date)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedBar(
                            targetHeight = targetHeight,
                            color = if (isSelected) Primary else PrimaryVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            dayLabel,
                            fontSize = 11.sp,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        }

        item(key = "selected_date") {
        // Selected date detail card
        MacroCard(delayMs = 100) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val displayDate = try {
                    LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                } catch (_: Exception) { selectedDate }
                Text(displayDate, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // Stats grid
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Calories", "${selectedMacro?.totalCalories ?: 0} kcal", Modifier.weight(1f))
                StatCard("Protein", "${selectedMacro?.totalProtein ?: 0}g", Modifier.weight(1f))
            }

            // Food logs
            Text("Food Logs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
            if (selectedLogs.isEmpty()) {
                Text("No food logs for this day.", color = TextSecondary, fontStyle = FontStyle.Italic, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedLogs.forEach { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Background),
                            border = BorderStroke(1.dp, Border),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(log.foodName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${log.calories} kcal • ${log.protein}g", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (loading) {
            item(key = "loading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading history…", fontSize = 13.sp, color = TextSecondary)
            }
            }
        }
    }
    }
}

@Composable
private fun AnimatedBar(targetHeight: Dp, color: Color) {
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = MacroMotion.entranceSpring(),
        label = "barHeight",
    )
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(120.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(animatedHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(color),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Background),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

