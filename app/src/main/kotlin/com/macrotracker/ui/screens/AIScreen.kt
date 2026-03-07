package com.macrotracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.AiViewModel

@Composable
fun AIScreen(
    onNavigateToCameraScan: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.collectAsState()
    val estimate by viewModel.estimate.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val loggedEvent by viewModel.loggedEvent.collectAsState()

    // Navigate to Home after a successful log
    LaunchedEffect(loggedEvent) {
        if (loggedEvent) {
            viewModel.consumeLoggedEvent()
            onNavigateToHome()
        }
    }

    var foodQuery by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Text("AI Nutrition", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
        Text(
            "Ask for macro estimates when labels are unavailable.",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        // Camera Scan Card
        MacroCard(delayMs = 60) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Camera Scan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text(
                "Scan nutrition labels and let AI read the values automatically.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            MacroButton(text = "Open Camera Label Scan", onClick = onNavigateToCameraScan)
        }

        // Estimate Card
        MacroCard(delayMs = 80) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estimate Food Macros", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            MacroTextField(
                value = foodQuery,
                onValueChange = { 
                    foodQuery = it
                    if (estimate != null) {
                        viewModel.clearEstimate()
                    }
                },
                placeholder = "e.g. 1 medium avocado",
                singleLine = false,
                trailingIcon = {
                    if (foodQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            foodQuery = "" 
                            viewModel.clearEstimate()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )

            MacroButton(
                text = if (loading) "Estimating..." else "Estimate with AI",
                onClick = { viewModel.estimateNutrition(foodQuery) },
                enabled = !loading,
            )

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crunching an estimate…", fontSize = 13.sp, color = TextSecondary)
                }
            }

            val fb = feedback
            if (fb != null) {
                Text(
                    text = fb.text,
                    color = if (fb.isError) Error else Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Result Card
        val est = estimate
        if (est != null) {
            MacroCard(delayMs = 120) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Estimate Result", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    IconButton(onClick = { viewModel.clearEstimate() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = "Remove Estimate", tint = TextSecondary)
                    }
                }

                Text(est.foodName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
                Text(est.servingDescription, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultPill(label = "kcal", value = est.calories.toString(), modifier = Modifier.weight(1f))
                    ResultPill(label = "protein", value = "${est.protein}g", modifier = Modifier.weight(1f))
                }

                val confColor = when (est.confidence) {
                    "high" -> Secondary
                    "low" -> Error
                    else -> Primary
                }
                Text(
                    "Confidence: ${est.confidence}",
                    fontSize = 13.sp,
                    color = confColor,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(est.notes, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))

                MacroButton(text = "Log This Estimate", onClick = { viewModel.logEstimate() }, variant = ButtonVariant.SECONDARY)
            }
        }
    }
}

@Composable
fun ResultPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Background),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
