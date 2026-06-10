package com.macrotracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.macrotracker.ui.screens.WidgetsScreen
import com.macrotracker.ui.screens.onboarding.PermissionsScreen
import com.macrotracker.ui.screens.onboarding.TutorialScreen
import com.macrotracker.ui.screens.onboarding.WelcomeScreen
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion

@Composable
fun DailyDashNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route,
    onboardingCompleted: Boolean = false,
    onOnboardingComplete: () -> Unit = {},
) {
    val tabOrder = listOf(
        Screen.Home.route,
        Screen.Health.route,
        Screen.AI.route,
        Screen.History.route,
        Screen.Settings.route
    )

    fun getTabDirection(initial: String?, target: String?): Boolean {
        val initialIdx = tabOrder.indexOf(initial).takeIf { it != -1 } ?: 0
        val targetIdx = tabOrder.indexOf(target).takeIf { it != -1 } ?: 0
        return targetIdx > initialIdx
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier.background(Background),
        enterTransition  = {
            val toRight = getTabDirection(initialState.destination.route, targetState.destination.route)
            MacroMotion.tabEnter(toRight)
        },
        exitTransition   = {
            val toRight = getTabDirection(initialState.destination.route, targetState.destination.route)
            MacroMotion.tabExit(toRight)
        },
        popEnterTransition = { MacroMotion.subScreenPopEnter },
        popExitTransition  = { MacroMotion.subScreenPopExit },
    ) {
        // ── Onboarding flow ──────────────────────────────────────────────
        composable(
            route          = OnboardingRoutes.WELCOME,
            enterTransition = { EnterTransition.None },
            exitTransition  = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition  = { MacroMotion.subScreenPopExit },
        ) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(OnboardingRoutes.PERMISSIONS) }
            )
        }

        composable(
            route = OnboardingRoutes.PERMISSIONS,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            PermissionsScreen(
                onContinue = {
                    navController.navigate(OnboardingRoutes.TUTORIAL)
                }
            )
        }

        composable(
            route = OnboardingRoutes.TUTORIAL,
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            TutorialScreen(
                onFinish = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(OnboardingRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // ── Main screens ─────────────────────────────────────────────────
        composable(Screen.Home.route) {
            val onNavigateToHealth = remember(navController) {
                { navController.navigate(Screen.Health.route) }
            }
            val onNavigateToStats = remember(navController) {
                { navController.navigate("stats") }
            }
            HomeScreen(
                onNavigateToHealth = onNavigateToHealth,
                onNavigateToStats = onNavigateToStats,
            )
        }

        composable(Screen.Health.route) {
            val onNavigateToCameraScan = remember(navController) {
                { navController.navigate("camera_scan") }
            }
            HealthScreen(onNavigateToCameraScan = onNavigateToCameraScan)
        }

        composable(Screen.AI.route) {
            val onNavigateToCameraScan = remember(navController) {
                { navController.navigate("camera_scan") }
            }
            val onNavigateToHome = remember(navController) {
                { navController.navigate(Screen.Home.route) }
            }
            AIScreen(
                onNavigateToCameraScan = onNavigateToCameraScan,
                onNavigateToHome = onNavigateToHome,
            )
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }

        composable(Screen.Settings.route) {
            val onNavigateToHelp = remember(navController) { { navController.navigate("help") } }
            val onNavigateToStats = remember(navController) { { navController.navigate("stats") } }
            val onNavigateToWidgets = remember(navController) { { navController.navigate("widgets") } }
            val onReplayTutorial = remember(navController) {
                {
                    navController.navigate(OnboardingRoutes.WELCOME) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            }
            SettingsScreen(
                onNavigateToHelp = onNavigateToHelp,
                onNavigateToStats = onNavigateToStats,
                onNavigateToWidgets = onNavigateToWidgets,
                onReplayTutorial = onReplayTutorial,
            )
        }

        // Sub-screens
        composable(
            route = "stats",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            StatsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "help",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            HelpScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "widgets",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            WidgetsScreen(onNavigateBack = onNavigateBack)
        }

        composable(
            route = "camera_scan",
            enterTransition = { MacroMotion.subScreenEnter },
            exitTransition = { MacroMotion.subScreenExit },
            popEnterTransition = { MacroMotion.subScreenPopEnter },
            popExitTransition = { MacroMotion.subScreenPopExit },
        ) {
            val onNavigateBack = remember(navController) { { navController.popBackStack(); Unit } }
            val onNavigateHome = remember(navController) {
                {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            }
            CameraScanScreen(
                onNavigateBack = onNavigateBack,
                onNavigateHome = onNavigateHome,
            )
        }
    }
}
