package com.macrotracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.macrotracker.ui.screens.AIScreen
import com.macrotracker.ui.screens.CameraScanScreen
import com.macrotracker.ui.screens.HistoryScreen
import com.macrotracker.ui.screens.HomeScreen
import com.macrotracker.ui.screens.StatsScreen
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    data object Home : Screen("home", "Log", Icons.Outlined.Home, Icons.Filled.Home)
    data object History : Screen("history", "History", Icons.Outlined.BarChart, Icons.Filled.BarChart)
    data object AI : Screen("ai", "AI", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome)
    data object Stats : Screen("stats", "More", Icons.Outlined.GridView, Icons.Filled.GridView)
    data object CameraScan : Screen("cameraScan", "", Icons.Outlined.Home, Icons.Filled.Home) // not in bottom nav
}

private val BOTTOM_NAV_ITEMS = listOf(Screen.Home, Screen.History, Screen.AI, Screen.Stats)

@Composable
fun MacroTrackerNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in BOTTOM_NAV_ITEMS.map { it.route }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    items = BOTTOM_NAV_ITEMS,
                    currentRoute = currentRoute,
                    onItemClick = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = { fadeIn() + slideInHorizontally { it / 4 } },
                exitTransition = { fadeOut() + slideOutHorizontally { -it / 4 } },
                popEnterTransition = { fadeIn() + slideInHorizontally { -it / 4 } },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 4 } },
            ) {
                composable(
                    route = "${Screen.Home.route}?foodName={foodName}&calories={calories}&protein={protein}",
                    arguments = listOf(
                        navArgument("foodName") { type = NavType.StringType; defaultValue = "" },
                        navArgument("calories") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("protein") { type = NavType.IntType; defaultValue = -1 },
                    ),
                ) { entry ->
                    val foodName = entry.arguments?.getString("foodName")?.takeIf { it.isNotEmpty() }
                    val calories = entry.arguments?.getInt("calories")?.takeIf { it >= 0 }
                    val protein = entry.arguments?.getInt("protein")?.takeIf { it >= 0 }
                    HomeScreen(
                        onNavigateToCameraScan = { navController.navigate(Screen.CameraScan.route) },
                        onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                        scannedFoodName = foodName,
                        scannedCalories = calories,
                        scannedProtein = protein,
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToCameraScan = { navController.navigate(Screen.CameraScan.route) },
                        onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                    )
                }

                composable(Screen.History.route) {
                    HistoryScreen()
                }

                composable(Screen.AI.route) {
                    AIScreen(
                        onNavigateToCameraScan = { navController.navigate(Screen.CameraScan.route) },
                    )
                }

                composable(Screen.Stats.route) {
                    StatsScreen()
                }

                composable(
                    route = Screen.CameraScan.route,
                    enterTransition = { slideInHorizontally { it } },
                    exitTransition = { slideOutHorizontally { it } },
                ) {
                    CameraScanScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onLogFood = { foodName, calories, protein ->
                            navController.navigate("${Screen.Home.route}?foodName=$foodName&calories=$calories&protein=$protein") {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Surface)
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { screen ->
                val isActive = currentRoute == screen.route
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .then(if (isActive) Modifier.background(Primary, RoundedCornerShape(999.dp)) else Modifier)
                        .clickable { if (!isActive) onItemClick(screen) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (isActive) screen.activeIcon else screen.icon,
                            contentDescription = screen.label,
                            tint = if (isActive) Color.White else TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        if (screen.label.isNotEmpty()) {
                            Text(
                                text = screen.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) Color.White else TextSecondary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

