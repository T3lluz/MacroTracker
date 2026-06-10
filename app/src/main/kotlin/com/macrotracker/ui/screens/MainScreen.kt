package com.macrotracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.screens.onboarding.SplashOverlay
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val splashShown by onboardingViewModel.splashShown.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val hasAiApiKey = geminiApiKey.isNotBlank()

    val startDestination = remember(onboardingCompleted) {
        if (onboardingCompleted) Screen.Home.route else OnboardingRoutes.WELCOME
    }

    val navController = rememberNavController()
    val items = remember(hasAiApiKey) {
        if (hasAiApiKey) {
            listOf(Screen.Home, Screen.Health, Screen.AI, Screen.History, Screen.Settings)
        } else {
            listOf(Screen.Home, Screen.Health, Screen.History, Screen.Settings)
        }
    }

    val onOnboardingComplete = remember(onboardingViewModel) {
        { onboardingViewModel.completeOnboarding() }
    }
    val onSplashFinished = remember(onboardingViewModel) {
        { onboardingViewModel.markSplashShown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenScrollScaffold(
            navController = navController,
            items = items,
            startDestination = startDestination,
            onboardingCompleted = onboardingCompleted,
            onOnboardingComplete = onOnboardingComplete,
        )

        if (!splashShown) {
            SplashOverlay(onFinished = onSplashFinished)
        }
    }
}

@Composable
private fun MainScreenScrollScaffold(
    navController: NavHostController,
    items: List<Screen>,
    startDestination: String,
    onboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
) {
    val density = LocalDensity.current
    val navBarHeight = 140.dp
    val navBarHeightPx = with(density) { navBarHeight.toPx() }
    val navigationBarsPaddingPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
    }
    val totalBottomOffsetPx = navBarHeightPx + navigationBarsPaddingPx

    var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(totalBottomOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                bottomBarOffsetHeightPx = (bottomBarOffsetHeightPx + delta)
                    .coerceIn(-totalBottomOffsetPx, 0f)
                return Offset.Zero
            }
        }
    }

    val navHostModifier = remember { Modifier.statusBarsPadding() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ScrollAwareBottomBar(
                    bottomBarOffsetHeightPx = bottomBarOffsetHeightPx,
                    navController = navController,
                    items = items,
                )
            },
        ) { _ ->
            DailyDashNavHost(
                navController = navController,
                modifier = navHostModifier,
                startDestination = startDestination,
                onboardingCompleted = onboardingCompleted,
                onOnboardingComplete = onOnboardingComplete,
            )
        }
    }
}

@Composable
private fun ScrollAwareBottomBar(
    bottomBarOffsetHeightPx: Float,
    navController: NavHostController,
    items: List<Screen>,
) {
    val animatedOffset by animateFloatAsState(
        targetValue = bottomBarOffsetHeightPx,
        animationSpec = tween(durationMillis = 200),
        label = "nav_bar_offset",
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    if (!items.any { it.route == currentRoute }) return

    val onItemClick = remember(navController) {
        { screen: Screen ->
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(x = 0, y = -animatedOffset.roundToInt()) }
            .navigationBarsPadding(),
    ) {
        PillNavigationBar(
            items = items,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
        )
    }
}
