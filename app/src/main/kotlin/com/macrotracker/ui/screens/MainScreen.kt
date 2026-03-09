package com.macrotracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.screens.onboarding.SplashOverlay
import com.macrotracker.ui.viewmodel.OnboardingViewModel

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val splashShown         by onboardingViewModel.splashShown.collectAsState()

    // NavHost starts directly at the right destination — the splash is
    // a separate overlay and never a nav destination.
    val startDestination = remember(onboardingCompleted) {
        if (onboardingCompleted) Screen.Home.route else OnboardingRoutes.WELCOME
    }

    val navController = rememberNavController()
    val items = listOf(
        Screen.Home, Screen.Health, Screen.AI, Screen.History, Screen.Settings,
    )

    // Outer Box so the SplashOverlay can sit above the Scaffold at true
    // window size — completely outside inset/padding influence.
    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (items.any { it.route == currentRoute }) {
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
        ) { innerPadding ->
            DailyDashNavHost(
                navController        = navController,
                modifier             = Modifier.padding(innerPadding),
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
