package com.macrotracker.ui.screens

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.viewmodel.AppUpdateViewModel
import com.macrotracker.ui.viewmodel.OnboardingViewModel
import com.macrotracker.ui.viewmodel.SettingsViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

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
        MainScreenScrollScaffold(
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
private fun MainScreenScrollScaffold(
    navController: NavHostController,
    items: List<Screen>,
    startDestination: String,
    onboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
    showSettingsUpdateBadge: Boolean,
) {
    val density = LocalDensity.current
    val navBarHeight = 102.dp
    val navBarHeightPx = with(density) { navBarHeight.toPx() }
    val navigationBarsPaddingPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
    }
    val totalBottomOffsetPx = navBarHeightPx + navigationBarsPaddingPx

    var navBarHidden by remember { mutableStateOf(false) }

    // Only react to scroll the child actually consumed. Using onPreScroll/available
    // made top-edge pulls toggle the nav bar and feel like rubber-banding.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var accumulated = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = consumed.y
                if (dy == 0f) {
                    // Overscroll / unconsumed edge pull — ignore so we don't bounce the bar.
                    if (available.y != 0f) accumulated = 0f
                    return Offset.Zero
                }

                // Reverse direction resets the accumulator so tiny jitters don't flip state.
                if ((accumulated > 0f && dy < 0f) || (accumulated < 0f && dy > 0f)) {
                    accumulated = 0f
                }
                accumulated += dy

                when {
                    accumulated <= -12f -> {
                        navBarHidden = true
                        accumulated = 0f
                    }
                    accumulated >= 12f -> {
                        navBarHidden = false
                        accumulated = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ScrollAwareBottomBar(
                    navBarHidden = navBarHidden,
                    hideDistancePx = totalBottomOffsetPx,
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
}

@Composable
private fun ScrollAwareBottomBar(
    navBarHidden: Boolean,
    hideDistancePx: Float,
    navController: NavHostController,
    items: List<Screen>,
    showSettingsUpdateBadge: Boolean,
) {
    val targetOffsetPx = if (navBarHidden) -hideDistancePx else 0f
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = MacroMotion.navBarTween(),
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
            showSettingsUpdateBadge = showSettingsUpdateBadge,
        )
    }
}
