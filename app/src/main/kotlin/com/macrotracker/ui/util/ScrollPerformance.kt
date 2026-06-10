package com.macrotracker.ui.util

import androidx.compose.runtime.staticCompositionLocalOf

/** When true, periodic UI tickers (countdowns, relative timestamps) pause updates. */
val LocalTickersPaused = staticCompositionLocalOf { false }
