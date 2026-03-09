package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean> = settings.onboardingCompleted

    /**
     * True only until the splash has played once during this process lifetime.
     * Resets to false when the process is killed — so the next cold start
     * (app killed from recents, OOM, device restart) will show the splash again.
     * Navigating within the app or backgrounding/foregrounding does NOT reset it.
     */
    private val _splashShown = MutableStateFlow(false)
    val splashShown: StateFlow<Boolean> = _splashShown.asStateFlow()

    fun markSplashShown() {
        _splashShown.value = true
    }

    fun completeOnboarding() {
        settings.setOnboardingCompleted(true)
    }

    /** Dev/debug helper — resets the flag so onboarding shows again on next launch. */
    fun resetOnboarding() {
        settings.setOnboardingCompleted(false)
    }
}



