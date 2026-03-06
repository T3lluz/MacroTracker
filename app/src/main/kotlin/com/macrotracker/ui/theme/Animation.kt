package com.macrotracker.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * DailyDash — single source of truth for every animation spec in the app.
 *
 * Design principle: smooth, critically-damped motion with zero bounce.
 * Each layer animates exactly once — the page transition handles the
 * entrance; individual elements DON'T re-slide / re-scale on top of it.
 * Value-driven animations (progress bars, chart bars) use a smooth
 * spring so they settle cleanly without overshoot.
 */
object MacroMotion {

    // ── Smooth spring (no bounce) ────────────────────────────────────
    // Critically damped = fastest settle with zero overshoot.
    private const val SMOOTH_DAMPING = 1f       // no bounce at all
    private const val SMOOTH_STIFFNESS = 400f   // snappy settle

    /** Smooth spring for value animations (progress bars, chart bars).
     *  No overshoot — the value slides to its target and stops. */
    fun <T> entranceSpring() = spring<T>(
        dampingRatio = SMOOTH_DAMPING,
        stiffness = SMOOTH_STIFFNESS,
    )

    /** Spring for the bottom nav pill — the only element with a subtle bounce. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 350f,
    )

    /** Quick spring for press/release feedback (buttons). */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = 1f,
        stiffness = 600f,
    )

    // ── Tween durations ──────────────────────────────────────────────
    private const val FADE_IN_MS = 200
    private const val FADE_OUT_MS = 150
    private const val SLIDE_MS = 300

    // ── Tab / content-switch transitions (NavHost top-level tabs) ────
    // Pure crossfade — no slide. Cards are already visible; the page
    // transition just swaps them without any positional movement.
    val contentEnter: EnterTransition = fadeIn(tween(FADE_IN_MS))

    val contentExit: ExitTransition = fadeOut(tween(FADE_OUT_MS))

    // ── Expand / collapse transitions (AnimatedVisibility) ───────────
    val expandEnter: EnterTransition =
        expandVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) +
            fadeIn(tween(FADE_IN_MS))

    val expandExit: ExitTransition =
        shrinkVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) +
            fadeOut(tween(FADE_OUT_MS))

    // ── Sub-screen slide transitions (Stats, CameraScan, Help) ───────
    // Clean horizontal slide with decelerate easing. The content rides
    // in with the page — no extra element-level movement.
    val subScreenEnter: EnterTransition =
        fadeIn(tween(FADE_IN_MS)) + slideInHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { it / 4 }

    val subScreenExit: ExitTransition =
        fadeOut(tween(FADE_OUT_MS)) + slideOutHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { -it / 4 }

    val subScreenPopEnter: EnterTransition =
        fadeIn(tween(FADE_IN_MS)) + slideInHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { -it / 4 }

    val subScreenPopExit: ExitTransition =
        fadeOut(tween(FADE_OUT_MS)) + slideOutHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { it / 4 }
}



