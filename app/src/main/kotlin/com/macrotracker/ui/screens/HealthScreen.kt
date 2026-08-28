package com.macrotracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.screens.health.ActivitiesSection
import com.macrotracker.ui.screens.health.AnimatedMacroBarChart
import com.macrotracker.ui.screens.health.DailyHealthSection
import com.macrotracker.ui.screens.health.HealthMetric
import com.macrotracker.ui.screens.health.HealthStatCard
import com.macrotracker.ui.screens.health.HealthTrendsSection
import com.macrotracker.ui.screens.health.iconRes
import com.macrotracker.data.health.HealthActivity
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.HealthConnectCard
import com.macrotracker.ui.components.LoadingRow
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroLogItem
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.ScreenHeader
import com.macrotracker.ui.components.ScreenHeaderSpacer
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.calculatePercentageChange
import com.macrotracker.ui.components.draggableWidgetItems
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.components.rememberDraggableWidgetListState
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.DashboardViewModel
import com.macrotracker.ui.viewmodel.HealthConnectUiState
import com.macrotracker.ui.viewmodel.HealthViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt


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
    val macroHistory by healthViewModel.macroHistory.collectAsState()
    val macroRangeDays by healthViewModel.macroRangeDays.collectAsState()
    val macroMetric by healthViewModel.macroMetric.collectAsState()
    val macroSelectedDate by healthViewModel.macroSelectedDate.collectAsState()
    val macroSelectedLogs by healthViewModel.macroSelectedLogs.collectAsState()
    val macroHistoryLoading by healthViewModel.macroHistoryLoading.collectAsState()
    val healthHistory by healthViewModel.healthHistory.collectAsState()
    val healthWidgetOrder by healthViewModel.healthWidgetOrder.collectAsState()
    val healthConnectState by healthViewModel.healthConnectState.collectAsState()
    val activitiesState by healthViewModel.activitiesState.collectAsState()

    val selectedDate by healthViewModel.selectedDate.collectAsState()
    val intradayHeartRate by healthViewModel.intradayHeartRate.collectAsState()
    val detailedSleep by healthViewModel.detailedSleep.collectAsState()
    val todaySleepSessions by healthViewModel.todaySleepSessions.collectAsState()
    val weekStartDay by healthViewModel.weekStartDay.collectAsState()
    val weeksBack by healthViewModel.weeksBack.collectAsState()
    val macroInsights by healthViewModel.macroInsights.collectAsState()
    val weekInsights by healthViewModel.weekInsights.collectAsState()

    var selectedMetric by rememberSaveable { mutableStateOf(HealthMetric.STEPS) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedMetric) {
        healthViewModel.setDetailMetric(
            when (selectedMetric) {
                HealthMetric.HEART_RATE -> HealthViewModel.DetailMetric.HEART_RATE
                HealthMetric.SLEEP -> HealthViewModel.DetailMetric.SLEEP
                else -> HealthViewModel.DetailMetric.NONE
            },
        )
    }

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    val defaultHealthWidgets = remember {
        listOf(
            Triple("DAILY_HEALTH", "Daily Health", Icons.Filled.Favorite),
            Triple("ACTIVITIES", "Activities", Icons.AutoMirrored.Filled.DirectionsWalk),
            Triple("BODY_STATS", "Body Stats", Icons.Default.MonitorHeart),
            Triple("HISTORY", "Weekly Trends", Icons.AutoMirrored.Filled.ShowChart),
            Triple("SUMMARY", "Daily Summary", Icons.Default.ViewDay),
            Triple("ADD_ENTRY", "Add Entry", Icons.Default.Add),
            Triple("WEEK_AT_A_GLANCE", "Macro Trends", Icons.Outlined.BarChart),
            Triple("RECENT_LOGS", "Recent Logs", Icons.AutoMirrored.Filled.List),
        )
    }
    val parsedConfigs = remember(healthWidgetOrder) {
        parseWidgetConfig(healthWidgetOrder, defaultHealthWidgets)
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

    var pendingRouteActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    val routeLauncher = rememberLauncherForActivityResult(
        contract = ExerciseRouteRequestContract(),
    ) { route ->
        val id = pendingRouteActivityId ?: return@rememberLauncherForActivityResult
        pendingRouteActivityId = null
        healthViewModel.applyExerciseRoute(id, route)
    }

    // Health Connect permission launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val anyGranted = granted.any { it in healthViewModel.healthConnectPermissions }
        val pendingId = pendingRouteActivityId
        val routesGranted = healthViewModel.exerciseRoutesPermission in granted
        healthViewModel.loadHealthConnect(
            permissionsGranted = anyGranted,
            silent = pendingId != null,
        )
        dashboardViewModel.loadData(forceRefresh = true)
        if (pendingId != null) {
            if (routesGranted) {
                routeLauncher.launch(pendingId)
            } else {
                // Denied or omitted READ_EXERCISE_ROUTES — stop looping “Show GPS map”.
                healthViewModel.applyExerciseRoute(pendingId, null)
                pendingRouteActivityId = null
            }
        }
    }

    fun revealActivityRoute(activity: HealthActivity) {
        pendingRouteActivityId = activity.id
        // READ_EXERCISE_ROUTES must be granted before the per-workout GPS sheet.
        // If it is already granted, the permission activity returns immediately
        // and we then launch ExerciseRouteRequestContract below.
        hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
    }

    // First visit to this tab happens while the Activity is already resumed, so
    // ON_RESUME never fires. Load now, then again on later resumes (30s throttle).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        healthViewModel.loadDataOnResume()
        dashboardViewModel.loadDataThrottled()
    }
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

    val visibleConfigs = remember(parsedConfigs) {
        parsedConfigs.filter { it.isVisible }
    }
    val listState = rememberLazyListState()
    val dragState = rememberDraggableWidgetListState(
        items = visibleConfigs,
        lazyListState = listState,
        itemKey = { it.id },
        onReorder = { reordered ->
            val hidden = parsedConfigs.filter { !it.isVisible }
            healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(reordered + hidden))
        },
        haptics = haptics,
    )
    val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }

    CompositionLocalProvider(LocalTickersPaused provides tickersPaused) {
    LazyColumn(
        state = listState,
        userScrollEnabled = !dragState.isDragActive,
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
    ) {
        item(key = "header") {
        ScreenHeaderSpacer()

        ScreenHeader(
            title = "Health",
            subtitle = todayFormatted,
            trailing = {
                IconButton(onClick = { haptics.tick(); isEditMode = !isEditMode }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Widgets", tint = Primary)
                }
            },
        )

        Spacer(modifier = Modifier.height(20.dp))

        when (val hc = healthConnectState) {
            is HealthConnectUiState.PermissionRequired -> {
                HealthConnectCard(
                    onRequestPermission = {
                        hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            is HealthConnectUiState.NotAvailable -> {
                HealthConnectCard(
                    title = "Health Connect Unavailable",
                    message = "Health Connect isn’t available on this device. Macro tracking still works.",
                    onRequestPermission = null,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            is HealthConnectUiState.Error -> {
                HealthConnectCard(
                    title = "Health Connect Error",
                    message = hc.message,
                    actionLabel = "Retry",
                    onRequestPermission = { healthViewModel.loadHealthConnect() },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            else -> {}
        }
        }

        if (isEditMode) {
            item(key = "editor") {
            WidgetEditor(
                configs = parsedConfigs,
                onConfigsChanged = { newConfigs ->
                    healthViewModel.updateHealthWidgetOrder(encodeWidgetConfig(newConfigs))
                },
                onClose = { isEditMode = false }
            )
            }
        } else {
            draggableWidgetItems(
                state = dragState,
                itemKey = { it.id },
                haptics = haptics,
            ) { _, config, _ ->
                    when (config.id) {
                    "DAILY_HEALTH" -> {
                        val hcStats = (healthConnectState as? HealthConnectUiState.Success)?.stats
                        DailyHealthSection(
                            stats = hcStats,
                            weekInsights = weekInsights,
                            summary = summary,
                            detailedSleep = todaySleepSessions,
                            heartRateBpm = heartRateState.value.takeIf { heartRateState.isEnabled },
                            restingHrBpm = restingHeartRateState.value.takeIf { restingHeartRateState.isEnabled },
                            spo2Percent = oxygenSaturationState.value.takeIf { oxygenSaturationState.isEnabled },
                            respRate = respiratoryRateState.value.takeIf { respiratoryRateState.isEnabled },
                            loading = healthConnectState is HealthConnectUiState.Loading,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    "ACTIVITIES" -> {
                        ActivitiesSection(
                            state = activitiesState,
                            haptics = haptics,
                            onRequestPermission = {
                                hcPermissionLauncher.launch(healthViewModel.healthConnectPermissions)
                            },
                            onRetry = { healthViewModel.retryHealthConnect() },
                            onRevealRoute = { activity ->
                                haptics.tick()
                                revealActivityRoute(activity)
                            },
                            onExpandActivity = { healthViewModel.loadActivityHeartRate(it) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    "BODY_STATS" -> {
                        // Daily Health already surfaces today's vitals/chips — skip this duplicate
                        // grid when that widget is visible. Keep Body Stats for installs that
                        // hide Daily Health.
                        val dailyHealthVisible = parsedConfigs.any {
                            it.id == "DAILY_HEALTH" && it.isVisible
                        }
                        val showHeartRate = !dailyHealthVisible &&
                            heartRateState.isEnabled && heartRateState.value != "–"
                        val showRestingHr = !dailyHealthVisible &&
                            restingHeartRateState.isEnabled && restingHeartRateState.value != "–"
                        val showSpo2 = !dailyHealthVisible &&
                            oxygenSaturationState.isEnabled && oxygenSaturationState.value != "–"
                        val showResp = !dailyHealthVisible &&
                            respiratoryRateState.isEnabled && respiratoryRateState.value != "–"
                        val showSteps = !dailyHealthVisible && stepsState.isEnabled
                        val showDistance = !dailyHealthVisible && distanceState.isEnabled
                        val showFloors = !dailyHealthVisible && floorsClimbedState.isEnabled
                        val showActive = !dailyHealthVisible && activeCaloriesState.isEnabled
                        val isAnyStatEnabled = showHeartRate || showRestingHr || showSpo2 || showResp ||
                                showSteps || showDistance || showFloors || showActive

                        if (isAnyStatEnabled) {
                            MacroCard(delayMs = 0) {
                                Text(
                                    "Body Stats",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = 2,
                                ) {
                                    if (showHeartRate) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Heart Rate",
                                            value = "${heartRateState.value} bpm",
                                            percentageChange = calculatePercentageChange(heartRateState.today, heartRateState.yesterday),
                                            iconRes = HealthMetric.HEART_RATE.iconRes(),
                                            color = Color(0xFFEF5350),
                                        )
                                    }
                                    if (showRestingHr) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Resting HR",
                                            value = "${restingHeartRateState.value} bpm",
                                            percentageChange = calculatePercentageChange(restingHeartRateState.today, restingHeartRateState.yesterday),
                                            iconRes = HealthMetric.RESTING_HEART_RATE.iconRes(),
                                            color = Color(0xFFE57373),
                                        )
                                    }
                                    if (showSpo2) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "SpO2",
                                            value = "${oxygenSaturationState.value} %",
                                            percentageChange = calculatePercentageChange(oxygenSaturationState.today, oxygenSaturationState.yesterday),
                                            iconRes = HealthMetric.OXYGEN_SATURATION.iconRes(),
                                            color = Color(0xFF42A5F5),
                                        )
                                    }
                                    if (showResp) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Resp. Rate",
                                            value = "${respiratoryRateState.value} rpm",
                                            percentageChange = calculatePercentageChange(respiratoryRateState.today, respiratoryRateState.yesterday),
                                            iconRes = HealthMetric.RESPIRATORY_RATE.iconRes(),
                                            color = Color(0xFF26C6DA),
                                        )
                                    }
                                    if (showSteps) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Steps",
                                            value = stepsState.value ?: "0",
                                            percentageChange = calculatePercentageChange(stepsState.today, stepsState.yesterday),
                                            iconRes = HealthMetric.STEPS.iconRes(),
                                            color = Primary,
                                        )
                                    }
                                    if (showDistance) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Distance",
                                            value = "${distanceState.value} km",
                                            percentageChange = calculatePercentageChange(distanceState.today, distanceState.yesterday),
                                            iconRes = HealthMetric.DISTANCE.iconRes(),
                                            color = Primary,
                                        )
                                    }
                                    if (showFloors) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Floors",
                                            value = floorsClimbedState.value ?: "0",
                                            percentageChange = calculatePercentageChange(floorsClimbedState.today, floorsClimbedState.yesterday),
                                            iconRes = HealthMetric.FLOORS_CLIMBED.iconRes(),
                                            color = Color(0xFF66BB6A),
                                        )
                                    }
                                    if (showActive) {
                                        HealthStatCard(
                                            modifier = Modifier.weight(1f),
                                            metricName = "Active Cals",
                                            value = activeCaloriesState.value ?: "0",
                                            percentageChange = calculatePercentageChange(activeCaloriesState.today, activeCaloriesState.yesterday),
                                            iconRes = HealthMetric.CALORIES.iconRes(),
                                            color = Color(0xFFFF9800),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "HISTORY" -> {
                        if (healthHistory.isNotEmpty()) {
                            HealthTrendsSection(
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
                                onDateSelected = { healthViewModel.selectDate(it) },
                                onMetricSelected = {
                                    selectedMetric = it
                                    haptics.tick()
                                },
                                onWeekStartDaySelected = {
                                    healthViewModel.setWeekStartDay(it)
                                    haptics.tick()
                                },
                                onPreviousWeek = { healthViewModel.previousWeek() },
                                onNextWeek = { healthViewModel.nextWeek() },
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    "SUMMARY" -> {
                        val s = summary
                        if (s != null) {
                            val hcStats = (healthConnectState as? HealthConnectUiState.Success)?.stats
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

                                val calRemaining = (s.calorieGoal - s.totalCalories).coerceAtLeast(0)
                                val proteinRemaining = (s.proteinGoal - s.totalProtein).coerceAtLeast(0)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MacroDayStatCard(
                                        "Left today",
                                        "$calRemaining kcal · ${proteinRemaining}g",
                                        Modifier.weight(1f),
                                    )
                                    if (hcStats != null && (hcStats.activeCaloriesBurned > 0 || hcStats.steps > 0)) {
                                        MacroDayStatCard(
                                            "Burned",
                                            buildString {
                                                if (hcStats.activeCaloriesBurned > 0) {
                                                    append("${hcStats.activeCaloriesBurned.toInt()} active")
                                                }
                                                if (hcStats.steps > 0) {
                                                    if (isNotEmpty()) append('\n')
                                                    append(String.format(Locale.US, "%,d steps", hcStats.steps))
                                                }
                                            },
                                            Modifier.weight(1f),
                                        )
                                    }
                                }

                                // Energy balance: intake vs active burn (Apple Health style)
                                if (hcStats != null && hcStats.activeCaloriesBurned > 0 && s.totalCalories > 0) {
                                    val net = s.totalCalories - hcStats.activeCaloriesBurned.roundToInt()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MacroDayStatCard(
                                            "Energy balance",
                                            when {
                                                net > 0 -> "+$net kcal"
                                                net < 0 -> "$net kcal"
                                                else -> "Even"
                                            },
                                            Modifier.weight(1f),
                                        )
                                        MacroDayStatCard(
                                            "Intake / Active",
                                            "${s.totalCalories} / ${hcStats.activeCaloriesBurned.roundToInt()}",
                                            Modifier.weight(1f),
                                        )
                                    }
                                }
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
                        MacroTrendsSection(
                            rangeDays = macroRangeDays,
                            metric = macroMetric,
                            macroHistory = macroHistory,
                            selectedDate = macroSelectedDate,
                            selectedLogs = macroSelectedLogs,
                            loading = macroHistoryLoading,
                            macroInsights = macroInsights,
                            haptics = haptics,
                            onRangeDaysSelected = { healthViewModel.setMacroRangeDays(it) },
                            onMetricSelected = { healthViewModel.setMacroMetric(it) },
                            onDateSelected = { healthViewModel.selectMacroDate(it) },
                            onDeleteLog = { healthViewModel.deleteLog(it) },
                        )
                        Spacer(modifier = Modifier.height(20.dp))
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
                                val reversedLogs = remember(logs) { logs.asReversed().take(20) }
                                WidgetScrollBox {
                                    reversedLogs.forEach { log ->
                                        MacroLogItem(
                                            log = log,
                                            onDelete = { healthViewModel.deleteLog(it) },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
    }
}

// ── Macro Trends (moved from History tab) ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroTrendsSection(
    rangeDays: Int,
    metric: String,
    macroHistory: List<DailySummary>,
    selectedDate: String,
    selectedLogs: List<MacroLogEntity>,
    loading: Boolean,
    macroInsights: com.macrotracker.ui.screens.health.MacroRangeInsights?,
    haptics: HapticHelper,
    onRangeDaysSelected: (Int) -> Unit,
    onMetricSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val dates = remember(rangeDays) {
        (0 until rangeDays).map { i ->
            LocalDate.now().minusDays((rangeDays - 1 - i).toLong()).format(dateFormat)
        }
    }
    val metricValues = dates.map { date ->
        val day = macroHistory.find { it.date == date }
        if (metric == "calories") day?.totalCalories ?: 0 else day?.totalProtein ?: 0
    }
    val selectedMacro = macroHistory.find { it.date == selectedDate }
    val barColor = if (metric == "calories") Color(0xFFFF9800) else Primary
    val selectedIndex = dates.indexOf(selectedDate).coerceAtLeast(0)
    val labels = dates.map { date ->
        try {
            LocalDate.parse(date).dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
        } catch (_: Exception) {
            "?"
        }
    }
    val avgMetric = metricValues.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }

    Column {
        MacroCard(delayMs = 70) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.BarChart, contentDescription = null, tint = barColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Macro Trends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(7, 14, 30).forEach { option ->
                    val isActive = option == rangeDays
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) barColor else Background)
                            .clickable {
                                haptics.tick()
                                onRangeDaysSelected(option)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${option}d",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) Color.White else TextSecondary,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf("calories" to "Calories", "protein" to "Protein").forEach { (key, label) ->
                    val isActive = metric == key
                    val tint = if (key == "calories") Color(0xFFFF9800) else Primary
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) tint.copy(alpha = 0.18f) else Background)
                            .border(1.dp, if (isActive) tint.copy(alpha = 0.45f) else Border, CircleShape)
                            .clickable {
                                haptics.tick()
                                onMetricSelected(key)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) tint else TextSecondary,
                        )
                    }
                }
            }

            macroInsights?.let { insights ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroDayStatCard(
                        "Avg",
                        if (metric == "calories") {
                            String.format(Locale.US, "%,.0f kcal", insights.avgCalories)
                        } else {
                            String.format(Locale.US, "%,.0fg", insights.avgProtein)
                        },
                        Modifier.weight(1f),
                    )
                    MacroDayStatCard(
                        "Adherence",
                        buildString {
                            val value = if (metric == "calories") insights.calorieAdherence else insights.proteinAdherence
                            append(if (value != null) "${(value * 100).toInt()}%" else "—")
                        },
                        Modifier.weight(1f),
                    )
                    MacroDayStatCard(
                        "Logged",
                        "${insights.loggedDays}/${insights.rangeDays}",
                        Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedMacroBarChart(
                values = metricValues,
                labels = labels,
                selectedIndex = selectedIndex,
                color = barColor,
                avgValue = avgMetric,
                haptics = haptics,
                onSelect = { idx -> dates.getOrNull(idx)?.let(onDateSelected) },
            )

            if (loading) {
                LoadingRow(color = Primary) {
                    Text("Loading trends…", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MacroCard(delayMs = 100) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val displayDate = try {
                    LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                } catch (_: Exception) { selectedDate }
                Text(displayDate, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroDayStatCard("Calories", "${selectedMacro?.totalCalories ?: 0} kcal", Modifier.weight(1f))
                MacroDayStatCard("Protein", "${selectedMacro?.totalProtein ?: 0}g", Modifier.weight(1f))
                MacroDayStatCard("Meals", "${selectedLogs.size}", Modifier.weight(1f))
            }

            Text(
                "Food Logs",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (selectedLogs.isEmpty()) {
                Text(
                    "No food logs for this day.",
                    color = TextSecondary,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                )
            } else {
                WidgetScrollBox {
                    selectedLogs.forEach { log ->
                        MacroLogItem(
                            log = log,
                            onDelete = onDeleteLog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroDayStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Background),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
