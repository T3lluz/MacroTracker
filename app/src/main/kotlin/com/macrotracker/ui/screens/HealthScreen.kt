package com.macrotracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroLogItem
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.HealthConnectUiState
import com.macrotracker.ui.viewmodel.HealthViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HealthScreen(
    onNavigateToCameraScan: () -> Unit,
    scannedFoodName: String? = null,
    scannedCalories: Int? = null,
    scannedProtein: Int? = null,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val weekHistory by viewModel.weekHistory.collectAsState()
    val healthConnectState by viewModel.healthConnectState.collectAsState()

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()

    // Health Connect permission launcher
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.loadHealthConnect(permissionsGranted = granted.containsAll(viewModel.healthConnectPermissions))
    }

    // Load Health Connect on first composition
    LaunchedEffect(Unit) {
        viewModel.loadHealthConnect()
    }

    // Reload data every time the screen comes into view
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
                viewModel.loadHealthConnect()
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

    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Text("Health", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
        Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // ── Body Stats (Health Connect) ──────────────────────────────
        HealthConnectCard(
            state = healthConnectState,
            onRequestPermission = {
                try {
                    hcPermissionLauncher.launch(viewModel.healthConnectPermissions)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Could not open Health Connect: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onRetry = { viewModel.loadHealthConnect() },
        )

        // Daily Summary Card
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
        }

        // Add Entry Card
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
                )
                MacroTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    placeholder = "Protein (g)",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }

            MacroButton(
                text = "Add Log",
                onClick = {
                    val cal = calories.toIntOrNull() ?: 0
                    val prot = protein.toIntOrNull() ?: 0
                    if (cal > 0 || prot > 0) {
                        haptics.confirm()
                        viewModel.addLog(foodName, cal, prot)
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

        // Week at a Glance - mini bar chart
        if (weekHistory.isNotEmpty()) {
            MacroCard(delayMs = 200) {
                Text(
                    "This Week",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                val maxCal = weekHistory.maxOf { it.totalCalories }.coerceAtLeast(1)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weekHistory.forEach { day ->
                        val heightFraction = day.totalCalories.toFloat() / maxCal
                        val targetHeight = (10 + heightFraction * 70).dp
                        val isToday = day.date == LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        val dayLabel = try {
                            LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        } catch (_: Exception) { "?" }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
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
        }

        // Recent Logs
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Recent Logs",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        if (logs.isEmpty()) {
            Text(
                "No logs yet today.",
                color = TextSecondary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth(),
            )
        } else {
            logs.reversed().forEachIndexed { index, log ->
                MacroLogItem(log = log, onDelete = { viewModel.deleteLog(it) }, index = index)
            }
        }
    }
}

// ── Health Connect Card ──────────────────────────────────────────────

@Composable
private fun HealthConnectCard(
    state: HealthConnectUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    MacroCard(delayMs = 50) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { MacroMotion.contentEnter togetherWith MacroMotion.contentExit },
            label = "hcContent",
        ) { currentState ->
            when (currentState) {
                is HealthConnectUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading health data…", color = TextSecondary, fontSize = 14.sp)
                    }
                }

                is HealthConnectUiState.Success -> {
                    val stats = currentState.stats
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Body Stats",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = "Refresh",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Text(
                            "via Health Connect (Garmin)",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )

                        // 2×2 grid of stat tiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HealthStatTile(
                                icon = Icons.Outlined.DirectionsWalk,
                                value = "%,d".format(stats.steps),
                                label = "Steps",
                                iconTint = Primary,
                                modifier = Modifier.weight(1f),
                            )
                            HealthStatTile(
                                icon = Icons.Outlined.MonitorHeart,
                                value = if (stats.avgHeartRate > 0) "${stats.avgHeartRate} bpm" else "—",
                                label = "Avg Heart Rate",
                                iconTint = Color(0xFFEF5350),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val sleepDisplay = if (stats.sleepMinutes > 0) {
                                val h = stats.sleepMinutes / 60
                                val m = stats.sleepMinutes % 60
                                "${h}h ${m}m"
                            } else "—"

                            HealthStatTile(
                                icon = Icons.Outlined.Bedtime,
                                value = sleepDisplay,
                                label = "Sleep",
                                iconTint = Color(0xFF7C4DFF),
                                modifier = Modifier.weight(1f),
                            )
                            HealthStatTile(
                                icon = Icons.Outlined.LocalFireDepartment,
                                value = if (stats.activeCaloriesBurned > 0) {
                                    "${stats.activeCaloriesBurned.toInt()} kcal"
                                } else "—",
                                label = "Active Calories",
                                iconTint = Color(0xFFFF9800),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is HealthConnectUiState.PermissionRequired -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Body Stats",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "Connect to Health Connect to see your steps, heart rate, sleep & calories from Garmin.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                            lineHeight = 18.sp,
                        )
                        MacroButton(
                            text = "🔗 Connect Health Data",
                            onClick = onRequestPermission,
                            variant = ButtonVariant.PRIMARY,
                        )
                    }
                }

                is HealthConnectUiState.NotAvailable -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SyncDisabled,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Body Stats",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                "Health Connect is not available on this device.\nInstall it from the Play Store to see Garmin data.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                is HealthConnectUiState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Body Stats",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                currentState.message,
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        MacroButton(
                            text = "Retry",
                            onClick = onRetry,
                            variant = ButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = TextSecondary,
        )
    }
}
