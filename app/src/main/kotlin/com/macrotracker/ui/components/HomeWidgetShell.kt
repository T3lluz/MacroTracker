package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused

/** Recessed well that sits inside a [MacroCard] without competing with the card chrome. */
private val ScrollBoxSurface = Color(0xFF0C121C)

/** Default cap so expanded hubs stay on-screen instead of stretching the home list. */
val WidgetScrollBoxMaxHeight = 340.dp

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
        animationSpec = MacroMotion.fadeTween(),
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
            animationSpec = MacroMotion.pressSpring(),
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

/**
 * Bounded nested list for long expanded widget content.
 *
 * Shrinks to the content height, caps at [maxHeight], and scrolls inside the
 * home [androidx.compose.foundation.lazy.LazyColumn] instead of stretching it.
 * Edge fades + a thin thumb appear only when there is overflow.
 */
@Composable
fun WidgetScrollBox(
    modifier: Modifier = Modifier,
    maxHeight: Dp = WidgetScrollBoxMaxHeight,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    containerColor: Color = ScrollBoxSurface,
    borderColor: Color = Border.copy(alpha = 0.55f),
    fadeColor: Color = ScrollBoxSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(containerColor)
            .border(0.5.dp, borderColor, shape)
            .drawWithContent {
                drawContent()
                drawScrollChrome(
                    canScrollBackward = scroll.canScrollBackward,
                    canScrollForward = scroll.canScrollForward,
                    scrollValue = scroll.value,
                    scrollMax = scroll.maxValue,
                    fadeColor = fadeColor,
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

private fun ContentDrawScope.drawScrollChrome(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    scrollValue: Int,
    scrollMax: Int,
    fadeColor: Color,
) {
    val fadeH = 28.dp.toPx()
    if (canScrollBackward) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(fadeColor, fadeColor.copy(alpha = 0f)),
                startY = 0f,
                endY = fadeH,
            ),
            size = Size(size.width, fadeH),
        )
    }
    if (canScrollForward) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                startY = size.height - fadeH,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fadeH),
            size = Size(size.width, fadeH),
        )
    }
    if (canScrollBackward || canScrollForward) {
        val inset = 6.dp.toPx()
        val trackH = (size.height - inset * 2).coerceAtLeast(1f)
        val thumbH = if (scrollMax > 0) {
            (size.height / (size.height + scrollMax) * trackH).coerceIn(16.dp.toPx(), trackH)
        } else {
            trackH
        }
        val travel = (trackH - thumbH).coerceAtLeast(0f)
        val y = inset + if (scrollMax > 0) {
            (scrollValue.toFloat() / scrollMax).coerceIn(0f, 1f) * travel
        } else {
            0f
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(size.width - 5.dp.toPx(), y),
            size = Size(2.5.dp.toPx(), thumbH),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}
