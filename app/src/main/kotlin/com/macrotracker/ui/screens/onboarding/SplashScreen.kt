package com.macrotracker.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.macrotracker.R
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen splash overlay. Rendered as a plain Box on top of the
 * Scaffold in MainScreen — NOT a nav destination — so it always fills
 * the real window including status-bar and nav-bar with zero inset issues.
 *
 * Call [onFinished] when the overlay has fully dissolved and can be
 * removed from composition.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {

    // ── Core animatables ─────────────────────────────────────────────────────
    val logoScale    = remember { Animatable(0.5f) }
    val logoAlpha    = remember { Animatable(0f) }
    val glowAlpha    = remember { Animatable(0f) }
    val warpScale    = remember { Animatable(1f) }

    // Chromatic aberration — spread offset for R/G/B ghost copies (0 → peak → 0)
    val chromaSpread = remember { Animatable(0f) }
    val chromaAlpha  = remember { Animatable(0f) }

    // Background overlay that seals the screen before we call onFinished
    val bgAlpha      = remember { Animatable(0f) }

    LaunchedEffect(Unit) {

        // ── Phase 1: entrance ────────────────────────────────────────────────
        launch { logoAlpha.animateTo(1f, MacroMotion.Splash.logoFadeIn()) }
        launch { glowAlpha.animateTo(0.55f, MacroMotion.Splash.glowFadeIn()) }
        logoScale.animateTo(1f, MacroMotion.Splash.logoScaleIn())

        // ── Phase 2: glow breathes (2 pulses, then stop) ─────────────────────
        repeat(2) {
            launch { glowAlpha.animateTo(0.85f, MacroMotion.Splash.glowPulse()) }
            delay(MacroMotion.Splash.PULSE_MS.toLong())
            launch { glowAlpha.animateTo(0.35f, MacroMotion.Splash.glowPulse()) }
            delay(MacroMotion.Splash.PULSE_MS.toLong())
        }
        delay(100)

        // ── Phase 3: warp zoom + chromatic aberration dissolve ───────────────
        //
        // Timeline (ms from start of phase 3):
        //   0   – glow fades out, chroma ghosts fade IN, warp begins
        //   200 – chroma spread reaches peak
        //   420 – chroma ghosts fade out
        //   500 – bg overlay fully opaque  ← earliest we can hand off
        //   560 – warp zoom completes
        coroutineScope {
            // Glow off
            launch { glowAlpha.animateTo(0f, MacroMotion.Splash.glowOut()) }

            // Chroma: fade in quickly, spread, then fade away
            launch {
                chromaAlpha.animateTo(0.6f, MacroMotion.Splash.chromaIn())
                coroutineScope {
                    launch { chromaSpread.animateTo(18f, MacroMotion.Splash.chromaSpread()) }
                    delay(180)
                }
                chromaAlpha.animateTo(0f, MacroMotion.Splash.chromaOut())
            }

            // Bg overlay seals the screen — starts slightly after warp begins
            launch { bgAlpha.animateTo(1f, MacroMotion.Splash.backdropSeal()) }

            // Warp zoom — logo rockets to fill screen
            warpScale.animateTo(44f, MacroMotion.Splash.warpZoom())
        }

        onFinished()
    }

    // ── UI — true fillMaxSize, no scaffold insets, drawn above everything ────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {

        // Glow halo
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    alpha  = glowAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f    to Primary.copy(alpha = 0.55f),
                    0.45f to Primary.copy(alpha = 0.15f),
                    1f    to Color.Transparent,
                ),
                radius    = size.minDimension / 2f,
                blendMode = BlendMode.Screen,
            )
        }

        // ── Chromatic aberration ghost copies ────────────────────────────────
        // Three copies of the logo tinted R / G / B, offset in different
        // directions, blended with Plus so they add onto the scene.
        // They only appear for ~400 ms during the warp exit.
        val spread = chromaSpread.value
        val ca     = chromaAlpha.value

        // Red channel — offset top-left
        Image(
            painter      = painterResource(id = R.drawable.ic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter  = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                1f,0f,0f,0f,0f,  0f,0f,0f,0f,0f,  0f,0f,0f,0f,0f,  0f,0f,0f,ca,0f
            ))),
            modifier = Modifier
                .size(130.dp)
                .offset(x = (-spread).dp, y = (-spread * 0.5f).dp)
                .graphicsLayer {
                    alpha  = ca
                    scaleX = logoScale.value * warpScale.value
                    scaleY = logoScale.value * warpScale.value
                }
                .clip(CircleShape),
        )

        // Green channel — offset right
        Image(
            painter      = painterResource(id = R.drawable.ic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter  = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0f,0f,0f,0f,0f,  0f,1f,0f,0f,0f,  0f,0f,0f,0f,0f,  0f,0f,0f,ca,0f
            ))),
            modifier = Modifier
                .size(130.dp)
                .offset(x = spread.dp, y = (-spread * 0.3f).dp)
                .graphicsLayer {
                    alpha  = ca
                    scaleX = logoScale.value * warpScale.value
                    scaleY = logoScale.value * warpScale.value
                }
                .clip(CircleShape),
        )

        // Blue channel — offset bottom
        Image(
            painter      = painterResource(id = R.drawable.ic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter  = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0f,0f,0f,0f,0f,  0f,0f,0f,0f,0f,  0f,0f,1f,0f,0f,  0f,0f,0f,ca,0f
            ))),
            modifier = Modifier
                .size(130.dp)
                .offset(x = (spread * 0.2f).dp, y = spread.dp)
                .graphicsLayer {
                    alpha  = ca
                    scaleX = logoScale.value * warpScale.value
                    scaleY = logoScale.value * warpScale.value
                }
                .clip(CircleShape),
        )

        // Main logo — on top of ghost copies
        Image(
            painter      = painterResource(id = R.drawable.ic_logo),
            contentDescription = "DailyDash",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    alpha  = logoAlpha.value
                    scaleX = logoScale.value * warpScale.value
                    scaleY = logoScale.value * warpScale.value
                }
                .clip(CircleShape),
        )

        // Solid background seal — fades over everything, already covering
        // status-bar + nav-bar since this Box is above the Scaffold.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha.value }
                .background(Background)
        )
    }
}
