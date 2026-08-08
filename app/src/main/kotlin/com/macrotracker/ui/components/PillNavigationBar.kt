package com.macrotracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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

private val NavBarShape = RoundedCornerShape(28.dp)
private val IndicatorShape = RoundedCornerShape(22.dp)

/** Frosted-glass fill — translucent layers that read as glass over the dark app surface. */
private val GlassFill = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.06f),
    ),
)
private val GlassBase = Color(0xCC141C2C) // ~80% opaque deep navy
private val GlassBorder = Color.White.copy(alpha = 0.18f)
private val GlassHighlight = Color.White.copy(alpha = 0.10f)

@Composable
fun PillNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    /** Shows a small update bubble on the Settings tab when an update is available. */
    showSettingsUpdateBadge: Boolean = false,
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
            .padding(bottom = 14.dp, start = 20.dp, end = 20.dp)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Frosted glass capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onSizeChanged { containerSize = it }
                .shadow(
                    elevation = 20.dp,
                    shape = NavBarShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(NavBarShape)
                .background(GlassBase)
                .background(GlassFill)
                .border(BorderStroke(1.dp, GlassBorder), NavBarShape),
        ) {
            // Top edge highlight — sells the glass rim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 18.dp)
                    .background(GlassHighlight, RoundedCornerShape(50)),
            )
        }

        // Sliding selected pill — stays inside the bar (no protruding bubble)
        if (containerSize.width > 0 && items.isNotEmpty()) {
            val itemWidthPx = containerSize.width.toFloat() / items.size
            val indicatorWidth = with(density) { (itemWidthPx - 12.dp.toPx()).coerceAtLeast(48.dp.toPx()) }
            val indicatorHeight = 48.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX =
                                (animatedIndex * itemWidthPx) + (itemWidthPx - indicatorWidth) / 2f
                        }
                        .size(width = with(density) { indicatorWidth.toDp() }, height = indicatorHeight)
                        .clip(IndicatorShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Primary.copy(alpha = 0.95f),
                                    Primary.copy(alpha = 0.80f),
                                ),
                            ),
                        )
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                            IndicatorShape,
                        ),
                )
            }
        }

        // Icons & labels
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
                    animationSpec = MacroMotion.entranceSpring(),
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
                        modifier = Modifier.graphicsLayer {
                            val s = 1f + 0.06f * selectionProgress
                            scaleX = s
                            scaleY = s
                        },
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                tint = lerp(
                                    TextSecondary.copy(alpha = 0.75f),
                                    Color.White,
                                    selectionProgress,
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                            if (showSettingsUpdateBadge && screen is Screen.Settings) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 8.dp, y = (-6).dp)
                                        .size(16.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Error)
                                        .border(
                                            BorderStroke(1.5.dp, GlassBase),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SystemUpdate,
                                        contentDescription = "Update available",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp),
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
                                .graphicsLayer {
                                    alpha = selectionProgress
                                    translationY = 4.dp.toPx() * (1f - selectionProgress)
                                },
                        )
                    }
                }
            }
        }
    }
}
