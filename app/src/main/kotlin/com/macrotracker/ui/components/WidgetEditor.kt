package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics

data class WidgetConfig(val id: String, val label: String, val isVisible: Boolean, val icon: ImageVector)

fun parseWidgetConfig(configStr: String, defaultOrder: List<Triple<String, String, ImageVector>>): List<WidgetConfig> {
    if (configStr.isBlank()) {
        return defaultOrder.map { WidgetConfig(it.first, it.second, true, it.third) }
    }
    val parts = configStr.split(",").mapNotNull { part ->
        val p = part.split(":")
        if (p.size == 2) p[0] to p[1].toBoolean() else null
    }.toMap()

    val configs = mutableListOf<WidgetConfig>()
    val configStrList = configStr.split(",").map { it.split(":")[0] }
    
    // First add saved ones in their order
    for (id in configStrList) {
        val defaultInfo = defaultOrder.find { it.first == id }
        if (defaultInfo != null) {
            configs.add(WidgetConfig(id, defaultInfo.second, parts[id] ?: true, defaultInfo.third))
        }
    }
    
    // Then add any missing ones (e.g. if new features added)
    for (def in defaultOrder) {
        if (!configs.any { it.id == def.first }) {
            configs.add(WidgetConfig(def.first, def.second, true, def.third))
        }
    }
    return configs
}

fun encodeWidgetConfig(configs: List<WidgetConfig>): String {
    return configs.joinToString(",") { "${it.id}:${it.isVisible}" }
}

@Composable
fun WidgetEditor(
    configs: List<WidgetConfig>,
    onConfigsChanged: (List<WidgetConfig>) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()

    // Drag state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // Heights of each row (in pixels) for hit-testing during drag
    val rowHeights = remember { mutableStateOf(FloatArray(configs.size)) }

    MacroCard(delayMs = 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Edit Layout",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Primary)
            }
        }
        Text(
            "Toggle visibility · hold ☰ and drag to reorder.",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            configs.forEachIndexed { index, config ->
                val isDragging = draggingIndex == index
                val elevation by animateFloatAsState(
                    targetValue = if (isDragging) 8f else 0f,
                    label = "drag_elevation_$index"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (index < rowHeights.value.size) {
                                rowHeights.value[index] = coords.size.height.toFloat()
                            }
                        }
                        .shadow(
                            elevation = elevation.dp,
                            shape = RoundedCornerShape(12.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDragging) Primary.copy(alpha = 0.12f) else Background
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) Primary else TextSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(configs) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffsetY = 0f
                                        haptics.gestureStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        // Calculate which index we've dragged into
                                        val currentDragging = draggingIndex
                                        if (currentDragging < 0) return@detectDragGesturesAfterLongPress

                                        // Estimate target index based on accumulated offset
                                        val heights = rowHeights.value
                                        val itemHeight = if (currentDragging < heights.size) heights[currentDragging] else 0f
                                        if (itemHeight <= 0f) return@detectDragGesturesAfterLongPress

                                        val steps = (dragOffsetY / itemHeight).toInt()
                                        val targetIndex = (currentDragging + steps).coerceIn(0, configs.size - 1)

                                        if (targetIndex != currentDragging) {
                                            val newList = configs.toMutableList()
                                            val item = newList.removeAt(currentDragging)
                                            newList.add(targetIndex, item)
                                            onConfigsChanged(newList)
                                            haptics.tick()
                                            // Reset offset relative to new position
                                            dragOffsetY -= steps * itemHeight
                                            draggingIndex = targetIndex
                                        }
                                    },
                                    onDragEnd = {
                                        haptics.gestureEnd()
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Widget icon in circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = config.icon,
                            contentDescription = config.label,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = config.label,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = config.isVisible,
                        onCheckedChange = { isChecked ->
                            val newList = configs.toMutableList()
                            newList[index] = config.copy(isVisible = isChecked)
                            onConfigsChanged(newList)
                            if (isChecked) haptics.toggleOn() else haptics.toggleOff()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Background,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Background
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}