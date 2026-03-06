package com.macrotracker.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.macrotracker.ui.screens.HealthScreen
import com.macrotracker.ui.screens.HelpScreen
import com.macrotracker.ui.screens.HistoryScreen
import com.macrotracker.ui.screens.HomeScreen
import com.macrotracker.ui.screens.SettingsScreen
import com.macrotracker.ui.screens.StatsScreen
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import kotlin.math.roundToInt

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Health : Screen("health", "Health", Icons.Outlined.FavoriteBorder, Icons.Filled.FavoriteBorder)
    data object History : Screen("history", "History", Icons.Outlined.BarChart, Icons.Filled.BarChart)
    data object AI : Screen("ai", "AI", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    data object Stats : Screen("stats", "", Icons.Outlined.BarChart, Icons.Filled.BarChart)
    data object CameraScan : Screen("cameraScan", "", Icons.Outlined.Home, Icons.Filled.Home)
    data object Help : Screen("help", "", Icons.Outlined.Home, Icons.Filled.Home)
}

private val BOTTOM_NAV_ITEMS = listOf(Screen.Home, Screen.Health, Screen.History, Screen.AI, Screen.Settings)

@Composable
fun MacroTrackerNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = BOTTOM_NAV_ITEMS.any { screen ->
        navBackStackEntry?.destination?.hierarchy?.any { it.route?.startsWith(screen.route) == true } == true
    }

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
                enterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { (it * 0.12f).toInt() }
                },
                exitTransition = {
                    fadeOut(tween(130)) + slideOutHorizontally(
                        animationSpec = tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { -(it * 0.12f).toInt() }
                },
                popEnterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(
                        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { -(it * 0.12f).toInt() }
                },
                popExitTransition = {
                    fadeOut(tween(130)) + slideOutHorizontally(
                        animationSpec = tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { (it * 0.12f).toInt() }
                },
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToHealth = {
                            navController.navigate(Screen.Health.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToStats = { navController.navigate(Screen.Settings.route) },
                    )
                }

                composable(
                    route = "${Screen.Health.route}?foodName={foodName}&calories={calories}&protein={protein}",
                    arguments = listOf(
                        navArgument("foodName") { type = NavType.StringType; defaultValue = "" },
                        navArgument("calories") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("protein") { type = NavType.IntType; defaultValue = -1 },
                    ),
                ) { entry ->
                    val foodName = entry.arguments?.getString("foodName")?.takeIf { it.isNotEmpty() }
                    val calories = entry.arguments?.getInt("calories")?.takeIf { it >= 0 }
                    val protein = entry.arguments?.getInt("protein")?.takeIf { it >= 0 }
                    HealthScreen(
                        onNavigateToCameraScan = { navController.navigate(Screen.CameraScan.route) },
                        scannedFoodName = foodName,
                        scannedCalories = calories,
                        scannedProtein = protein,
                    )
                }

                composable(Screen.History.route) {
                    HistoryScreen()
                }

                composable(Screen.AI.route) {
                    AIScreen(
                        onNavigateToCameraScan = { navController.navigate(Screen.CameraScan.route) },
                        onNavigateToHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                        onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                    )
                }

                composable(
                    route = Screen.Stats.route,
                    enterTransition = {
                        fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it }
                    },
                    exitTransition = {
                        fadeOut(tween(130)) + slideOutHorizontally(tween(180)) { it }
                    },
                ) {
                    StatsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Screen.CameraScan.route,
                    enterTransition = {
                        fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it }
                    },
                    exitTransition = {
                        fadeOut(tween(130)) + slideOutHorizontally(tween(180)) { it }
                    },
                ) {
                    CameraScanScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateHome = {
                            navController.navigate(Screen.Health.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable(
                    route = Screen.Help.route,
                    enterTransition = {
                        fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it }
                    },
                    exitTransition = {
                        fadeOut(tween(130)) + slideOutHorizontally(tween(180)) { it }
                    },
                ) {
                    HelpScreen(
                        onNavigateBack = { navController.popBackStack() },
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
    val activeIndex = items.indexOfFirst { currentRoute?.startsWith(it.route) == true }.coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navPillX",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Surface)
                .padding(horizontal = 4.dp),
        ) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }

            // Animated sliding pill background
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = (animatedIndex * itemWidthPx).roundToInt(), y = 0) }
                    .width(itemWidth)
                    .height(52.dp)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Primary),
            )

            // Nav items row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, screen ->
                    val isActive = index == activeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (!isActive) onItemClick(screen) },
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
                            if (isActive && screen.label.isNotEmpty()) {
                                Text(
                                    text = screen.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
