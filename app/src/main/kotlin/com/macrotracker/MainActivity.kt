package com.macrotracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.macrotracker.data.server.ServerNotifier
import com.macrotracker.ui.screens.MainScreen
import com.macrotracker.ui.theme.DailyDashTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Set when a server notification is tapped. The activity is `singleTask`,
     * so a tap while the app is already open arrives through [onNewIntent]
     * rather than a fresh [onCreate] — both paths funnel through here.
     */
    private val _openServerDashboard = MutableStateFlow(false)
    private val openServerDashboard: StateFlow<Boolean> = _openServerDashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch away from the splash theme before Compose draws its first frame
        setTheme(R.style.Theme_DailyDash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleServerIntent(intent)
        setContent {
            DailyDashTheme {
                MainScreen(
                    openServerDashboard = openServerDashboard,
                    onServerDashboardOpened = { _openServerDashboard.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleServerIntent(intent)
    }

    private fun handleServerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ServerNotifier.EXTRA_OPEN_SERVERS, false) == true) {
            _openServerDashboard.value = true
        }
    }
}
