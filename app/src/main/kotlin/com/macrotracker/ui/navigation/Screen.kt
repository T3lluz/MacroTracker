package com.macrotracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Health : Screen("health", "Health", Icons.Default.MonitorHeart)
    object AI : Screen("ai", "AI Scan", Icons.Default.AutoAwesome)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

// Onboarding flow — not part of the bottom-nav bar
object OnboardingRoutes {
    const val WELCOME = "onboarding_welcome"
    const val PERMISSIONS = "onboarding_permissions"
    const val TUTORIAL = "onboarding_tutorial"
}

// Settings category sub-screens
object SettingsRoutes {
    const val CONNECTIONS = "settings_connections"
    const val AI = "settings_ai"
    const val NUTRITION = "settings_nutrition"
    const val ABOUT = "settings_about"
}
