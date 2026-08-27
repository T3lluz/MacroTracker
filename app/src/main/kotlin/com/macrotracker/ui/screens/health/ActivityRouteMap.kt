package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.macrotracker.data.health.ActivityRoutePoint
import com.macrotracker.data.health.RouteMapViewport
import com.macrotracker.data.health.buildRouteMapViewport
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.roundToInt

private val MapBg = Color(0xFF0B1424)
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
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val aspect = if (sizePx.width > 0 && sizePx.height > 0) {
        sizePx.width.toDouble() / sizePx.height
    } else {
        2.0
    }
    val viewport = remember(points, aspect) { buildRouteMapViewport(points, aspect) }
    val reveal = remember(points) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(points, animate, viewport) {
        if (animate && points.size >= 2) {
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
            .background(MapBg)
            .onSizeChanged { sizePx = it },
        contentAlignment = Alignment.Center,
    ) {
        if (points.size < 2 || viewport == null) {
            Text(
                "No GPS path for this activity",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        } else {
            MapTiles(viewport = viewport)
            RouteOverlay(
                points = points,
                viewport = viewport,
                accent = accent,
                progress = reveal.value,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.22f),
                            0.18f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.38f),
                        ),
                    ),
            )
            Text(
                "© OSM · CARTO",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
            Text(
                "Start",
                color = StartDot,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            Text(
                "Finish",
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

@Composable
private fun MapTiles(viewport: RouteMapViewport) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it },
    ) {
        if (sizePx.width > 0 && sizePx.height > 0) {
            val tilePxW = sizePx.width / viewport.spanX
            val tilePxH = sizePx.height / viewport.spanY
            val tileWdp = with(density) { tilePxW.toFloat().toDp() }
            val tileHdp = with(density) { tilePxH.toFloat().toDp() }
            val nTiles = 1 shl viewport.zoom
            for (ty in viewport.tileY0..viewport.tileY1) {
                if (ty < 0 || ty >= nTiles) continue
                for (tx in viewport.tileX0..viewport.tileX1) {
                    val wrappedX = Math.floorMod(tx, nTiles)
                    val url = tileUrl(viewport.zoom, wrappedX, ty)
                    val left = ((tx - viewport.west) * tilePxW).roundToInt()
                    val top = ((ty - viewport.north) * tilePxH).roundToInt()
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .size(512)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .absoluteOffset { IntOffset(left, top) }
                            .requiredSize(tileWdp, tileHdp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteOverlay(
    points: List<ActivityRoutePoint>,
    viewport: RouteMapViewport,
    accent: Color,
    progress: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(0.dp)) {
        val w = size.width
        val h = size.height
        val count = max(2, (points.size * progress).toInt().coerceAtLeast(2))
        val visible = points.take(count)
        if (visible.size < 2) return@Canvas

        val path = Path()
        val firstX = viewport.fractionX(visible[0].longitude) * w
        val firstY = viewport.fractionY(visible[0].latitude) * h
        path.moveTo(firstX, firstY)
        for (i in 1 until visible.size) {
            path.lineTo(
                viewport.fractionX(visible[i].longitude) * w,
                viewport.fractionY(visible[i].latitude) * h,
            )
        }

        drawPath(
            path,
            color = Color.Black.copy(alpha = 0.45f),
            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path,
            color = accent.copy(alpha = 0.28f),
            style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                listOf(accent.copy(alpha = 0.85f), accent, Color.White.copy(alpha = 0.95f)),
            ),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val start = Offset(firstX, firstY)
        val last = visible.last()
        val end = Offset(
            viewport.fractionX(last.longitude) * w,
            viewport.fractionY(last.latitude) * h,
        )
        drawCircle(StartDot.copy(alpha = 0.28f), radius = 12f, center = start)
        drawCircle(StartDot, radius = 5.5f, center = start)
        drawCircle(Color.White, radius = 2.2f, center = start)
        if (progress > 0.92f) {
            drawCircle(EndDot.copy(alpha = 0.28f), radius = 12f, center = end)
            drawCircle(EndDot, radius = 5.5f, center = end)
            drawCircle(Color.White, radius = 2.2f, center = end)
        }
    }
}

private fun tileUrl(zoom: Int, x: Int, y: Int): String {
    val host = when ((x + y) and 3) {
        0 -> "a"
        1 -> "b"
        2 -> "c"
        else -> "d"
    }
    return "https://$host.basemaps.cartocdn.com/dark_all/$zoom/$x/$y@2x.png"
}
