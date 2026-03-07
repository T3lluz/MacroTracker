package com.macrotracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HomeHealthState
import com.macrotracker.ui.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dashboardTips = listOf(
    "💡 Aim for 25-30g of protein per meal for optimal absorption.",
    "💧 Don't forget hydration — aim for at least 8 glasses of water today.",
    "🥦 Try to get 5 portions of fruits and vegetables each day.",
    "🏃 Even a 15-minute walk after meals helps with digestion.",
    "😴 Good sleep is essential — aim for 7-9 hours tonight.",
    "🎯 Consistency beats perfection. Small steps add up!",
    "🥚 Protein-rich breakfasts help control hunger throughout the day.",
    "📊 Track regularly — awareness is the first step to change.",
    "🍎 Whole foods over processed — your body will thank you.",
    "⏰ Try eating at regular times to support your metabolism.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHealth: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val calendarState by viewModel.calendarState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var quickFood by rememberSaveable { mutableStateOf("") }
    var quickCalories by rememberSaveable { mutableStateOf("") }
    var quickProtein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCalendarPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.loadWeather(granted)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.loadCalendar(granted)
    }

    fun onRefresh() {
        viewModel.refreshAll(
            hasLocationPermission = hasLocationPermission(),
            hasCalendarPermission = hasCalendarPermission(),
            hasHealthPermission = true
        )
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    val greeting = when (java.time.LocalTime.now().hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRefresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 120.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Greeting Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
                    Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Primary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Weather Widget
            WeatherCard(
                state = weatherState,
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onRetry = { viewModel.loadWeather(hasLocationPermission()) },
                onExpand = { viewModel.loadWeatherAiSummary() },
            )

            // Calendar Widget
            CalendarCard(
                state = calendarState,
                onRequestPermission = {
                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            )

            // Health Connect Body Stats
            val hs = healthState
            if (hs is HomeHealthState.Success) {
                val stats = hs.stats
                MacroCard(delayMs = 75) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        if (hs.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeStatTile(
                            icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                            value = "%,d".format(stats.steps),
                            label = "Steps",
                            iconTint = Primary,
                            modifier = Modifier.weight(1f),
                        )
                        HomeStatTile(
                            icon = Icons.Outlined.MonitorHeart,
                            value = if (stats.avgHeartRate > 0) "${stats.avgHeartRate}" else "—",
                            label = "Avg HR",
                            iconTint = Color(0xFFEF5350),
                            modifier = Modifier.weight(1f),
                        )
                        val sleepDisplay = if (stats.sleepMinutes > 0) {
                            val h = stats.sleepMinutes / 60
                            val m = stats.sleepMinutes % 60
                            "${h}h ${m}m"
                        } else "—"
                        HomeStatTile(
                            icon = Icons.Outlined.Bedtime,
                            value = sleepDisplay,
                            label = "Sleep",
                            iconTint = Color(0xFF7C4DFF),
                            modifier = Modifier.weight(1f),
                        )
                        HomeStatTile(
                            icon = Icons.Outlined.LocalFireDepartment,
                            value = if (stats.totalCaloriesBurned > 0) {
                                "${stats.totalCaloriesBurned.toInt()}"
                            } else "—",
                            label = "Total Cal",
                            iconTint = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (hs is HomeHealthState.Loading) {
                MacroCard(delayMs = 75) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            // Daily Progress Overview
            val s = summary
            if (s != null) {
                MacroCard(delayMs = 100) {
                    Text(
                        "Today's Progress",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

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

            // Quick Add Card
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
                    onValueChange = { quickFood = it },
                    placeholder = "Food name (optional)",
                    trailingIcon = {
                        if (quickFood.isNotEmpty()) {
                            IconButton(onClick = { quickFood = "" }) {
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
                        onValueChange = { quickCalories = it },
                        placeholder = "Calories",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        trailingIcon = {
                            if (quickCalories.isNotEmpty()) {
                                IconButton(onClick = { quickCalories = "" }) {
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
                        onValueChange = { quickProtein = it },
                        placeholder = "Protein (g)",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        trailingIcon = {
                            if (quickProtein.isNotEmpty()) {
                                IconButton(onClick = { quickProtein = "" }) {
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
                                quickFood = ""
                                quickCalories = ""
                                quickProtein = ""
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
    }
}

@Composable
private fun HomeStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = TextSecondary,
        )
    }
}
