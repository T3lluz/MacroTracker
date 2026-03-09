package com.macrotracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.macrotracker.ui.screens.AIScreen
import com.macrotracker.ui.screens.CameraScanScreen
import com.macrotracker.ui.screens.HealthScreen
import com.macrotracker.ui.screens.HelpScreen
import com.macrotracker.ui.screens.HistoryScreen
import com.macrotracker.ui.screens.HomeScreen
import com.macrotracker.ui.screens.SettingsScreen
import com.macrotracker.ui.screens.StatsScreen
import com.macrotracker.ui.theme.MacroMotion

@Composable
fun DailyDashNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { MacroMotion.contentEnter },
        exitTransition = { MacroMotion.contentExit },
        popEnterTransition = { MacroMotion.contentEnter },
        popExitTransition = { MacroMotion.contentExit }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToHealth = { navController.navigate(Screen.Health.route) },
                onNavigateToStats = { navController.navigate("stats") },
            )
        }

        composable(Screen.Health.route) {
            HealthScreen(
                onNavigateToCameraScan = { navController.navigate("camera_scan") }
            )
        }

        composable(Screen.AI.route) {
            AIScreen(
                onNavigateToCameraScan = { navController.navigate("camera_scan") },
                onNavigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToHelp = { navController.navigate("help") },
                onNavigateToStats = { navController.navigate("stats") }
            )
        }

        // Sub-screens
        composable(
            route = "stats",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit }
        ) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "help",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit }
        ) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "camera_scan",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit }
        ) {
            CameraScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
