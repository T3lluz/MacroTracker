package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.ActivityRoutePoint
import com.macrotracker.data.health.mercatorY
import com.macrotracker.data.health.routeBounds
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import kotlin.math.max

private val MapBg = Color(0xFF0B1424)
private val MapGrid = Color(0xFF1A2A44)
private val StartDot = Color(0xFF34D399)
private val EndDot = Color(0xFFFB7185)

@Composable
fun ActivityRouteMap(
    points: List<ActivityRoutePoint>,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    animate: Boolean = true,
) {
    val projected = remember(points) { projectRoute(points) }
    val reveal = remember(points) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(points, animate) {
        if (animate && projected.size >= 2) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, MacroMotion.drawTween(900))
        } else {
            reveal.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MapBg),
        contentAlignment = Alignment.Center,
    ) {
        if (projected.size < 2) {
            Text(
                "No GPS path for this activity",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        } else {
            val progress = reveal.value
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val w = size.width
                val h = size.height
                // Soft grid so the card reads as a map, not a blank panel.
                val cols = 6
                val rows = 4
                for (c in 0..cols) {
                    val x = w * c / cols
                    drawLine(MapGrid.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                for (r in 0..rows) {
                    val y = h * r / rows
                    drawLine(MapGrid.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                val count = max(2, (projected.size * progress).toInt().coerceAtLeast(2))
                val visible = projected.take(count)
                if (visible.size < 2) return@Canvas

                val path = Path()
                path.moveTo(visible[0].x * w, visible[0].y * h)
                for (i in 1 until visible.size) {
                    path.lineTo(visible[i].x * w, visible[i].y * h)
                }

                drawPath(
                    path,
                    color = accent.copy(alpha = 0.22f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path,
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.75f), accent, Color.White.copy(alpha = 0.95f)),
                    ),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                val start = Offset(visible.first().x * w, visible.first().y * h)
                val end = Offset(visible.last().x * w, visible.last().y * h)
                drawCircle(StartDot.copy(alpha = 0.28f), radius = 12f, center = start)
                drawCircle(StartDot, radius = 5.5f, center = start)
                drawCircle(Color.White, radius = 2.2f, center = start)
                if (progress > 0.92f) {
                    drawCircle(EndDot.copy(alpha = 0.28f), radius = 12f, center = end)
                    drawCircle(EndDot, radius = 5.5f, center = end)
                    drawCircle(Color.White, radius = 2.2f, center = end)
                }
            }
            Text(
                "S",
                color = StartDot,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            Text(
                "F",
                color = EndDot,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}

private data class ProjectedPoint(val x: Float, val y: Float)

private fun projectRoute(points: List<ActivityRoutePoint>): List<ProjectedPoint> {
    if (points.size < 2) return emptyList()
    val bounds = routeBounds(points) ?: return emptyList()
    var minLat = bounds.minLat
    var maxLat = bounds.maxLat
    var minLng = bounds.minLng
    var maxLng = bounds.maxLng
    val latPad = max((maxLat - minLat) * 0.18, 0.0008)
    val lngPad = max((maxLng - minLng) * 0.18, 0.0008)
    minLat -= latPad
    maxLat += latPad
    minLng -= lngPad
    maxLng += lngPad

    val minY = mercatorY(minLat)
    val maxY = mercatorY(maxLat)
    val spanX = (maxLng - minLng).coerceAtLeast(1e-9)
    val spanY = (maxY - minY).coerceAtLeast(1e-9)

    return points.map { p ->
        val x = ((p.longitude - minLng) / spanX).toFloat().coerceIn(0f, 1f)
        val y = (1.0 - (mercatorY(p.latitude) - minY) / spanY).toFloat().coerceIn(0f, 1f)
        ProjectedPoint(x, y)
    }
}
