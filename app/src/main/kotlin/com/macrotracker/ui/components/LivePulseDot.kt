package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.LocalTickersPaused

/** Sizes for [LivePulseDot] so callers pick a name, not a number. */
object LivePulseSpec {
    /** Inside a filter chip or next to a label. */
    val SizeChip: Dp = 7.dp

    /** Badge overlaid on an avatar or thumbnail. */
    val SizeBadge: Dp = 9.dp

    /** How far the halo expands past the core, as a multiple of the core radius. */
    const val HALO_SCALE = 2.4f
}

/**
 * "On air" indicator — a solid core with a halo that expands and fades out,
 * the way a broadcast/record light reads.
 *
 * Shared on purpose: Twitch live channels use it today, and anything else with
 * a live/active state (YouTube premieres, F1 session in progress) should use
 * this rather than growing its own dot. Timing lives in [MacroMotion].
 *
 * The halo is skipped while the list is scrolling ([LocalTickersPaused]) so the
 * pulse never competes with scroll performance.
 */
@Composable
fun LivePulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = LivePulseSpec.SizeChip,
) {
    val paused = LocalTickersPaused.current
    val phase = if (paused) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "livePulse")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = MacroMotion.livePulseSpec(),
            label = "livePulsePhase",
        )
        animated
    }

    // The whole thing is drawn inside `size`; the halo eats into the core so the
    // dot never overflows the row it sits in.
    Canvas(modifier = modifier.size(size)) {
        val maxRadius = this.size.minDimension / 2f
        val coreRadius = maxRadius / LivePulseSpec.HALO_SCALE
        if (phase > 0f) {
            drawCircle(
                color = color.copy(alpha = (1f - phase) * 0.45f),
                radius = coreRadius + (maxRadius - coreRadius) * phase,
            )
        }
        drawCircle(color = color, radius = coreRadius)
    }
}
