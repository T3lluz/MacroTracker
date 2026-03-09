package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean> = settings.onboardingCompleted

    fun completeOnboarding() {
        settings.setOnboardingCompleted(true)
    }

    /** Dev/debug helper — resets the flag so onboarding shows again on next launch. */
    fun resetOnboarding() {
        settings.setOnboardingCompleted(false)
    }
}

