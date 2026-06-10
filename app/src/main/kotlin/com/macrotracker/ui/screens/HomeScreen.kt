package com.macrotracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.ui.components.WidgetEditor
import com.macrotracker.ui.components.draggableWidgetItems
import com.macrotracker.ui.components.encodeWidgetConfig
import com.macrotracker.ui.components.parseWidgetConfig
import com.macrotracker.ui.components.rememberDraggableWidgetListState
import com.macrotracker.ui.util.HOME_RESUME_DEFER_MS
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberVisibleHomeWidgetIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextSecondary
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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val homeWidgetOrder by viewModel.homeWidgetOrder.collectAsState()

    var quickFood by rememberSaveable { mutableStateOf("") }
    var quickCalories by rememberSaveable { mutableStateOf("") }
    var quickProtein by rememberSaveable { mutableStateOf("") }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    val defaultHomeWidgets = remember {
        listOf(
            Triple("F1", "F1 Standings", Icons.Default.Flag),
            Triple("YOUTUBE", "YouTube Feed", Icons.Default.PlayArrow),
            Triple("WEATHER", "Weather", Icons.Default.Cloud),
            Triple("CALENDAR", "Calendar", Icons.Default.CalendarMonth),
            Triple("BODY_STATS", "Body Stats", Icons.Default.MonitorHeart),
            Triple("PROGRESS", "Today's Progress", Icons.Default.PieChart),
            Triple("QUICK_ADD", "Quick Add", Icons.Default.Add),
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    delay(HOME_RESUME_DEFER_MS)
                    viewModel.loadData()
                }
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

    val parsedConfigs by remember(homeWidgetOrder) {
        derivedStateOf { parseWidgetConfig(homeWidgetOrder, defaultHomeWidgets) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.refreshAll(
                hasLocationPermission = hasLocationPermission(),
                hasCalendarPermission = hasCalendarPermission(),
                force = true,
                widgetIds = parsedConfigs.filter { it.isVisible }.map { it.id }.toSet(),
            )
        },
        modifier = Modifier.fillMaxSize().background(Background),
    ) {
        val visibleConfigs by remember(parsedConfigs) {
            derivedStateOf { parsedConfigs.filter { it.isVisible } }
        }
        val dragState = rememberDraggableWidgetListState(
            items = visibleConfigs,
            onReorder = { reordered ->
                val hidden = parsedConfigs.filter { !it.isVisible }
                viewModel.updateHomeWidgetOrder(encodeWidgetConfig(reordered + hidden))
            },
        )

        val listState = rememberLazyListState()
        val tickersPaused by remember { derivedStateOf { listState.isScrollInProgress } }
        val visibleWidgetIds = rememberVisibleHomeWidgetIds(listState)

        LaunchedEffect(visibleWidgetIds) {
            if (visibleWidgetIds.isNotEmpty()) {
                viewModel.refreshAll(
                    hasLocationPermission = hasLocationPermission(),
                    hasCalendarPermission = hasCalendarPermission(),
                    widgetIds = visibleWidgetIds,
                )
            }
        }

        val onRequestLocationPermission = remember(locationPermissionLauncher) {
            {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
        val onRequestCalendarPermission = remember(calendarPermissionLauncher) {
            { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
        }
        val hasLocationPermissionFn = remember(context) { { hasLocationPermission() } }

        CompositionLocalProvider(LocalTickersPaused provides tickersPaused) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 16.dp,
                bottom = 120.dp,
            ),
        ) {
            item(key = "header") {
                Spacer(modifier = Modifier.height(48.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(greeting, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
                        Text(todayFormatted, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = { haptics.tick(); isEditMode = !isEditMode }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Widgets", tint = Primary)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isEditMode) {
                item(key = "editor") {
                    WidgetEditor(
                        configs = parsedConfigs,
                        onConfigsChanged = { newConfigs ->
                            viewModel.updateHomeWidgetOrder(encodeWidgetConfig(newConfigs))
                        },
                        onClose = { isEditMode = false },
                    )
                }
            } else {
                draggableWidgetItems(
                    state = dragState,
                    itemKey = { it.id },
                    haptics = haptics,
                    scope = scope,
                ) { _, config ->
                    HomeWidgetItem(
                        config = config,
                        isVisible = config.id in visibleWidgetIds,
                        viewModel = viewModel,
                        onNavigateToHealth = onNavigateToHealth,
                        onNavigateToStats = onNavigateToStats,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onRequestCalendarPermission = onRequestCalendarPermission,
                        hasLocationPermission = hasLocationPermissionFn,
                        quickFood = quickFood,
                        onQuickFoodChange = { quickFood = it },
                        quickCalories = quickCalories,
                        onQuickCaloriesChange = { quickCalories = it },
                        quickProtein = quickProtein,
                        onQuickProteinChange = { quickProtein = it },
                    )
                }
            }
        }
        }
    }
}
