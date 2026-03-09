package com.macrotracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.ui.components.BodyStats
import com.macrotracker.ui.components.MetricInfo
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val heartRateState by viewModel.heartRateState.collectAsState()
    val stepsState by viewModel.stepsState.collectAsState()
    val activeCaloriesState by viewModel.activeCaloriesState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    val metrics = listOf(
        Pair(
            MetricInfo("Heart Rate", "bpm", Icons.Outlined.FavoriteBorder, Color(0xFFEF5350)),
            heartRateState
        ),
        Pair(
            MetricInfo("Steps", "", Icons.AutoMirrored.Outlined.DirectionsWalk, Color(0xFF42A5F5)),
            stepsState
        ),
        Pair(
            MetricInfo("Active Calories", "kcal", Icons.Outlined.LocalFireDepartment, Color(0xFFFFA726)),
            activeCaloriesState
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 120.dp),
    ) {
        Text("Dashboard", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Body Stats",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        BodyStats(metrics = metrics)
    }
}
