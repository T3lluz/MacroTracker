package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.GlassDot
import kotlin.math.ceil

/**
 * Cinema-Info dotted frost: a fine dot grid over frosted glass.
 *
 * Pair with haze (or a translucent fill) — this draws the texture only,
 * not a second blur. Static [drawWithCache] so scrolling stays cheap.
 */
fun Modifier.dottedFrost(
    color: Color = GlassDot,
    cell: Dp = 8.dp,
    core: Dp = 1.85.dp,
): Modifier = drawWithCache {
    val cellPx = cell.toPx().coerceAtLeast(1f)
    val radius = (core.toPx() / 2f).coerceAtLeast(0.4f)
    val cols = ceil(size.width / cellPx).toInt() + 1
    val rows = ceil(size.height / cellPx).toInt() + 1
    val originX = cellPx / 2f
    val originY = cellPx / 2f
    onDrawWithContent {
        drawContent()
        var yIndex = 0
        while (yIndex < rows) {
            val y = originY + yIndex * cellPx
            var xIndex = 0
            while (xIndex < cols) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(originX + xIndex * cellPx, y),
                )
                xIndex++
            }
            yIndex++
        }
    }
}

@Composable
fun DottedFrostOverlay(
    modifier: Modifier = Modifier,
    color: Color = GlassDot,
) {
    Box(modifier = modifier.dottedFrost(color = color))
}

/** Clip-friendly wrapper when the overlay needs to sit above [content]. */
@Composable
fun DottedFrostLayer(
    modifier: Modifier = Modifier,
    color: Color = GlassDot,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        DottedFrostOverlay(modifier = Modifier.matchParentSize(), color = color)
    }
}
