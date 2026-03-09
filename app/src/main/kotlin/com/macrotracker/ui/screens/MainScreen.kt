package com.macrotracker.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.viewmodel.OnboardingViewModel

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val startDestination = if (onboardingCompleted) Screen.Home.route else OnboardingRoutes.SPLASH

    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Health,
        Screen.AI,
        Screen.History,
        Screen.Settings,
    )

    // Routes where the bottom bar should be hidden
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val showBottomBar = items.any { it.route == currentRoute }

            if (showBottomBar) {
                PillNavigationBar(
                    items = items,
                    currentRoute = currentRoute,
                    onItemClick = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        DailyDashNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            startDestination = startDestination,
            onOnboardingComplete = { onboardingViewModel.completeOnboarding() },
        )
    }
}
