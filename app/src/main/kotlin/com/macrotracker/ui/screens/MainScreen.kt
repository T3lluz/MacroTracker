package com.macrotracker.ui.screens

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.ui.components.AppUpdateDialog
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.navigation.DailyDashNavHost
import com.macrotracker.ui.navigation.OnboardingRoutes
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.screens.onboarding.SplashOverlay
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun MainScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current as ComponentActivity
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
    val splashShown by onboardingViewModel.splashShown.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val openAiApiKey by settingsViewModel.openAiApiKey.collectAsState()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsState()
    val hasAiApiKey = when (aiProvider) {
        AiProvider.GEMINI -> geminiApiKey.isNotBlank()
        AiProvider.OPENAI -> openAiApiKey.isNotBlank()
        AiProvider.OPENROUTER -> openRouterApiKey.isNotBlank()
    }

    val updateState by appUpdateViewModel.state.collectAsState()
    val showUpdateDialog by appUpdateViewModel.showDialog.collectAsState()
    val updateAvailable by appUpdateViewModel.updateAvailable.collectAsState()
    val context = LocalContext.current

    val startDestination = remember(onboardingCompleted) {
        if (onboardingCompleted) Screen.Home.route else OnboardingRoutes.WELCOME
    }

    val navController = rememberNavController()
    val items = remember(hasAiApiKey) {
        if (hasAiApiKey) {
            listOf(Screen.Home, Screen.Health, Screen.AI, Screen.Settings)
        } else {
            listOf(Screen.Home, Screen.Health, Screen.Settings)
        }
    }

    val onOnboardingComplete = remember(onboardingViewModel) {
        { onboardingViewModel.completeOnboarding() }
    }
    val onSplashFinished = remember(onboardingViewModel) {
        { onboardingViewModel.markSplashShown() }
    }

    // After splash (and only once onboarding is done), start listening for GitHub Releases.
    LaunchedEffect(splashShown, onboardingCompleted) {
        if (splashShown && onboardingCompleted) {
            appUpdateViewModel.startListening()
        }
    }

    // Re-check when returning to the foreground.
    DisposableEffect(activity, splashShown, onboardingCompleted) {
        if (!splashShown || !onboardingCompleted) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appUpdateViewModel.checkOnResume()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenScaffold(
            navController = navController,
            items = items,
            startDestination = startDestination,
            onboardingCompleted = onboardingCompleted,
            onOnboardingComplete = onOnboardingComplete,
            showSettingsUpdateBadge = updateAvailable,
        )

        if (!splashShown) {
            SplashOverlay(onFinished = onSplashFinished)
        }

        if (showUpdateDialog &&
            (updateState is AppUpdateUiState.Available ||
                updateState is AppUpdateUiState.Downloading ||
                updateState is AppUpdateUiState.ReadyToInstall)
        ) {
            val needsPermission = !appUpdateViewModel.canInstallPackages()
            AppUpdateDialog(
                state = updateState,
                currentVersionName = appUpdateViewModel.currentVersionName,
                onDismiss = { appUpdateViewModel.dismissDialog(snooze = true) },
                onUpdate = { info ->
                    if (appUpdateViewModel.canInstallPackages()) {
                        appUpdateViewModel.startDownload(info)
                    } else {
                        context.startActivity(appUpdateViewModel.installPermissionSettingsIntent())
                    }
                },
                onInstall = { appUpdateViewModel.installDownloaded() },
                onOpenInstallPermission = {
                    context.startActivity(appUpdateViewModel.installPermissionSettingsIntent())
                },
                needsInstallPermission = needsPermission,
            )
        }
    }
}

@Composable
private fun MainScreenScaffold(
    navController: NavHostController,
    items: List<Screen>,
    startDestination: String,
    onboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
    showSettingsUpdateBadge: Boolean,
) {
    val navHostModifier = remember { Modifier.statusBarsPadding() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isMainTabRoute = items.any { it.route == currentRoute }
    val context = LocalContext.current

    // Predictive back on root tabs: collect progress so the system gesture
    // can animate, then finish only if the gesture completes.
    PredictiveBackHandler(enabled = isMainTabRoute) { progress ->
        try {
            progress.collect { /* system drives the predictive animation */ }
            (context as? Activity)?.finish()
        } catch (_: CancellationException) {
            // Gesture cancelled — stay on the current tab.
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MainBottomBar(
                navController = navController,
                items = items,
                showSettingsUpdateBadge = showSettingsUpdateBadge,
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

@Composable
private fun MainBottomBar(
    navController: NavHostController,
    items: List<Screen>,
    showSettingsUpdateBadge: Boolean,
) {
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

    Box(modifier = Modifier.navigationBarsPadding()) {
        PillNavigationBar(
            items = items,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            showSettingsUpdateBadge = showSettingsUpdateBadge,
        )
    }
}
