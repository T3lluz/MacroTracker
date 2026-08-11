package com.macrotracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.macrotracker.data.twitch.TwitchAuthClient
import com.macrotracker.ui.screens.MainScreen
import com.macrotracker.ui.theme.DailyDashTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var twitchAuthClient: TwitchAuthClient

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch away from the splash theme before Compose draws its first frame
        setTheme(R.style.Theme_DailyDash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        twitchAuthClient.handleRedirectIntent(intent)
        setContent {
            DailyDashTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        twitchAuthClient.handleRedirectIntent(intent)
    }
}
