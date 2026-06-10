package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused

/**
 * Shared motion primitives for home-screen widgets.
 * Keeps loading/success transitions and expand/collapse behaviour consistent.
 */

/** Crossfades between widget states (loading, success, error) without positional movement. */
@Composable
fun <T> WidgetStateSwitch(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "widgetState",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = tween(200),
        label = label,
        content = content,
    )
}

/** Vertical expand/collapse used by every home widget's extra content section. */
@Composable
fun WidgetExpandSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = MacroMotion.expandEnter,
        exit = MacroMotion.expandExit,
        content = { content() },
    )
}

/** Scroll-aware header chevron shared by expandable home widgets. */
@Composable
fun WidgetExpandChevron(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TextSecondary,
) {
    val scrollIdle = !LocalTickersPaused.current
    val rotation = if (scrollIdle) {
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = MacroMotion.bouncySpring(),
            label = "widget_chevron",
        ).value
    } else if (expanded) {
        180f
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = accentColor.copy(alpha = if (expanded) 0.75f else 0.55f),
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}

/** Standard spacing + [WidgetExpandBar] footer for collapsed/expanded widget sections. */
@Composable
fun WidgetExpandFooter(
    expanded: Boolean,
    onToggle: () -> Unit,
    accentColor: Color,
    expandLabel: String = "More",
    collapseLabel: String = "Show less",
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = Modifier.height(4.dp))
    WidgetExpandBar(
        expanded = expanded,
        onToggle = onToggle,
        accentColor = accentColor,
        expandLabel = expandLabel,
        collapseLabel = collapseLabel,
        modifier = modifier,
    )
}
