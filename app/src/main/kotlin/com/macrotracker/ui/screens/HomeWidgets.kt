package com.macrotracker.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.BodyStats
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.components.F1Card
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.MetricInfo
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.YoutubeCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HomeHealthState
import com.macrotracker.ui.viewmodel.HomeViewModel

@Composable
fun HomeWidgetItem(
    config: WidgetConfig,
    viewModel: HomeViewModel,
    onNavigateToHealth: () -> Unit,
    onNavigateToStats: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    hasLocationPermission: () -> Boolean,
    quickFood: String,
    onQuickFoodChange: (String) -> Unit,
    quickCalories: String,
    onQuickCaloriesChange: (String) -> Unit,
    quickProtein: String,
    onQuickProteinChange: (String) -> Unit,
) {
    when (config.id) {
        "F1" -> HomeF1Widget(viewModel)
        "YOUTUBE" -> YoutubeCard()
        "WEATHER" -> HomeWeatherWidget(
            viewModel = viewModel,
            onRequestPermission = onRequestLocationPermission,
            hasLocationPermission = hasLocationPermission,
        )
        "CALENDAR" -> HomeCalendarWidget(
            viewModel = viewModel,
            onRequestPermission = onRequestCalendarPermission,
        )
        "BODY_STATS" -> HomeBodyStatsWidget(viewModel)
        "PROGRESS" -> HomeProgressWidget(
            viewModel = viewModel,
            onNavigateToStats = onNavigateToStats,
        )
        "QUICK_ADD" -> HomeQuickAddWidget(
            viewModel = viewModel,
            onNavigateToHealth = onNavigateToHealth,
            quickFood = quickFood,
            onQuickFoodChange = onQuickFoodChange,
            quickCalories = quickCalories,
            onQuickCaloriesChange = onQuickCaloriesChange,
            quickProtein = quickProtein,
            onQuickProteinChange = onQuickProteinChange,
        )
    }
}

@Composable
private fun HomeF1Widget(viewModel: HomeViewModel) {
    val f1State by viewModel.f1State.collectAsState()
    val onRefresh = remember(viewModel) { { viewModel.loadF1Data(forceRefresh = true) } }
    F1Card(
        state = f1State,
        onRefresh = onRefresh,
    )
}

@Composable
private fun HomeWeatherWidget(
    viewModel: HomeViewModel,
    onRequestPermission: () -> Unit,
    hasLocationPermission: () -> Boolean,
) {
    val weatherState by viewModel.weatherState.collectAsState()
    val hasPermission = rememberUpdatedState(hasLocationPermission)
    val onRetry = remember(viewModel) { { viewModel.loadWeather(hasPermission.value()) } }
    val onExpand = remember(viewModel) { { viewModel.loadWeatherAiSummary() } }
    WeatherCard(
        state = weatherState,
        onRequestPermission = onRequestPermission,
        onRetry = onRetry,
        onExpand = onExpand,
        onRequestPreciseLocation = onRequestPermission,
    )
}

@Composable
private fun HomeCalendarWidget(
    viewModel: HomeViewModel,
    onRequestPermission: () -> Unit,
) {
    val calendarState by viewModel.calendarState.collectAsState()
    CalendarCard(
        state = calendarState,
        onRequestPermission = onRequestPermission,
    )
}

@Composable
private fun HomeBodyStatsWidget(viewModel: HomeViewModel) {
    val healthState by viewModel.healthState.collectAsState()
    when (val hs = healthState) {
        is HomeHealthState.Success -> {
            val stats = hs.stats
            MacroCard(delayMs = 50) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Body Stats",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            "via Health Connect",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LastUpdatedText(
                            lastUpdatedAt = hs.lastUpdatedAt,
                            color = TextSecondary,
                        )
                        if (hs.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                val sleepDisplay = if (stats.sleepMinutes > 0) {
                    val h = stats.sleepMinutes / 60
                    val m = stats.sleepMinutes % 60
                    "${h}h ${m}m"
                } else "—"

                val homeMetrics = listOf(
                    Pair(
                        MetricInfo("Steps", "", Icons.AutoMirrored.Outlined.DirectionsWalk, Primary),
                        HealthMetricUiState(value = "%,d".format(stats.steps), isEnabled = true),
                    ),
                    Pair(
                        MetricInfo("Avg HR", "bpm", Icons.Outlined.MonitorHeart, Color(0xFFEF5350)),
                        HealthMetricUiState(
                            value = if (stats.avgHeartRate > 0) "${stats.avgHeartRate}" else "—",
                            isEnabled = true,
                        ),
                    ),
                    Pair(
                        MetricInfo("Sleep", "", Icons.Outlined.Bedtime, Color(0xFF7C4DFF)),
                        HealthMetricUiState(value = sleepDisplay, isEnabled = true),
                    ),
                    Pair(
                        MetricInfo("Total Cal", "kcal", Icons.Outlined.LocalFireDepartment, Color(0xFFFF9800)),
                        HealthMetricUiState(
                            value = if (stats.totalCaloriesBurned > 0) "${stats.totalCaloriesBurned.toInt()}" else "—",
                            isEnabled = true,
                        ),
                    ),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    BodyStats(metrics = homeMetrics, isCompact = true)
                }
            }
        }
        is HomeHealthState.Loading -> {
            MacroCard(delayMs = 75) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        }
        HomeHealthState.Unavailable -> Unit
    }
}

@Composable
private fun HomeProgressWidget(
    viewModel: HomeViewModel,
    onNavigateToStats: () -> Unit,
) {
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val logsLastUpdatedAt by viewModel.logsLastUpdatedAt.collectAsState()
    val s = summary ?: return

    MacroCard(delayMs = 100) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Today's Progress",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LastUpdatedText(
                    lastUpdatedAt = logsLastUpdatedAt,
                    color = TextSecondary,
                )
                IconButton(onClick = onNavigateToStats, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.FitnessCenter, contentDescription = "Stats", tint = Secondary)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (s.totalCalories > s.calorieGoal) Error else Primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${s.totalCalories}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "/ ${s.calorieGoal} kcal",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${s.totalProtein}g",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "/ ${s.proteinGoal}g protein",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${logs.size}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "meals",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val calProgress = if (s.calorieGoal > 0) s.totalCalories.toFloat() / s.calorieGoal else 0f
        val protProgress = if (s.proteinGoal > 0) s.totalProtein.toFloat() / s.proteinGoal else 0f
        MacroProgressBar(
            progress = calProgress,
            label = "Calories",
            color = if (calProgress > 1f) Error else Primary,
        )
        MacroProgressBar(
            progress = protProgress,
            label = "Protein",
            color = Secondary,
        )
    }
}

@Composable
private fun HomeQuickAddWidget(
    viewModel: HomeViewModel,
    onNavigateToHealth: () -> Unit,
    quickFood: String,
    onQuickFoodChange: (String) -> Unit,
    quickCalories: String,
    onQuickCaloriesChange: (String) -> Unit,
    quickProtein: String,
    onQuickProteinChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()

    MacroCard(delayMs = 200) {
        Text(
            "Quick Add",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        MacroTextField(
            value = quickFood,
            onValueChange = onQuickFoodChange,
            placeholder = "Food name (optional)",
            trailingIcon = {
                if (quickFood.isNotEmpty()) {
                    IconButton(onClick = { onQuickFoodChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                        )
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroTextField(
                value = quickCalories,
                onValueChange = onQuickCaloriesChange,
                placeholder = "Calories",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = {
                    if (quickCalories.isNotEmpty()) {
                        IconButton(onClick = { onQuickCaloriesChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )
            MacroTextField(
                value = quickProtein,
                onValueChange = onQuickProteinChange,
                placeholder = "Protein (g)",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = {
                    if (quickProtein.isNotEmpty()) {
                        IconButton(onClick = { onQuickProteinChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroButton(
                text = "Add",
                onClick = {
                    val cal = quickCalories.toIntOrNull() ?: 0
                    val prot = quickProtein.toIntOrNull() ?: 0
                    if (cal > 0 || prot > 0) {
                        haptics.confirm()
                        viewModel.addLog(quickFood, cal, prot)
                        onQuickFoodChange("")
                        onQuickCaloriesChange("")
                        onQuickProteinChange("")
                        Toast.makeText(context, "✅ Entry added!", Toast.LENGTH_SHORT).show()
                    } else {
                        haptics.reject()
                        Toast.makeText(context, "Enter calories or protein first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            MacroButton(
                text = "📋 View All Logs",
                onClick = onNavigateToHealth,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.SECONDARY,
            )
        }
    }
}
