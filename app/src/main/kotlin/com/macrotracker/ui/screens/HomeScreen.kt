package com.macrotracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroLogItem
import com.macrotracker.ui.components.MacroProgressBar
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onNavigateToCameraScan: () -> Unit,
    onNavigateToStats: () -> Unit,
    scannedFoodName: String? = null,
    scannedCalories: Int? = null,
    scannedProtein: Int? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var foodName by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Handle scanned food data passed from CameraScan
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
            .padding(bottom = 100.dp),
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Header
        Text("Today", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Primary)
        Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // Daily Summary Card
        val s = summary
        if (s != null) {
            MacroCard(delayMs = 100) {
                Text("Daily Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 16.dp))
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
        MacroCard(delayMs = 200) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add Entry", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                MacroButton(
                    text = "📷 Scan Label",
                    onClick = onNavigateToCameraScan,
                    modifier = Modifier.padding(start = 8.dp),
                    variant = ButtonVariant.PRIMARY,
                )
            }

            MacroTextField(value = foodName, onValueChange = { foodName = it }, placeholder = "Food Name (optional)")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        viewModel.addLog(foodName, cal, prot)
                        foodName = ""
                        calories = ""
                        protein = ""
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Recent Logs
        Spacer(modifier = Modifier.height(8.dp))
        Text("Recent Logs", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

        if (logs.isEmpty()) {
            Text(
                "No logs yet today.",
                color = TextSecondary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            )
        } else {
            logs.reversed().forEachIndexed { index, log ->
                MacroLogItem(log = log, onDelete = { viewModel.deleteLog(it) }, index = index)
            }
        }
    }
}

