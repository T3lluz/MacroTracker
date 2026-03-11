package com.macrotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.macrotracker.ui.screens.MainScreen
import com.macrotracker.ui.theme.DailyDashTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch away from the splash theme before Compose draws its first frame
        setTheme(R.style.Theme_DailyDash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyDashTheme {
                MainScreen()
            }
        }
    }
}

