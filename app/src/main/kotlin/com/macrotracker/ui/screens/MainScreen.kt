package com.macrotracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.screens.onboarding.SplashOverlay
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val splashShown         by onboardingViewModel.splashShown.collectAsState()
    
    // We also need to check if the AI screen should be visible
    val homeViewModel: com.macrotracker.ui.viewmodel.HomeViewModel = hiltViewModel()
    val hasAiApiKey = homeViewModel.hasAiApiKey

    // NavHost starts directly at the right destination — the splash is
    // a separate overlay and never a nav destination.
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

    // ── Hide on scroll logic ──────────────────────────────────────────
    val density = LocalDensity.current
    val navBarHeight = 140.dp // Increased significantly to ensure the selection pill is also hidden
    val navBarHeightPx = with(density) { navBarHeight.toPx() }
    val navigationBarsPaddingPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val totalBottomOffsetPx = navBarHeightPx + navigationBarsPaddingPx

    var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx + delta
                bottomBarOffsetHeightPx = newOffset.coerceIn(-totalBottomOffsetPx, 0f)
                return Offset.Zero
            }
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = bottomBarOffsetHeightPx,
        animationSpec = tween(durationMillis = 200),
        label = "nav_bar_offset"
    )

    // Outer Box so the SplashOverlay can sit above the Scaffold at true
    // window size — completely outside inset/padding influence.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (items.any { it.route == currentRoute }) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x = 0, y = -animatedOffset.roundToInt()) }
                            .navigationBarsPadding()
                    ) {
                        PillNavigationBar(
                            items        = items,
                            currentRoute = currentRoute,
                            onItemClick  = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        ) { _ ->
            DailyDashNavHost(
                navController        = navController,
                modifier             = Modifier.statusBarsPadding(),
                startDestination     = startDestination,
                onboardingCompleted  = onboardingCompleted,
                onOnboardingComplete = { onboardingViewModel.completeOnboarding() },
            )
        }

        // Splash overlay — drawn above the Scaffold so it covers status-bar,
        // nav-bar, and bottom nav without any inset fighting.
        // Removed from composition the moment it calls onFinished().
        if (!splashShown) {
            SplashOverlay(
                onFinished = { onboardingViewModel.markSplashShown() }
            )
        }
    }
}
