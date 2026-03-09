package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.TextSecondary

/**
 * Unified expand/collapse bar used at the bottom of every widget.
 *
 * Design: a compact centred pill (accent-tinted background) flanked by
 * two thin lines that meet the pill edges. The chevron rotates 180° on
 * a bouncy spring. The pill brightens slightly on press.
 */
@Composable
fun WidgetExpandBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TextSecondary,
    expandLabel: String = "More",
    collapseLabel: String = "Less",
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chevron_rot",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Pill background: stronger when expanded, brightens on press
    val pillAlpha by animateFloatAsState(
        targetValue = when {
            isPressed  -> 0.22f
            expanded   -> 0.14f
            else       -> 0.09f
        },
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "pill_alpha",
    )
    // Line alpha: present always, brighter when expanded
    val lineAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.22f else 0.14f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "line_alpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Left line
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(
                color = accentColor.copy(alpha = lineAlpha),
                start = Offset(0f, size.height / 2f),
                end   = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Centre pill — the actual tap target
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accentColor.copy(alpha = pillAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = accentColor, bounded = true, radius = 80.dp),
                    onClick = onToggle,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                    label = "bar_label",
                ) { isExpanded ->
                    Text(
                        text = if (isExpanded) collapseLabel else expandLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor.copy(alpha = if (isExpanded) 0.75f else 0.65f),
                        letterSpacing = 0.3.sp,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = if (expanded) 0.80f else 0.65f),
                    modifier = Modifier.size(14.dp).rotate(chevronRotation),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right line
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(
                color = accentColor.copy(alpha = lineAlpha),
                start = Offset(0f, size.height / 2f),
                end   = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
