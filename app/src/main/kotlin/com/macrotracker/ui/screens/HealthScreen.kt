package com.macrotracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.DraggableWidgetColumn
import com.macrotracker.ui.components.HealthConnectCard
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroLogItem
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.WidgetConfig
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.calculatePercentageChange
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DashboardViewModel
import com.macrotracker.ui.viewmodel.HealthConnectUiState
import com.macrotracker.ui.viewmodel.HealthViewModel
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

enum class HealthMetric {
    STEPS,
    HEART_RATE,
    SLEEP,
    CALORIES,
    RESTING_HEART_RATE,
    OXYGEN_SATURATION,
    RESPIRATORY_RATE,
    DISTANCE,
    FLOORS_CLIMBED,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen(
    onNavigateToCameraScan: () -> Unit,
    scannedFoodName: String? = null,
    scannedCalories: Int? = null,
    scannedProtein: Int? = null,
    healthViewModel: HealthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val summary by healthViewModel.summary.collectAsState()
    val logs by healthViewModel.logs.collectAsState()
    val weekHistory by healthViewModel.weekHistory.collectAsState()
    val healthHistory by healthViewModel.healthHistory.collectAsState()
    val healthWidgetOrder by healthViewModel.healthWidgetOrder.collectAsState()
    val healthConnectState by healthViewModel.healthConnectState.collectAsState()

    val selectedDate by healthViewModel.selectedDate.collectAsState()
    val intradayHeartRate by healthViewModel.intradayHeartRate.collectAsState()
    val detailedSleep by healthViewModel.detailedSleep.collectAsState()
    val weekStartDay by healthViewModel.weekStartDay.collectAsState()
    val weeksBack by healthViewModel.weeksBack.collectAsState()

    var selectedMetric by rememberSaveable { mutableStateOf(HealthMetric.STEPS) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    val defaultHealthWidgets = remember {
        listOf(
            Triple("BODY_STATS", "Body Stats", Icons.Default.MonitorHeart),
            Triple("HISTORY", "Weekly Trends", Icons.Default.ShowChart),
            Triple("SUMMARY", "Daily Summary", Icons.Default.ViewDay),
            Triple("ADD_ENTRY", "Add Entry", Icons.Default.Add),
            Triple("WEEK_AT_A_GLANCE", "Food Calories", Icons.Default.History),
            Triple("RECENT_LOGS", "Recent Logs", Icons.Default.List)
        )
    }
    val parsedConfigs by remember(healthWidgetOrder) {
        derivedStateOf { parseWidgetConfig(healthWidgetOrder, defaultHealthWidgets) }
    }

    // Health Connect data states from the new ViewModel
    val heartRateState by dashboardViewModel.heartRateState.collectAsState()
    val restingHeartRateState by dashboardViewModel.restingHeartRateState.collectAsState()
    val oxygenSaturationState by dashboardViewModel.oxygenSaturationState.collectAsState()
    val respiratoryRateState by dashboardViewModel.respiratoryRateState.collectAsState()
    val stepsState by dashboardViewModel.stepsState.collectAsState()
    val distanceState by dashboardViewModel.distanceState.collectAsState()
    val floorsClimbedState by dashboardViewModel.floorsClimbedState.collectAsState()
    val activeCaloriesState by dashboardViewModel.activeCaloriesState.collectAsState()

    // Health Connect permission launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        healthViewModel.loadHealthConnect(permissionsGranted = granted.containsAll(healthViewModel.healthConnectPermissions))
        dashboardViewModel.loadData() // Reload new dashboard data after permissions change
    }

    // Load data on first composition and on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                healthViewModel.loadDataOnResume()
                dashboardViewModel.loadDataThrottled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle scanned food data
    LaunchedEffect(scannedFoodName, scannedCalories, scannedProtein) {
        if (scannedFoodName != null) foodName = scannedFoodName
        if (scannedCalories != null) calories = scannedCalories.toString()
        if (scannedProtein != null) protein = scannedProtein.toString()
    }

    val todayFormatted = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Health", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
                Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = { isEditMode = !isEditMode }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Widgets", tint = Primary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (healthConnectState) {
            is HealthConnectUiState.PermissionRequired -> {
                HealthConnectCard(
                    onRequestPermission = {
                        hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            else -> {}
        }

        if (isEditMode) {
            WidgetEditor(
                configs = parsedConfigs,
                onConfigsChanged = { newConfigs ->
                    healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(newConfigs))
                },
                onClose = { isEditMode = false }
            )
        } else {
            val visibleConfigs by remember(parsedConfigs) {
                derivedStateOf { parsedConfigs.filter { it.isVisible } }
            }
            DraggableWidgetColumn(
                items = visibleConfigs,
                onReorder = { reordered ->
                    val hidden = parsedConfigs.filter { !it.isVisible }
                    healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(reordered + hidden))
                },
                itemContent = { _, config ->
                    when (config.id) {
                    "BODY_STATS" -> {
                        val isAnyStatEnabled = heartRateState.isEnabled || restingHeartRateState.isEnabled ||
                                oxygenSaturationState.isEnabled || respiratoryRateState.isEnabled ||
                                stepsState.isEnabled || distanceState.isEnabled ||
                                floorsClimbedState.isEnabled || activeCaloriesState.isEnabled

                        if (isAnyStatEnabled) {
                            MacroCard(delayMs = 0) {
                                Text(
                                    "Body Stats",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    if (heartRateState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Heart Rate",
                                            value = "${heartRateState.value} bpm",
                                            percentageChange = calculatePercentageChange(heartRateState.today, heartRateState.yesterday),
                                            icon = Icons.Outlined.FavoriteBorder,
                                            color = Color(0xFFEF5350)
                                        )
                                    }
                                    if (restingHeartRateState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Resting HR",
                                            value = "${restingHeartRateState.value} bpm",
                                            percentageChange = calculatePercentageChange(restingHeartRateState.today, restingHeartRateState.yesterday),
                                            icon = Icons.Outlined.Bedtime,
                                            color = Color(0xFFEF5350)
                                        )
                                    }
                                    if (oxygenSaturationState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "SpO2",
                                            value = "${oxygenSaturationState.value} %",
                                            percentageChange = calculatePercentageChange(oxygenSaturationState.today, oxygenSaturationState.yesterday),
                                            icon = Icons.Outlined.Bloodtype,
                                            color = Color(0xFF42A5F5)
                                        )
                                    }
                                    if (respiratoryRateState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Resp. Rate",
                                            value = "${respiratoryRateState.value} rpm",
                                            percentageChange = calculatePercentageChange(respiratoryRateState.today, respiratoryRateState.yesterday),
                                            icon = Icons.Outlined.Air,
                                            color = Color(0xFF42A5F5)
                                        )
                                    }
                                    if (stepsState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Steps",
                                            value = stepsState.value ?: "0",
                                            percentageChange = calculatePercentageChange(stepsState.today, stepsState.yesterday),
                                            icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                                            color = Primary
                                        )
                                    }
                                    if (distanceState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Distance",
                                            value = "${distanceState.value} km",
                                            percentageChange = calculatePercentageChange(distanceState.today, distanceState.yesterday),
                                            icon = Icons.Outlined.Route,
                                            color = Primary
                                        )
                                    }
                                    if (floorsClimbedState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Floors",
                                            value = floorsClimbedState.value ?: "0",
                                            percentageChange = calculatePercentageChange(floorsClimbedState.today, floorsClimbedState.yesterday),
                                            icon = Icons.Outlined.Stairs,
                                            color = Color(0xFF66BB6A)
                                        )
                                    }
                                    if (activeCaloriesState.isEnabled) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Active Cals",
                                            value = activeCaloriesState.value ?: "0",
                                            percentageChange = calculatePercentageChange(activeCaloriesState.today, activeCaloriesState.yesterday),
                                            icon = Icons.Outlined.LocalFireDepartment,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "HISTORY" -> {
                        if (healthHistory.isNotEmpty()) {
                            HealthHistoryCard(
                                healthHistory = healthHistory,
                                selectedDate = selectedDate,
                                selectedMetric = selectedMetric,
                                intradayHeartRate = intradayHeartRate,
                                detailedSleep = detailedSleep,
                                weekStartDay = weekStartDay,
                                weeksBack = weeksBack,
                                haptics = haptics,
                                isStepsEnabled = stepsState.isEnabled,
                                isHeartRateEnabled = heartRateState.isEnabled,
                                isRestingHeartRateEnabled = restingHeartRateState.isEnabled,
                                isSpo2Enabled = oxygenSaturationState.isEnabled,
                                isRespRateEnabled = respiratoryRateState.isEnabled,
                                isDistanceEnabled = distanceState.isEnabled,
                                isFloorsEnabled = floorsClimbedState.isEnabled,
                                isActiveCaloriesEnabled = activeCaloriesState.isEnabled,
                                onDateSelected = {
                                    healthViewModel.selectDate(it)
                                },
                                onMetricSelected = {
                                    selectedMetric = it
                                    haptics.tick()
                                },
                                onWeekStartDaySelected = {
                                    healthViewModel.setWeekStartDay(it)
                                    haptics.tick()
                                },
                                onPreviousWeek = { healthViewModel.previousWeek() },
                                onNextWeek = { healthViewModel.nextWeek() }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "SUMMARY" -> {
                        val s = summary
                        if (s != null) {
                            MacroCard(delayMs = 100) {
                                Text(
                                    "Daily Summary",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                val calProgress = if (s.calorieGoal > 0) s.totalCalories.toFloat() / s.calorieGoal else 0f
                                val protProgress = if (s.proteinGoal > 0) s.totalProtein.toFloat() / s.proteinGoal else 0f
                                MacroProgressBar(
                                    progress = calProgress,
                                    label = "${s.totalCalories} / ${s.calorieGoal} kcal",
                                    color = if (calProgress > 1f) Error else Primary,
                                )
                                MacroProgressBar(
                                    progress = protProgress,
                                    label = "${s.totalProtein} / ${s.proteinGoal} g protein",
                                    color = Secondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "ADD_ENTRY" -> {
                        MacroCard(delayMs = 150) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Add Entry",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                MacroButton(
                                    text = "📷 Scan Label",
                                    onClick = onNavigateToCameraScan,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .width(160.dp),
                                    variant = ButtonVariant.PRIMARY,
                                )
                            }

                            MacroTextField(
                                value = foodName,
                                onValueChange = { foodName = it },
                                placeholder = "Food Name (optional)",
                                trailingIcon = {
                                    if (foodName.isNotEmpty()) {
                                        IconButton(onClick = { foodName = "" }) {
                                            Icon(
                                                imageVector = Icons.Filled.Clear,
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
                                    value = calories,
                                    onValueChange = { calories = it },
                                    placeholder = "Calories",
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number,
                                    trailingIcon = {
                                        if (calories.isNotEmpty()) {
                                            IconButton(onClick = { calories = "" }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Clear,
                                                    contentDescription = "Clear",
                                                )
                                            }
                                        }
                                    },
                                )
                                MacroTextField(
                                    value = protein,
                                    onValueChange = { protein = it },
                                    placeholder = "Protein (g)",
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number,
                                    trailingIcon = {
                                        if (protein.isNotEmpty()) {
                                            IconButton(onClick = { protein = "" }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Clear,
                                                    contentDescription = "Clear",
                                                )
                                            }
                                        }
                                    },
                                )
                            }

                            MacroButton(
                                text = "Add Log",
                                onClick = {
                                    val cal = calories.toIntOrNull() ?: 0
                                    val prot = protein.toIntOrNull() ?: 0
                                    if (cal > 0 || prot > 0) {
                                        haptics.confirm()
                                        healthViewModel.addLog(foodName, cal, prot)
                                        foodName = ""
                                        calories = ""
                                        protein = ""
                                        Toast.makeText(context, "✅ Entry added!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        haptics.reject()
                                        Toast.makeText(context, "Enter calories or protein first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    "WEEK_AT_A_GLANCE" -> {
                        if (weekHistory.isNotEmpty()) {
                            MacroCard(delayMs = 200) {
                                Text(
                                    "Food Calories",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )

                                val maxCal = weekHistory.maxOf { it.totalCalories }.coerceAtLeast(1)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp), // Increased height to prevent clipping
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    weekHistory.forEach { day ->
                                        val heightFraction = day.totalCalories.toFloat() / maxCal
                                        val targetHeight = (10 + heightFraction * 110).dp
                                        val isToday = day.date == LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                        val dayLabel = try {
                                            LocalDate.parse(day.date).dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                                        } catch (_: Exception) { "?" }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            if (day.totalCalories > 0) {
                                                Text(
                                                    text = day.totalCalories.toString(),
                                                    fontSize = 10.sp,
                                                    color = if (isToday) TextPrimary else TextSecondary,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }
                                            val animatedHeight by animateDpAsState(
                                                targetValue = targetHeight,
                                                animationSpec = MacroMotion.entranceSpring(),
                                                label = "barHeight",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .height(animatedHeight)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isToday) Primary else PrimaryVariant),
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                dayLabel,
                                                fontSize = 11.sp,
                                                color = if (isToday) TextPrimary else TextSecondary,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "RECENT_LOGS" -> {
                        MacroCard(delayMs = 250) {
                            Text(
                                "Recent Logs",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )

                            if (logs.isEmpty()) {
                                Text(
                                    "No logs yet today.",
                                    color = TextSecondary,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .fillMaxWidth(),
                                )
                            } else {
                                logs.reversed().forEachIndexed { index, log ->
                                    MacroLogItem(log = log, onDelete = { healthViewModel.deleteLog(it) }, index = index)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            },
        )
        }
    }
}

// ── New Stat Card Composables ──────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    metricName: String,
    value: String,
    percentageChange: Double?,
    icon: ImageVector,
    color: Color,
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = metricName,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = metricName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (percentageChange != null) {
                    PercentageChange(percentageChange)
                }
            }
        }
    }
}

@Composable
private fun PercentageChange(percentage: Double) {
    val isPositive = percentage >= 0
    val color = if (isPositive) Success else Error
    val icon = if (isPositive) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
    val formatter = DecimalFormat("0.0'%'")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPositive) "Increase" else "Decrease",
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatter.format(abs(percentage)),
            fontSize = 11.sp,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}


// ── Health History Graph ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthHistoryCard(
    healthHistory: List<DailyHealthStats>,
    selectedDate: LocalDate,
    selectedMetric: HealthMetric,
    intradayHeartRate: List<HeartRateRecord.Sample>,
    detailedSleep: List<SleepSessionRecord>,
    weekStartDay: DayOfWeek,
    weeksBack: Int,
    haptics: HapticHelper,
    isStepsEnabled: Boolean,
    isHeartRateEnabled: Boolean,
    isRestingHeartRateEnabled: Boolean,
    isSpo2Enabled: Boolean,
    isRespRateEnabled: Boolean,
    isDistanceEnabled: Boolean,
    isFloorsEnabled: Boolean,
    isActiveCaloriesEnabled: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onMetricSelected: (HealthMetric) -> Unit,
    onWeekStartDaySelected: (DayOfWeek) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    MacroCard(delayMs = 75) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousWeek, enabled = weeksBack < 2, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week", tint = if (weeksBack < 2) Primary else Border)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (weeksBack == 0) "Weekly Trends" else if (weeksBack == 1) "Last Week" else "2 Weeks Ago",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onNextWeek, enabled = weeksBack > 0, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week", tint = if (weeksBack > 0) Primary else Border)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Starts:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Background)
                            .clickable {
                                onWeekStartDaySelected(
                                    if (weekStartDay == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (weekStartDay == DayOfWeek.MONDAY) "Mon" else "Sun",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metric Selector
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isStepsEnabled) {
                    MetricFilterChip(
                        text = "Steps",
                        selected = selectedMetric == HealthMetric.STEPS,
                        onClick = { onMetricSelected(HealthMetric.STEPS) }
                    )
                }
                if (isHeartRateEnabled) {
                    MetricFilterChip(
                        text = "HR",
                        selected = selectedMetric == HealthMetric.HEART_RATE,
                        onClick = { onMetricSelected(HealthMetric.HEART_RATE) }
                    )
                }
                // Always show Sleep
                MetricFilterChip(
                    text = "Sleep",
                    selected = selectedMetric == HealthMetric.SLEEP,
                    onClick = { onMetricSelected(HealthMetric.SLEEP) }
                )
                if (isActiveCaloriesEnabled) {
                    MetricFilterChip(
                        text = "Cals",
                        selected = selectedMetric == HealthMetric.CALORIES,
                        onClick = { onMetricSelected(HealthMetric.CALORIES) }
                    )
                }
                if (isRestingHeartRateEnabled) {
                    MetricFilterChip(
                        text = "Resting HR",
                        selected = selectedMetric == HealthMetric.RESTING_HEART_RATE,
                        onClick = { onMetricSelected(HealthMetric.RESTING_HEART_RATE) }
                    )
                }
                if (isSpo2Enabled) {
                    MetricFilterChip(
                        text = "SpO2",
                        selected = selectedMetric == HealthMetric.OXYGEN_SATURATION,
                        onClick = { onMetricSelected(HealthMetric.OXYGEN_SATURATION) }
                    )
                }
                if (isRespRateEnabled) {
                    MetricFilterChip(
                        text = "Resp. Rate",
                        selected = selectedMetric == HealthMetric.RESPIRATORY_RATE,
                        onClick = { onMetricSelected(HealthMetric.RESPIRATORY_RATE) }
                    )
                }
                if (isDistanceEnabled) {
                    MetricFilterChip(
                        text = "Distance",
                        selected = selectedMetric == HealthMetric.DISTANCE,
                        onClick = { onMetricSelected(HealthMetric.DISTANCE) }
                    )
                }
                if (isFloorsEnabled) {
                    MetricFilterChip(
                        text = "Floors",
                        selected = selectedMetric == HealthMetric.FLOORS_CLIMBED,
                        onClick = { onMetricSelected(HealthMetric.FLOORS_CLIMBED) }
                    )
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            val color = when (selectedMetric) {
                HealthMetric.STEPS -> Primary
                HealthMetric.HEART_RATE -> Color(0xFFEF5350)
                HealthMetric.SLEEP -> Color(0xFF7C4DFF)
                HealthMetric.CALORIES -> Color(0xFFFF9800)
                HealthMetric.RESTING_HEART_RATE -> Color(0xFFEF5350)
                HealthMetric.OXYGEN_SATURATION -> Color(0xFF42A5F5)
                HealthMetric.RESPIRATORY_RATE -> Color(0xFF42A5F5)
                HealthMetric.DISTANCE -> Primary
                HealthMetric.FLOORS_CLIMBED -> Color(0xFF66BB6A)
            }

            // Calculate Weekly Averages/Totals for enrichment
            val validStats = healthHistory.filter {
                when (selectedMetric) {
                    HealthMetric.STEPS -> it.stats.steps > 0
                    HealthMetric.HEART_RATE -> it.stats.avgHeartRate > 0
                    HealthMetric.SLEEP -> it.stats.sleepMinutes > 0
                    HealthMetric.CALORIES -> it.stats.totalCaloriesBurned > 0
                    HealthMetric.RESTING_HEART_RATE -> it.stats.restingHeartRate > 0
                    HealthMetric.OXYGEN_SATURATION -> it.stats.oxygenSaturation > 0
                    HealthMetric.RESPIRATORY_RATE -> it.stats.respiratoryRate > 0
                    HealthMetric.DISTANCE -> it.stats.distance > 0
                    HealthMetric.FLOORS_CLIMBED -> it.stats.floorsClimbed > 0
                }
            }

            val avgValue = if (validStats.isNotEmpty()) {
                validStats.sumOf {
                    when (selectedMetric) {
                        HealthMetric.STEPS -> it.stats.steps.toDouble()
                        HealthMetric.HEART_RATE -> it.stats.avgHeartRate.toDouble()
                        HealthMetric.SLEEP -> (it.stats.sleepMinutes / 60.0)
                        HealthMetric.CALORIES -> it.stats.totalCaloriesBurned
                        HealthMetric.RESTING_HEART_RATE -> it.stats.restingHeartRate.toDouble()
                        HealthMetric.OXYGEN_SATURATION -> it.stats.oxygenSaturation
                        HealthMetric.RESPIRATORY_RATE -> it.stats.respiratoryRate.toDouble()
                        HealthMetric.DISTANCE -> it.stats.distance
                        HealthMetric.FLOORS_CLIMBED -> it.stats.floorsClimbed
                    }
                } / validStats.size
            } else 0.0

            val summaryText = when (selectedMetric) {
                HealthMetric.STEPS -> "Avg: ${String.format(Locale.US, "%,d", avgValue.toInt())} steps"
                HealthMetric.HEART_RATE -> "Avg: ${avgValue.toInt()} bpm"
                HealthMetric.SLEEP -> {
                    val h = avgValue.toInt()
                    val m = ((avgValue - h) * 60).toInt()
                    "Avg: ${h}h ${m}m"
                }
                HealthMetric.CALORIES -> "Avg: ${String.format(Locale.US, "%,d", avgValue.toInt())} kcal"
                HealthMetric.RESTING_HEART_RATE -> "Avg: ${avgValue.toInt()} bpm"
                HealthMetric.OXYGEN_SATURATION -> "Avg: ${String.format(Locale.US, "%.1f", avgValue)}%"
                HealthMetric.RESPIRATORY_RATE -> "Avg: ${avgValue.toInt()} rpm"
                HealthMetric.DISTANCE -> "Avg: ${String.format(Locale.US, "%.2f", avgValue)} km"
                HealthMetric.FLOORS_CLIMBED -> "Avg: ${String.format(Locale.US, "%.1f", avgValue)} floors"
            }

            // Display Summary Text Above Graph
            if (avgValue > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Average",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = summaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Graph
            val maxValue = healthHistory.maxOf {
                when (selectedMetric) {
                    HealthMetric.STEPS -> it.stats.steps.toDouble()
                    HealthMetric.HEART_RATE -> it.stats.avgHeartRate.toDouble()
                    HealthMetric.SLEEP -> (it.stats.sleepMinutes / 60.0) // hours
                    HealthMetric.CALORIES -> it.stats.totalCaloriesBurned
                    HealthMetric.RESTING_HEART_RATE -> it.stats.restingHeartRate.toDouble()
                    HealthMetric.OXYGEN_SATURATION -> it.stats.oxygenSaturation
                    HealthMetric.RESPIRATORY_RATE -> it.stats.respiratoryRate.toDouble()
                    HealthMetric.DISTANCE -> it.stats.distance
                    HealthMetric.FLOORS_CLIMBED -> it.stats.floorsClimbed
                }
            }.coerceAtLeast(1.0)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Background),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Background Average Line Indicator
                if (avgValue > 0 && maxValue > 0) {
                    val heightFraction = (avgValue / maxValue).toFloat()
                    val bottomContentHeight = 22.dp // Space for "Mon", "Tue" text + spacers below the bar
                    val avgBarHeight = 10.dp + (heightFraction * 120).dp
                    val lineBottomOffset = bottomContentHeight + avgBarHeight

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lineY = size.height - lineBottomOffset.toPx()
                        drawLine(
                            color = color.copy(alpha = 0.5f),
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(healthHistory) {
                            var lastIndex = -1
                            detectDragGestures(
                                onDragStart = { _ -> },
                                onDrag = { change, _ ->
                                    if (healthHistory.isNotEmpty()) {
                                        val clampedX = change.position.x.coerceIn(0f, size.width.toFloat())
                                        val widthPerItem = size.width / healthHistory.size.toFloat()
                                        val index = (clampedX / widthPerItem)
                                            .toInt()
                                            .coerceIn(0, healthHistory.size - 1)
                                        if (index != lastIndex) {
                                            haptics.tick()
                                            onDateSelected(healthHistory[index].date)
                                            lastIndex = index
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(healthHistory) {
                            detectTapGestures(
                                onPress = { touch ->
                                    if (healthHistory.isNotEmpty()) {
                                        val widthPerItem = size.width / healthHistory.size.toFloat()
                                        val index = (touch.x / widthPerItem)
                                            .toInt()
                                            .coerceIn(0, healthHistory.size - 1)
                                        haptics.tick()
                                        onDateSelected(healthHistory[index].date)
                                    }
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    healthHistory.forEach { day ->
                        val value = when (selectedMetric) {
                            HealthMetric.STEPS -> day.stats.steps.toDouble()
                            HealthMetric.HEART_RATE -> day.stats.avgHeartRate.toDouble()
                            HealthMetric.SLEEP -> (day.stats.sleepMinutes / 60.0)
                            HealthMetric.CALORIES -> day.stats.totalCaloriesBurned
                            HealthMetric.RESTING_HEART_RATE -> day.stats.restingHeartRate.toDouble()
                            HealthMetric.OXYGEN_SATURATION -> day.stats.oxygenSaturation
                            HealthMetric.RESPIRATORY_RATE -> day.stats.respiratoryRate.toDouble()
                            HealthMetric.DISTANCE -> day.stats.distance
                            HealthMetric.FLOORS_CLIMBED -> day.stats.floorsClimbed
                        }
                        val heightFraction = (value / maxValue).toFloat()
                        val targetHeight = (10 + heightFraction * 120).dp // Adjusted to scale better with 180dp row
                        val isSelected = day.date == selectedDate
                        val dayLabel = try {
                            day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                        } catch (_: Exception) {
                            "?"
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp),
                        ) {
                            // Display value on top of bar, prominently if selected
                            val displayValue = when (selectedMetric) {
                                HealthMetric.STEPS -> if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000.0) else value
                                    .toInt()
                                    .toString()
                                HealthMetric.HEART_RATE -> value
                                    .toInt()
                                    .toString()
                                HealthMetric.SLEEP -> String.format(Locale.US, "%.1fh", value)
                                HealthMetric.CALORIES -> value
                                    .toInt()
                                    .toString()
                                HealthMetric.RESTING_HEART_RATE -> value
                                    .toInt()
                                    .toString()
                                HealthMetric.OXYGEN_SATURATION -> String.format(Locale.US, "%.1f", value)
                                HealthMetric.RESPIRATORY_RATE -> value.toInt().toString()
                                HealthMetric.DISTANCE -> String.format(Locale.US, "%.2f", value)
                                HealthMetric.FLOORS_CLIMBED -> String.format(Locale.US, "%.1f", value)
                            }

                            if (value > 0 || isSelected) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (value > 0) displayValue else "—",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (value > 0) displayValue else "—",
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }

                            val animatedHeight by animateDpAsState(
                                targetValue = targetHeight,
                                animationSpec = MacroMotion.entranceSpring(),
                                label = "barHeight",
                            )
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 28.dp else 24.dp)
                                    .height(animatedHeight)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) color else color.copy(alpha = 0.3f)),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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

            // Selected Day Summary Panel
            val selectedDayStats = healthHistory.find { it.date == selectedDate }
            if (selectedDayStats != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                val dayName = if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                
                val selectedDayValue = when (selectedMetric) {
                    HealthMetric.STEPS -> selectedDayStats.stats.steps.toDouble()
                    HealthMetric.HEART_RATE -> selectedDayStats.stats.avgHeartRate.toDouble()
                    HealthMetric.SLEEP -> (selectedDayStats.stats.sleepMinutes / 60.0)
                    HealthMetric.CALORIES -> selectedDayStats.stats.totalCaloriesBurned
                    HealthMetric.RESTING_HEART_RATE -> selectedDayStats.stats.restingHeartRate.toDouble()
                    HealthMetric.OXYGEN_SATURATION -> selectedDayStats.stats.oxygenSaturation
                    HealthMetric.RESPIRATORY_RATE -> selectedDayStats.stats.respiratoryRate.toDouble()
                    HealthMetric.DISTANCE -> selectedDayStats.stats.distance
                    HealthMetric.FLOORS_CLIMBED -> selectedDayStats.stats.floorsClimbed
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    if (avgValue > 0 && selectedDayValue > 0) {
                        val diff = ((selectedDayValue - avgValue) / avgValue * 100)
                        val isPositive = diff >= 0
                        val diffColor = if (isPositive) Success else Error
                        val diffIcon = if (isPositive) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(diffIcon, contentDescription = null, tint = diffColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.1f", kotlin.math.abs(diff))}% vs avg",
                                fontSize = 12.sp,
                                color = diffColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isStepsEnabled && selectedDayStats.stats.steps > 0) {
                        SelectedDayStatChip(icon = Icons.AutoMirrored.Outlined.DirectionsWalk, value = "${selectedDayStats.stats.steps}", label = "Steps", tint = Primary)
                    }
                    if (isHeartRateEnabled && selectedDayStats.stats.avgHeartRate > 0) {
                        SelectedDayStatChip(icon = Icons.Outlined.FavoriteBorder, value = "${selectedDayStats.stats.avgHeartRate}", label = "Avg HR", tint = Color(0xFFEF5350))
                    }
                    if (selectedDayStats.stats.sleepMinutes > 0) {
                        val h = selectedDayStats.stats.sleepMinutes / 60
                        val m = selectedDayStats.stats.sleepMinutes % 60
                        SelectedDayStatChip(icon = Icons.Outlined.Bedtime, value = "${h}h ${m}m", label = "Sleep", tint = Color(0xFF7C4DFF))
                    }
                    if (isActiveCaloriesEnabled && selectedDayStats.stats.totalCaloriesBurned > 0) {
                        SelectedDayStatChip(icon = Icons.Outlined.LocalFireDepartment, value = "${selectedDayStats.stats.totalCaloriesBurned.toInt()}", label = "Active Cals", tint = Color(0xFFFF9800))
                    }
                    if (isDistanceEnabled && selectedDayStats.stats.distance > 0) {
                        SelectedDayStatChip(icon = Icons.Outlined.Route, value = String.format(Locale.US, "%.1f km", selectedDayStats.stats.distance), label = "Distance", tint = Primary)
                    }
                    if (isFloorsEnabled && selectedDayStats.stats.floorsClimbed > 0) {
                         SelectedDayStatChip(icon = Icons.Outlined.Stairs, value = String.format(Locale.US, "%.1f", selectedDayStats.stats.floorsClimbed), label = "Floors", tint = Color(0xFF66BB6A))
                    }
                    if (isRestingHeartRateEnabled && selectedDayStats.stats.restingHeartRate > 0) {
                         SelectedDayStatChip(icon = Icons.Outlined.FavoriteBorder, value = "${selectedDayStats.stats.restingHeartRate}", label = "Resting HR", tint = Color(0xFFEF5350))
                    }
                    if (isSpo2Enabled && selectedDayStats.stats.oxygenSaturation > 0) {
                         SelectedDayStatChip(icon = Icons.Outlined.Bloodtype, value = String.format(Locale.US, "%.1f%%", selectedDayStats.stats.oxygenSaturation), label = "SpO2", tint = Color(0xFF42A5F5))
                    }
                    if (isRespRateEnabled && selectedDayStats.stats.respiratoryRate > 0) {
                         SelectedDayStatChip(icon = Icons.Outlined.Air, value = "${selectedDayStats.stats.respiratoryRate}", label = "Resp Rate", tint = Color(0xFF42A5F5))
                    }
                }
            }

            // ── Inline detail graph — expands smoothly within the card ──────────
            AnimatedVisibility(
                visible = selectedMetric == HealthMetric.HEART_RATE || selectedMetric == HealthMetric.SLEEP,
                enter = MacroMotion.expandEnter,
                exit = MacroMotion.expandExit,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Border.copy(alpha = 0.35f))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (selectedMetric == HealthMetric.HEART_RATE) {
                        HeartRateDetailContent(intradayHeartRate, selectedDate, haptics)
                    } else if (selectedMetric == HealthMetric.SLEEP) {
                        SleepDetailContent(detailedSleep, selectedDate, haptics)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateDetailContent(samples: List<HeartRateRecord.Sample>, date: LocalDate, haptics: HapticHelper) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val dateStr = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("MMM d"))
            Text(
                "Heart Rate ($dateStr)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (samples.isEmpty()) {
                Text("No detailed heart rate data available.", color = TextSecondary, fontSize = 14.sp)
            } else {
                var touchX by remember { mutableStateOf<Float?>(null) }
                val textMeasurer = rememberTextMeasurer()

                Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Background)
                    .pointerInput(samples) {
                        var lastClosestIndex = -1
                        val width = size.width.toFloat()

                        detectDragGestures(
                            onDragStart = { touchX = it.x.coerceIn(0f, size.width.toFloat()) },
                            onDrag = { change, _ ->
                                val clampedX = change.position.x.coerceIn(0f, size.width.toFloat())
                                touchX = clampedX
                                val closestIndex = samples
                                    .withIndex()
                                    .minByOrNull {
                                        val zdt = it.value.time.atZone(java.time.ZoneId.systemDefault())
                                        val hourFraction = zdt.hour + zdt.minute / 60f + zdt.second / 3600f
                                        val px = (hourFraction / 24f) * width
                                        kotlin.math.abs(px - clampedX)
                                    }?.index ?: -1

                                if (closestIndex != -1 && closestIndex != lastClosestIndex) {
                                    haptics.tick()
                                    lastClosestIndex = closestIndex
                                }
                            },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null }
                        )
                    }
                    .pointerInput(samples) {
                        detectTapGestures(
                            onPress = {
                                touchX = it.x.coerceIn(0f, size.width.toFloat())
                                haptics.tick()
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
                ) {
                    val width = size.width
                    val height = size.height
                    val minHr = 40f
                    val maxHr = maxOf(180f, samples.maxOf { it.beatsPerMinute }.toFloat())
                    val hrRange = maxHr - minHr

                    val path = androidx.compose.ui.graphics.Path()
                    val points = mutableListOf<Pair<Offset, HeartRateRecord.Sample>>()

                    // Top margin to ensure tooltip doesn't draw out of bounds easily
                    val topMargin = 40.dp.toPx()
                    val graphHeight = height - topMargin

                    samples.forEachIndexed { index, sample ->
                        val zdt = sample.time.atZone(java.time.ZoneId.systemDefault())
                        val hourFraction = zdt.hour + zdt.minute / 60f + zdt.second / 3600f
                        val x = (hourFraction / 24f) * width
                        val y = topMargin + graphHeight - ((sample.beatsPerMinute - minHr) / hrRange) * graphHeight

                        val point = Offset(x, y)
                        points.add(point to sample)

                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Draw the heart rate continuous line
                    drawPath(
                        path = path,
                        color = Color(0xFFEF5350),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )

                    // Draw interactive data overlay if currently touching/dragging
                    if (touchX != null && points.isNotEmpty()) {
                        val closest = points.minByOrNull { kotlin.math.abs(it.first.x - touchX!!) }
                        if (closest != null) {
                            val (point, sample) = closest

                            // Draw vertical indicator line
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(point.x, topMargin),
                                end = Offset(point.x, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            // Highlight the data point
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = point
                            )
                            drawCircle(
                                color = Color(0xFFEF5350),
                                radius = 3.dp.toPx(),
                                center = point
                            )

                            // Draw Tooltip showing exact time and bpm
                            val zdt = sample.time.atZone(java.time.ZoneId.systemDefault())
                            val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                            val text = "${sample.beatsPerMinute} bpm\n${zdt.format(timeFmt)}"

                            val textLayout = textMeasurer.measure(
                                text = text,
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )

                            val tooltipWidth = textLayout.size.width + 16.dp.toPx()
                            val tooltipHeight = textLayout.size.height + 8.dp.toPx()

                            // Constrain tooltip horizontally to not clip off screen
                            var tooltipX = point.x - tooltipWidth / 2f
                            if (tooltipX < 0) tooltipX = 0f
                            if (tooltipX + tooltipWidth > width) tooltipX = width - tooltipWidth

                            // Draw background box for tooltip
                            drawRoundRect(
                                color = Color(0xFF333333),
                                topLeft = Offset(tooltipX, 0f),
                                size = Size(tooltipWidth, tooltipHeight),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )

                            // Draw tooltip text
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(tooltipX + 8.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 AM", fontSize = 10.sp, color = TextSecondary)
                    Text("12 PM", fontSize = 10.sp, color = TextSecondary)
                    Text("11:59 PM", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
}

@Composable
private fun SleepDetailContent(sessions: List<SleepSessionRecord>, date: LocalDate, haptics: HapticHelper) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val dateStr = if (date == LocalDate.now()) "Last Night" else date.format(DateTimeFormatter.ofPattern("MMM d"))
            Text(
                "Sleep Stages ($dateStr)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (sessions.isEmpty() || sessions.all { it.stages.isEmpty() }) {
                Text("No detailed sleep stages available.", color = TextSecondary, fontSize = 14.sp)
            } else {
                val stages = sessions.flatMap { it.stages }.sortedBy { it.startTime }
                var touchX by remember { mutableStateOf<Float?>(null) }
                val textMeasurer = rememberTextMeasurer()

                // Detailed Hypnogram for Sleep Stages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .width(50.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Awake",
                            fontSize = 10.sp,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text("REM", fontSize = 10.sp, color = Color(0xFF03A9F4), fontWeight = FontWeight.Bold)
                        Text("Light", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        Text(
                            "Deep",
                            fontSize = 10.sp,
                            color = Color(0xFF3F51B5),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val minTime = stages.first().startTime.toEpochMilli()
                    val maxTime = stages.last().endTime.toEpochMilli()
                    val timeRange = maxTime - minTime

                    // Hypnogram Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Background)
                    ) {
                        Canvas(modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(stages) {
                                var lastStage: SleepSessionRecord.Stage? = null
                                detectDragGestures(
                                    onDragStart = { touchX = it.x.coerceIn(0f, size.width.toFloat()) },
                                    onDrag = { change, _ ->
                                        val clampedX = change.position.x.coerceIn(0f, size.width.toFloat())
                                        touchX = clampedX
                                        if (timeRange > 0) {
                                            val fraction = (clampedX / size.width.toFloat()).coerceIn(0f, 1f)
                                            val touchTime = minTime + (fraction * timeRange).toLong()
                                            val stage = stages.find { touchTime in it.startTime.toEpochMilli()..it.endTime.toEpochMilli() }
                                            if (stage != null && stage != lastStage) {
                                                haptics.tick()
                                                lastStage = stage
                                            }
                                        }
                                    },
                                    onDragEnd = { touchX = null },
                                    onDragCancel = { touchX = null }
                                )
                            }
                            .pointerInput(stages) {
                                detectTapGestures(
                                    onPress = {
                                        touchX = it.x.coerceIn(0f, size.width.toFloat())
                                        haptics.tick()
                                        tryAwaitRelease()
                                        touchX = null
                                    }
                                )
                            }
                        ) {
                            val width = size.width
                            val height = size.height

                            val topMargin = 40.dp.toPx()
                            val drawHeight = height - topMargin

                            val awakeY = topMargin + drawHeight * 0.1f
                            val remY = topMargin + drawHeight * 0.36f
                            val lightY = topMargin + drawHeight * 0.63f
                            val deepY = topMargin + drawHeight * 0.9f

                            // Background dotted grid lines for readability
                            val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            drawLine(
                                Color.Gray.copy(alpha = 0.2f),
                                androidx.compose.ui.geometry.Offset(0f, awakeY),
                                androidx.compose.ui.geometry.Offset(width, awakeY),
                                pathEffect = pathEffect
                            )
                            drawLine(
                                Color.Gray.copy(alpha = 0.2f),
                                androidx.compose.ui.geometry.Offset(0f, remY),
                                androidx.compose.ui.geometry.Offset(width, remY),
                                pathEffect = pathEffect
                            )
                            drawLine(
                                Color.Gray.copy(alpha = 0.2f),
                                androidx.compose.ui.geometry.Offset(0f, lightY),
                                androidx.compose.ui.geometry.Offset(width, lightY),
                                pathEffect = pathEffect
                            )
                            drawLine(
                                Color.Gray.copy(alpha = 0.2f),
                                androidx.compose.ui.geometry.Offset(0f, deepY),
                                androidx.compose.ui.geometry.Offset(width, deepY),
                                pathEffect = pathEffect
                            )

                            if (timeRange > 0) {
                                stages.forEachIndexed { index, stage ->
                                    val startX = ((stage.startTime.toEpochMilli() - minTime).toFloat() / timeRange) * width
                                    val endX = ((stage.endTime.toEpochMilli() - minTime).toFloat() / timeRange) * width

                                    val (y, color) = when (stage.stage) {
                                        SleepSessionRecord.STAGE_TYPE_AWAKE -> awakeY to Color(0xFFFF9800)
                                        SleepSessionRecord.STAGE_TYPE_REM -> remY to Color(0xFF03A9F4)
                                        SleepSessionRecord.STAGE_TYPE_LIGHT -> lightY to Color(0xFF4CAF50)
                                        SleepSessionRecord.STAGE_TYPE_DEEP -> deepY to Color(0xFF3F51B5)
                                        else -> lightY to Color.Gray
                                    }

                                    // horizontal line showing time spent in stage
                                    drawLine(
                                        color = color,
                                        start = androidx.compose.ui.geometry.Offset(startX, y),
                                        end = androidx.compose.ui.geometry.Offset(endX, y),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )

                                    // vertical connecting line to previous stage
                                    if (index > 0) {
                                        val prevStage = stages[index - 1]
                                        val prevY = when (prevStage.stage) {
                                            SleepSessionRecord.STAGE_TYPE_AWAKE -> awakeY
                                            SleepSessionRecord.STAGE_TYPE_REM -> remY
                                            SleepSessionRecord.STAGE_TYPE_LIGHT -> lightY
                                            SleepSessionRecord.STAGE_TYPE_DEEP -> deepY
                                            else -> lightY
                                        }
                                        drawLine(
                                            color = Color.Gray.copy(alpha = 0.4f),
                                            start = androidx.compose.ui.geometry.Offset(startX, prevY),
                                            end = androidx.compose.ui.geometry.Offset(startX, y),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }

                                // Interactive Tooltip & Highlight
                                if (touchX != null) {
                                    val fraction = (touchX!! / width).coerceIn(0f, 1f)
                                    val touchTime = minTime + (fraction * timeRange).toLong()
                                    val activeStage = stages.find { touchTime in it.startTime.toEpochMilli()..it.endTime.toEpochMilli() }

                                    if (activeStage != null) {
                                        val stageName = when (activeStage.stage) {
                                            SleepSessionRecord.STAGE_TYPE_AWAKE -> "Awake"
                                            SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                                            SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light"
                                            SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
                                            else -> "Unknown"
                                        }

                                        val activeY = when (activeStage.stage) {
                                            SleepSessionRecord.STAGE_TYPE_AWAKE -> awakeY
                                            SleepSessionRecord.STAGE_TYPE_REM -> remY
                                            SleepSessionRecord.STAGE_TYPE_LIGHT -> lightY
                                            SleepSessionRecord.STAGE_TYPE_DEEP -> deepY
                                            else -> lightY
                                        }

                                        drawLine(
                                            color = Color.Gray.copy(alpha = 0.5f),
                                            start = Offset(touchX!!, topMargin),
                                            end = Offset(touchX!!, height),
                                            strokeWidth = 1.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                        )

                                        drawCircle(
                                            color = Color.White,
                                            radius = 4.dp.toPx(),
                                            center = Offset(touchX!!, activeY)
                                        )

                                        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                                        val startStr = Instant
                                            .ofEpochMilli(activeStage.startTime.toEpochMilli())
                                            .atZone(ZoneId.systemDefault())
                                            .format(timeFmt)
                                        val endStr = Instant
                                            .ofEpochMilli(activeStage.endTime.toEpochMilli())
                                            .atZone(ZoneId.systemDefault())
                                            .format(timeFmt)
                                        val durationMins = Duration.between(activeStage.startTime, activeStage.endTime).toMinutes()

                                        val text = "$stageName: ${durationMins}m\n$startStr - $endStr"

                                        val textLayout = textMeasurer.measure(
                                            text = text,
                                            style = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        )

                                        val tooltipWidth = textLayout.size.width + 16.dp.toPx()
                                        val tooltipHeight = textLayout.size.height + 8.dp.toPx()

                                        var tooltipX = touchX!! - tooltipWidth / 2f
                                        if (tooltipX < 0) tooltipX = 0f
                                        if (tooltipX + tooltipWidth > width) tooltipX = width - tooltipWidth

                                        drawRoundRect(
                                            color = Color(0xFF333333),
                                            topLeft = Offset(tooltipX, 0f),
                                            size = Size(tooltipWidth, tooltipHeight),
                                            cornerRadius = CornerRadius(6.dp.toPx())
                                        )

                                        drawText(
                                            textLayoutResult = textLayout,
                                            topLeft = Offset(tooltipX + 8.dp.toPx(), 4.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Start/End Time bounds for hypnogram
                val minTime = stages.firstOrNull()?.startTime?.toEpochMilli() ?: 0L
                val maxTime = stages.lastOrNull()?.endTime?.toEpochMilli() ?: 0L
                if (minTime < maxTime) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 58.dp, top = 8.dp), // Pushed to right past the 50dp Y-Axis labels
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val startZdt = java.time.Instant
                            .ofEpochMilli(minTime)
                            .atZone(java.time.ZoneId.systemDefault())
                        val endZdt = java.time.Instant
                            .ofEpochMilli(maxTime)
                            .atZone(java.time.ZoneId.systemDefault())
                        val fmt = DateTimeFormatter.ofPattern("h:mm a")
                        Text(startZdt.format(fmt), fontSize = 10.sp, color = TextSecondary)
                        Text(endZdt.format(fmt), fontSize = 10.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val deepTime = stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                val lightTime = stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                val remTime = stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                val awakeTime = stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }

                SleepStageRow("Awake", awakeTime, Color(0xFFFF9800))
                SleepStageRow("REM", remTime, Color(0xFF03A9F4))
                SleepStageRow("Light", lightTime, Color(0xFF4CAF50))
                SleepStageRow("Deep", deepTime, Color(0xFF3F51B5))
            }
        }
}

@Composable
private fun SleepStageRow(label: String, minutes: Long, color: Color) {
    if (minutes <= 0) return
    val h = minutes / 60
    val m = minutes % 60
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (h > 0) "${h}h ${m}m" else "${m}m", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Primary else Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SelectedDayStatChip(icon: ImageVector, value: String, label: String, tint: Color = TextSecondary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Background)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(modifier = Modifier.width(4.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

