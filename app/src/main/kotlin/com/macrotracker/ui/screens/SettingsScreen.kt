package com.macrotracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.macrotracker.ui.theme.MacroMotion
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.calendar.CalendarInfo
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.OpenRouterModels
import com.macrotracker.data.update.AppReleaseNotes
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.MarkdownText
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import com.macrotracker.ui.viewmodel.StatsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToHelp: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    statsViewModel: StatsViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current as ComponentActivity
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val savedKey by viewModel.geminiApiKey.collectAsState()
    val savedOpenAiKey by viewModel.openAiApiKey.collectAsState()
    val savedOpenRouterKey by viewModel.openRouterApiKey.collectAsState()
    val openRouterModelId by viewModel.openRouterModelId.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val healthConnectAvailable by viewModel.healthConnectConnected.collectAsState()
    val weatherConnected by viewModel.weatherConnected.collectAsState()
    val calendarConnected by viewModel.calendarConnected.collectAsState()
    val availableCalendars by viewModel.availableCalendars.collectAsState()
    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsState()

    val masterHealthConnectEnabled by viewModel.masterHealthConnectEnabled.collectAsState()
    val masterWeatherEnabled by viewModel.masterWeatherEnabled.collectAsState()
    val masterCalendarEnabled by viewModel.masterCalendarEnabled.collectAsState()

    val heartRateEnabled by viewModel.heartRateEnabled.collectAsState()
    val restingHeartRateEnabled by viewModel.restingHeartRateEnabled.collectAsState()
    val oxygenSaturationEnabled by viewModel.oxygenSaturationEnabled.collectAsState()
    val respiratoryRateEnabled by viewModel.respiratoryRateEnabled.collectAsState()
    val stepsEnabled by viewModel.stepsEnabled.collectAsState()
    val distanceEnabled by viewModel.distanceEnabled.collectAsState()
    val floorsClimbedEnabled by viewModel.floorsClimbedEnabled.collectAsState()
    val activeCaloriesEnabled by viewModel.activeCaloriesEnabled.collectAsState()

    val calGoal by statsViewModel.calGoal.collectAsState()
    val protGoal by statsViewModel.protGoal.collectAsState()
    var goalsSaved by remember { mutableStateOf(false) }

    val activeSavedKey = when (aiProvider) {
        AiProvider.GEMINI -> savedKey
        AiProvider.OPENAI -> savedOpenAiKey
        AiProvider.OPENROUTER -> savedOpenRouterKey
    }
    var draftKey by remember(aiProvider, activeSavedKey) { mutableStateOf(activeSavedKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val updateState by appUpdateViewModel.state.collectAsState()
    val releaseNotes by appUpdateViewModel.releaseNotes.collectAsState()
    val releaseNotesLoading by appUpdateViewModel.releaseNotesLoading.collectAsState()

    LaunchedEffect(Unit) {
        appUpdateViewModel.loadReleaseNotes()
    }

    val isDirty = draftKey.trim() != activeSavedKey
    val hasKey = activeSavedKey.isNotBlank()

    val keyFormatOk = AiApiClient.looksLikeValidKey(aiProvider, draftKey)
    val keyFeedback: String? = when {
        draftKey.isNotBlank() && !keyFormatOk -> when (aiProvider) {
            AiProvider.GEMINI -> "Doesn't look like a Gemini key (should start with AIza…)"
            AiProvider.OPENAI -> "Doesn't look like an OpenAI key (should start with sk-…)"
            AiProvider.OPENROUTER -> "Doesn't look like an OpenRouter key (should start with sk-or-…)"
        }
        else -> null
    }

    val context = LocalContext.current

    fun hasCalendarPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setMasterCalendarEnabled(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshConnectionStatus()
        statsViewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
        Spacer(modifier = Modifier.height(20.dp))

        // ── Connections Card ─────────────────────────────────────────────
        MacroCard(delayMs = 50) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Connections",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }

            Text(
                text = "Services connected to DailyDash",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            ConnectionRow(
                icon = Icons.Outlined.FavoriteBorder,
                name = "Health Connect",
                description = "Steps, heart rate, sleep & active calories",
                connected = healthConnectAvailable,
                iconTint = Color(0xFFEF5350),
                enabled = masterHealthConnectEnabled,
                onToggle = { 
                    haptics.tick()
                    viewModel.setMasterHealthConnectEnabled(it) 
                }
            )

            if (healthConnectAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Health Connect Metrics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                MetricToggleRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    name = "Heart Rate",
                    enabled = heartRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("heart_rate_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.Outlined.Bedtime,
                    name = "Resting Heart Rate",
                    enabled = restingHeartRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("resting_heart_rate_enabled", it)
                    }
                )

                MetricToggleRow(
                    icon = Icons.Outlined.Bloodtype,
                    name = "Oxygen Saturation",
                    enabled = oxygenSaturationEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("oxygen_saturation_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.Outlined.Air,
                    name = "Respiratory Rate",
                    enabled = respiratoryRateEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("respiratory_rate_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    name = "Steps",
                    enabled = stepsEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("steps_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.Outlined.Route,
                    name = "Distance",
                    enabled = distanceEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("distance_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.Outlined.Stairs,
                    name = "Floors Climbed",
                    enabled = floorsClimbedEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("floors_climbed_enabled", it)
                    }
                )
                MetricToggleRow(
                    icon = Icons.Outlined.LocalFireDepartment,
                    name = "Active Calories",
                    enabled = activeCaloriesEnabled,
                    onCheckedChange = {
                        haptics.tick()
                        viewModel.setMetricEnabled("active_calories_enabled", it)
                    }
                )
            }

            HorizontalDivider(
                color = Border.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            ConnectionRow(
                icon = Icons.Outlined.Cloud,
                name = "Weather Data",
                description = "Location-based weather via Yr.no",
                connected = weatherConnected,
                iconTint = Color(0xFF42A5F5),
                enabled = masterWeatherEnabled,
                onToggle = { 
                    haptics.tick()
                    viewModel.setMasterWeatherEnabled(it) 
                }
            )

            HorizontalDivider(
                color = Border.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            ConnectionRow(
                icon = Icons.Outlined.CalendarMonth,
                name = "Google Calendar",
                description = "Today's events & schedule on dashboard",
                connected = calendarConnected,
                iconTint = Color(0xFF4285F4),
                enabled = masterCalendarEnabled,
                onToggle = { enabled ->
                    haptics.tick()
                    if (enabled) {
                        if (hasCalendarPermission()) {
                            viewModel.setMasterCalendarEnabled(true)
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        viewModel.setMasterCalendarEnabled(false)
                    }
                }
            )

            if (calendarConnected && availableCalendars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select calendars to show:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableCalendars.forEach { cal ->
                        CalendarChip(
                            calendar = cal,
                            isSelected = selectedCalendarIds.contains(cal.id),
                            onToggle = {
                                haptics.tick()
                                viewModel.toggleCalendar(cal.id)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Border.copy(.3f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            ConnectionRow(
                icon = Icons.Outlined.SmartToy,
                name = "AI (${aiProvider.displayName})",
                description = "Food estimates, label scanning & weather tips",
                connected = hasKey,
                iconTint = Color(0xFF7C4DFF)
            )
        }

        // ── AI Provider + API Key Card ────────────────────────────────────
        MacroCard(delayMs = 100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  AI Provider",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                if (hasKey) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Key saved",
                        tint = Success,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose Gemini, OpenAI, or OpenRouter for food estimates, label scanning, and weather tips.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            AiProviderToggle(
                selected = aiProvider,
                onSelect = { provider ->
                    haptics.tick()
                    viewModel.setAiProvider(provider)
                    keySaved = false
                },
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${aiProvider.displayName} API Key",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = AiApiClient.keyHint(aiProvider),
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = draftKey,
                onValueChange = {
                    draftKey = it
                    keySaved = false
                },
                placeholder = {
                    Text(
                        AiApiClient.keyPlaceholder(aiProvider),
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = {
                        haptics.tick()
                        keyVisible = !keyVisible
                    }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key",
                            tint = TextSecondary,
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedBorderColor = if (!keyFormatOk) Error else Primary,
                    unfocusedBorderColor = if (!keyFormatOk) Error else Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                ),
            )

            if (keyFeedback != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = keyFeedback,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroButton(
                    text = if (keySaved) "Saved ✓" else "Save Key",
                    onClick = {
                        haptics.confirm()
                        viewModel.saveApiKey(aiProvider, draftKey)
                        keySaved = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDirty || !hasKey,
                )
                if (hasKey) {
                    MacroButton(
                        text = "Clear",
                        onClick = {
                            haptics.reject()
                            draftKey = ""
                            viewModel.saveApiKey(aiProvider, "")
                            keySaved = false
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }
        }

        // ── AI Model Info / OpenRouter selector ───────────────────────────
        MacroCard(delayMs = 150) {
            Text(
                text = if (aiProvider == AiProvider.OPENROUTER) "OpenRouter Model" else "AI Model",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (aiProvider == AiProvider.OPENROUTER) {
                Text(
                    text = "Pick a cheap vision-capable model. Prices are OpenRouter list rates (USD per 1M tokens) and may change. DailyDash calls are short, so even paid models stay fractions of a cent.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OpenRouterModelSelector(
                    selectedId = openRouterModelId,
                    onSelect = { modelId ->
                        haptics.tick()
                        viewModel.setOpenRouterModelId(modelId)
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = AiApiClient.modelLabel(aiProvider),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                        Text(
                            text = when (aiProvider) {
                                AiProvider.GEMINI -> "Fast · Free tier · Sufficient for nutrition"
                                AiProvider.OPENAI -> "Fast · Vision-capable · gpt-4o-mini"
                                AiProvider.OPENROUTER -> ""
                            },
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                    Text(
                        text = if (hasKey) "Active" else "No Key",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasKey) Success else Error,
                    )
                }
            }
        }

        // ── Daily Goals ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        MacroCard(delayMs = 175) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Daily Goals",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Calorie and protein targets for progress bars",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Calories",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    MacroTextField(
                        value = calGoal,
                        onValueChange = {
                            goalsSaved = false
                            statsViewModel.setCalGoal(it)
                        },
                        placeholder = "2000",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Protein (g)",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    MacroTextField(
                        value = protGoal,
                        onValueChange = {
                            goalsSaved = false
                            statsViewModel.setProtGoal(it)
                        },
                        placeholder = "150",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            MacroButton(
                text = if (goalsSaved) "Goals Saved" else "Save Goals",
                onClick = {
                    haptics.confirm()
                    statsViewModel.saveGoals()
                    goalsSaved = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        // ── Quick Links ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))

        // Widgets showcase — full-width prominent button
        MacroCard(delayMs = 200) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Text("📱", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Home Screen Widgets",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "8 widgets — nutrition, health, weather, calendar & F1",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
            MacroButton(
                text = "Browse & Add Widgets",
                onClick = {
                    haptics.click()
                    onNavigateToWidgets()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroButton(
                text = "📊 Stats",
                onClick = onNavigateToStats,
                modifier = Modifier.weight(1f),
            )
            MacroButton(
                text = "❓ Help & How-To",
                onClick = onNavigateToHelp,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.SECONDARY,
            )
        }
        MacroButton(
            text = "🎓 Replay Tutorial",
            onClick = {
                haptics.click()
                // Keep onboardingCompleted=true so Back from Welcome returns to Settings
                // instead of leaving the user on Home with incomplete onboarding.
                onReplayTutorial()
            },
            variant = ButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── App Update (bottom of Settings) ──────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        MacroCard(delayMs = 250) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Icon(
                    Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Update",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Installed ${appUpdateViewModel.currentVersionName} (build ${appUpdateViewModel.currentVersionCode})",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }

            when (val s = updateState) {
                is AppUpdateUiState.Idle -> {
                    Text(
                        text = "Listens for new GitHub Releases and prompts in-app automatically.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Checking -> {
                    Text(
                        text = "Checking for updates…",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.UpToDate -> {
                    Text(
                        text = "You're on the latest build.",
                        fontSize = 12.sp,
                        color = Success,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Available -> {
                    Text(
                        text = "Update available: ${s.info.versionName} (build ${s.info.versionCode})",
                        fontSize = 12.sp,
                        color = Primary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Downloading -> {
                    Text(
                        text = "Downloading ${s.info.versionName}… ${(s.progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        color = Primary,
                    )
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    Text(
                        text = "Ready to install ${s.info.versionName}",
                        fontSize = 12.sp,
                        color = Success,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                is AppUpdateUiState.Error -> {
                    Text(
                        text = s.message,
                        fontSize = 12.sp,
                        color = Error,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }

            when (val s = updateState) {
                is AppUpdateUiState.Available -> {
                    MacroButton(
                        text = "Update to ${s.info.versionName}",
                        onClick = {
                            haptics.confirm()
                            if (appUpdateViewModel.canInstallPackages()) {
                                appUpdateViewModel.startDownload(s.info)
                            } else {
                                context.startActivity(appUpdateViewModel.installPermissionSettingsIntent())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    MacroButton(
                        text = "Install update",
                        onClick = {
                            haptics.confirm()
                            appUpdateViewModel.installDownloaded()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is AppUpdateUiState.Downloading -> {
                    MacroButton(
                        text = "Downloading…",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    MacroButton(
                        text = if (updateState is AppUpdateUiState.Checking) "Checking…" else "Check for updates",
                        onClick = {
                            haptics.click()
                            appUpdateViewModel.checkFromSettings()
                        },
                        enabled = updateState !is AppUpdateUiState.Checking,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Release notes",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when {
                releaseNotesLoading && releaseNotes.isEmpty() -> {
                    Text(
                        text = "Loading release history…",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                releaseNotes.isEmpty() -> {
                    Text(
                        text = "No published releases found yet.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                else -> {
                    releaseNotes.forEach { release ->
                        ReleaseNotesDropdown(
                            release = release,
                            isCurrent = release.versionCode == appUpdateViewModel.currentVersionCode,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesDropdown(
    release: AppReleaseNotes,
    isCurrent: Boolean,
) {
    var expanded by remember(release.tagName) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Background)
            .border(1.dp, Border.copy(alpha = 0.55f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.click()
                    expanded = !expanded
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${release.versionName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (release.isNewerThanInstalled) Primary else TextPrimary,
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Installed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Success,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Success.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else if (release.isNewerThanInstalled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "build ${release.versionCode}" +
                        (release.publishedAt?.take(10)?.let { " · $it" } ?: ""),
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                HorizontalDivider(
                    color = Border.copy(alpha = 0.45f),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                MarkdownText(
                    markdown = release.releaseNotes.ifBlank { "No release notes." },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun AiProviderToggle(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(12.dp))
            .border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AiProvider.entries.forEach { provider ->
            val isSelected = provider == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) Primary else Color.Transparent)
                    .clickable { onSelect(provider) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.displayName,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OpenRouterModelSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenRouterModels.options.forEach { model ->
            val selected = model.id == selectedId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = if (selected) Primary else Border.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .background(if (selected) Primary.copy(alpha = 0.08f) else Background)
                    .clickable { onSelect(model.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        if (model.recommended) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Best value",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier
                                    .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = Primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.priceLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    text = buildString {
                        append(model.approxRequestCostLabel)
                        if (model.supportsVision) append(" · Vision")
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.blurb,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun CalendarChip(
    calendar: CalendarInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val color = Color(calendar.color).copy(alpha = 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) color.copy(alpha = 0.15f) else Background)
            .border(
                width = 1.dp,
                color = if (isSelected) color.copy(alpha = 0.5f) else Border.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = calendar.name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TextPrimary else TextSecondary,
            maxLines = 1
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun ConnectionRow(
    icon: ImageVector,
    name: String,
    description: String,
    connected: Boolean,
    iconTint: Color,
    enabled: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (connected) "Connected" else "Not connected",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (connected) Success else TextSecondary,
            )
        }
        if (onToggle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (connected) "Connected" else "Not connected",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (connected) Success else TextSecondary,
            )
        }
    }
}

@Composable
private fun MetricToggleRow(
    name: String,
    enabled: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
