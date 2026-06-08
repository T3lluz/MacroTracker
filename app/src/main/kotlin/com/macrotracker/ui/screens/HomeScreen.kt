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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.macrotracker.ui.components.BodyStats
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.CalendarCard
import com.macrotracker.ui.components.DraggableWidgetColumn
import com.macrotracker.ui.components.F1Card
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.MetricInfo
import com.macrotracker.ui.components.WeatherCard
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.YoutubeCard
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.HomeHealthState
import com.macrotracker.ui.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val f1State by viewModel.f1State.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val homeWidgetOrder by viewModel.homeWidgetOrder.collectAsState()
    val logsLastUpdatedAt by viewModel.logsLastUpdatedAt.collectAsState()

    var quickFood by rememberSaveable { mutableStateOf("") }
    var quickCalories by rememberSaveable { mutableStateOf("") }
    var quickProtein by rememberSaveable { mutableStateOf("") }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val defaultHomeWidgets = remember {
        listOf(
            Triple("F1", "F1 Standings", Icons.Default.Flag),
            Triple("YOUTUBE", "YouTube Feed", Icons.Default.PlayArrow),
            Triple("WEATHER", "Weather", Icons.Default.Cloud),
            Triple("CALENDAR", "Calendar", Icons.Default.CalendarMonth),
            Triple("BODY_STATS", "Body Stats", Icons.Default.MonitorHeart),
            Triple("PROGRESS", "Today's Progress", Icons.Default.PieChart),
            Triple("QUICK_ADD", "Quick Add", Icons.Default.Add)
        )
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
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
        if (granted) {
            viewModel.setMasterWeatherEnabled(true)
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.loadCalendar(granted)
        if (granted) {
            viewModel.setMasterCalendarEnabled(true)
        }
    }

    fun onRefresh(force: Boolean = false) {
        viewModel.refreshAll(
            hasLocationPermission = hasLocationPermission(),
            hasCalendarPermission = hasCalendarPermission(),
            force = force,
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

    // Recomputed on every recomposition — cheap, and always reflects the actual current time.
    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    val greeting = when (java.time.LocalTime.now().hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    val parsedConfigs by remember(homeWidgetOrder) {
        derivedStateOf { parseWidgetConfig(homeWidgetOrder, defaultHomeWidgets) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRefresh(force = true) },
        modifier = Modifier.fillMaxSize().background(Background)
    ) {
        val visibleConfigs by remember(parsedConfigs) {
            derivedStateOf { parsedConfigs.filter { it.isVisible } }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 16.dp,
                bottom = 120.dp,
            ),
        ) {
            // ── Greeting header ──────────────────────────────────────────────
            item(key = "header") {
                Spacer(modifier = Modifier.height(48.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(greeting, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
                        Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp, color = Primary)
                        }
                        IconButton(onClick = { isEditMode = !isEditMode }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Widgets", tint = Primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Edit mode ────────────────────────────────────────────────────
            if (isEditMode) {
                item(key = "editor") {
                    WidgetEditor(
                        configs = parsedConfigs,
                        onConfigsChanged = { newConfigs ->
                            viewModel.updateHomeWidgetOrder(encodeWidgetConfig(newConfigs))
                        },
                        onClose = { isEditMode = false }
                    )
                }
            } else {
                // ── Draggable widget list ────────────────────────────────────
                item(key = "widgets") {
                DraggableWidgetColumn(
                    items = visibleConfigs,
                    onReorder = { reordered ->
                        val hidden = parsedConfigs.filter { !it.isVisible }
                        viewModel.updateHomeWidgetOrder(encodeWidgetConfig(reordered + hidden))
                    },
                    itemContent = { _, config ->
                        when (config.id) {
                        "F1" -> {
                            F1Card(
                                state = f1State,
                                onRefresh = { viewModel.loadF1Data(forceRefresh = true) }
                            )
                        }
                        "YOUTUBE" -> {
                            YoutubeCard()
                        }
                        "WEATHER" -> {
                            WeatherCard(
                                state = weatherState,
                                onRequestPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                },
                                onRetry = { viewModel.loadWeather(hasLocationPermission()) },
                                onExpand = { viewModel.loadWeatherAiSummary() },
                                onRequestPreciseLocation = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                },
                            )
                        }
                        "CALENDAR" -> {
                            CalendarCard(
                                state = calendarState,
                                onRequestPermission = {
                                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            )
                        }
                        "BODY_STATS" -> {
                            val hs = healthState
                            if (hs is HomeHealthState.Success) {
                                val stats = hs.stats
                                MacroCard(delayMs = 50) {
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
                                            HealthMetricUiState(value = "%,d".format(stats.steps), isEnabled = true)
                                        ),
                                        Pair(
                                            MetricInfo("Avg HR", "bpm", Icons.Outlined.MonitorHeart, Color(0xFFEF5350)),
                                            HealthMetricUiState(
                                                value = if (stats.avgHeartRate > 0) "${stats.avgHeartRate}" else "—",
                                                isEnabled = true
                                            )
                                        ),
                                        Pair(
                                            MetricInfo("Sleep", "", Icons.Outlined.Bedtime, Color(0xFF7C4DFF)),
                                            HealthMetricUiState(value = sleepDisplay, isEnabled = true)
                                        ),
                                        Pair(
                                            MetricInfo("Total Cal", "kcal", Icons.Outlined.LocalFireDepartment, Color(0xFFFF9800)),
                                            HealthMetricUiState(
                                                value = if (stats.totalCaloriesBurned > 0) "${stats.totalCaloriesBurned.toInt()}" else "—",
                                                isEnabled = true
                                            )
                                        )
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        BodyStats(metrics = homeMetrics, isCompact = true)
                                    }
                                }
                            } else if (hs is HomeHealthState.Loading) {
                                MacroCard(delayMs = 75) {
                                    Box(modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Primary)
                                    }
                                }
                            }
                        }
                        "PROGRESS" -> {
                            val s = summary
                            if (s != null) {
                                MacroCard(delayMs = 100) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
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
                        }
                        "QUICK_ADD" -> {
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
                )
                } // end item("widgets")
            } // end else (not edit mode)
        } // end LazyColumn
    } // end PullToRefreshBox
}
