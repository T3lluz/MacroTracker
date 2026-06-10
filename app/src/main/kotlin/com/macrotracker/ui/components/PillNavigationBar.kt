package com.macrotracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics

@Composable
fun PillNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit
) {
    val haptics = rememberHaptics()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Using a State object directly for the index to avoid recompositions during the sliding animation
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.7f, 
            stiffness = 500f
        ),
        label = "nav_slide"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp, start = 20.dp, end = 20.dp)
            .height(84.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. Static Background Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onSizeChanged { containerSize = it }
                // Lighter background than the app surface + prominent shadow
                .shadow(
                    elevation = 16.dp, 
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1B2436)) // Distinct slightly lighter dark blue
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(28.dp))
        )

        // 2. Floating Indicator (Pill) - Performance Optimized
        if (containerSize.width > 0) {
            val itemWidthPx = containerSize.width.toFloat() / items.size
            val indicatorSize = 72.dp
            val indicatorSizePx = with(density) { indicatorSize.toPx() }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // Reading state inside graphicsLayer lambda prevents recomposition of the whole Box
                            translationX = (animatedIndexState.value * itemWidthPx) + (itemWidthPx / 2) - (indicatorSizePx / 2)
                            translationY = -14.dp.toPx()
                        }
                        .size(indicatorSize)
                        .clip(CircleShape)
                        .background(Primary) // Changed to Primary color
                )
            }
        }

        // 3. Interactive Icons & Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex
                
                // Animating selection progress per item
                val selectionProgressState = animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "selection_fade"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptics.tick()
                            onItemClick(screen)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            // Use state value inside lambda to avoid recomposing the Column every frame
                            translationY = -16.dp.toPx() * selectionProgressState.value
                        }
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label,
                            tint = if (isSelected) Color.White else TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Text(
                            text = screen.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .graphicsLayer {
                                    // Hardware-accelerated alpha transition
                                    alpha = selectionProgressState.value
                                }
                        )
                    }
                }
            }
        }
    }
}
