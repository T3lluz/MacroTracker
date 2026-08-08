package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

private val NavPillShape = RoundedCornerShape(percent = 50)
private val NavGlassTint = Color(0xFF141C2C)
private val NavHairline = Color.White.copy(alpha = 0.16f)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PillNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    /** Shows a small update bubble on the Settings tab when an update is available. */
    showSettingsUpdateBadge: Boolean = false,
    hazeState: HazeState? = null,
) {
    val haptics = rememberHaptics()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MacroMotion.bouncySpring(),
        label = "nav_slide",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = 8.dp)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onSizeChanged { containerSize = it }
                .shadow(
                    elevation = 18.dp,
                    shape = NavPillShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                )
                .clip(NavPillShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(containerColor = NavGlassTint),
                        ) {
                            blurRadius = 22.dp
                            noiseFactor = 0.06f
                        }
                    } else {
                        Modifier.background(NavGlassTint.copy(alpha = 0.92f))
                    },
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(BorderStroke(1.dp, NavHairline), NavPillShape),
        )

        if (containerSize.width > 0 && items.isNotEmpty()) {
            val itemWidthPx = containerSize.width.toFloat() / items.size
            val indicatorHorizontalInset = with(density) { 6.dp.toPx() }
            val indicatorWidthPx = itemWidthPx - indicatorHorizontalInset * 2f
            val indicatorHeight = 52.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX =
                                animatedIndex * itemWidthPx + indicatorHorizontalInset
                        }
                        .width(with(density) { indicatorWidthPx.toDp() })
                        .height(indicatorHeight)
                        .clip(NavPillShape)
                        .background(Primary.copy(alpha = 0.92f)),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex
                val selectionProgress by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = MacroMotion.bouncySpring(),
                    label = "selection_fade_$index",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.tick()
                            onItemClick(screen)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                tint = androidx.compose.ui.graphics.lerp(
                                    TextSecondary.copy(alpha = 0.75f),
                                    Color.White,
                                    selectionProgress,
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                            if (showSettingsUpdateBadge && screen is Screen.Settings) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 7.dp, y = (-5).dp)
                                        .size(15.dp)
                                        .shadow(3.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Error)
                                        .border(
                                            BorderStroke(1.5.dp, NavGlassTint),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SystemUpdate,
                                        contentDescription = "Update available",
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = screen.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .graphicsLayer { alpha = 0.55f + 0.45f * selectionProgress },
                        )
                    }
                }
            }
        }
    }
}
