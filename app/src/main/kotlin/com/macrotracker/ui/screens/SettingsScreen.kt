package com.macrotracker.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.calendar.CalendarInfo
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToHelp: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedKey by viewModel.geminiApiKey.collectAsState()
    val healthConnectAvailable by viewModel.healthConnectConnected.collectAsState()
    val weatherConnected by viewModel.weatherConnected.collectAsState()
    val calendarConnected by viewModel.calendarConnected.collectAsState()
    val availableCalendars by viewModel.availableCalendars.collectAsState()
    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsState()

    var draftKey by remember(savedKey) { mutableStateOf(savedKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val isDirty = draftKey.trim() != savedKey
    val hasKey = savedKey.isNotBlank()

    val keyFormatOk = draftKey.isBlank() || draftKey.trim().startsWith("AIza")
    val keyFeedback: String? = when {
        draftKey.isNotBlank() && !keyFormatOk -> "⚠ Doesn't look like a Gemini key (should start with AIza…)"
        else -> null
    }

    LaunchedEffect(Unit) {
        viewModel.refreshConnectionStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            )

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
                            onToggle = { viewModel.toggleCalendar(cal.id) }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Border.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            ConnectionRow(
                icon = Icons.Outlined.SmartToy,
                name = "Gemini AI",
                description = "Food estimates & label scanning",
                connected = hasKey,
                iconTint = Color(0xFF7C4DFF),
            )
        }

        // ── Gemini API Key Card ──────────────────────────────────────────
        MacroCard(delayMs = 100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  Gemini API Key",
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
                text = "Required for AI food estimates & label scanning. " +
                    "Get a free key at aistudio.google.com. " +
                    "Uses gemini-2.0-flash (free tier).",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draftKey,
                onValueChange = {
                    draftKey = it
                    keySaved = false
                },
                placeholder = { Text("Paste your Gemini API key here", color = TextSecondary, fontSize = 13.sp) },
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
                Text(
                    text = keyFeedback,
                    fontSize = 12.sp,
                    color = Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
                        viewModel.saveApiKey(draftKey)
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
                            viewModel.saveApiKey("")
                            keySaved = false
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.SECONDARY,
                    )
                }
            }
        }

        // ── AI Model Info Card ───────────────────────────────────────────
        MacroCard(delayMs = 150) {
            Text(
                text = "AI Model",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                        text = "gemini-2.0-flash",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Fast · Free tier · Sufficient for nutrition",
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

        // ── Quick Links ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroButton(
                text = "📊 Stats & Goals",
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
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (connected) "Connected" else "Not connected",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (connected) Success else TextSecondary,
        )
    }
}
