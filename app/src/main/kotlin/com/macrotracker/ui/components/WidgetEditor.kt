package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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

    for (id in configStrList) {
        val defaultInfo = defaultOrder.find { it.first == id }
        if (defaultInfo != null) {
            configs.add(WidgetConfig(id, defaultInfo.second, parts[id] ?: true, defaultInfo.third))
        }
    }

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

private const val DRAG_HANDLE_INLINE = "dragHandle"

@Composable
fun WidgetEditor(
    configs: List<WidgetConfig>,
    onConfigsChanged: (List<WidgetConfig>) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val currentConfigs by rememberUpdatedState(configs)
    val currentOnConfigsChanged by rememberUpdatedState(onConfigsChanged)

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val releaseAnim = remember { Animatable(0f) }
    var settlingIndex by remember { mutableIntStateOf(-1) }
    val rowHeights = remember { mutableStateOf(FloatArray(0)) }

    // Keep height array sized to the current list.
    if (rowHeights.value.size != configs.size) {
        rowHeights.value = FloatArray(configs.size)
    }

    val hint = buildAnnotatedString {
        append("Toggle visibility · hold ")
        appendInlineContent(DRAG_HANDLE_INLINE, "[drag]")
        append(" and drag to reorder.")
    }
    val inlineContent = mapOf(
        DRAG_HANDLE_INLINE to InlineTextContent(
            Placeholder(
                width = 18.sp,
                height = 18.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        },
    )

    MacroCard(delayMs = 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Edit Layout",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Primary)
            }
        }
        Text(
            text = hint,
            inlineContent = inlineContent,
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            configs.forEachIndexed { index, config ->
                val currentIndex by rememberUpdatedState(index)
                val isDragging = draggingIndex == index
                val isSettling = settlingIndex == index && draggingIndex < 0
                val elevation by animateFloatAsState(
                    targetValue = if (isDragging) 8f else 0f,
                    animationSpec = MacroMotion.entranceSpring(),
                    label = "drag_elevation_$index",
                )
                val translationY = when {
                    isDragging -> dragOffsetY
                    isSettling -> releaseAnim.value
                    else -> 0f
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging || isSettling) 1f else 0f)
                        .graphicsLayer { this.translationY = translationY }
                        .onGloballyPositioned { coords ->
                            val heights = rowHeights.value
                            if (index < heights.size) {
                                heights[index] = coords.size.height.toFloat() + 8f // include spacedBy gap
                            }
                        }
                        .shadow(
                            elevation = elevation.dp,
                            shape = RoundedCornerShape(12.dp),
                            clip = false,
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDragging) Primary.copy(alpha = 0.12f) else Background,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) Primary else TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .pointerInput(config.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val idx = currentIndex
                                        scope.launch {
                                            releaseAnim.snapTo(0f)
                                            settlingIndex = -1
                                        }
                                        draggingIndex = idx
                                        dragOffsetY = 0f
                                        haptics.gestureStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        val from = draggingIndex
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        val heights = rowHeights.value
                                        if (from >= heights.size || heights[from] <= 0f) {
                                            return@detectDragGesturesAfterLongPress
                                        }

                                        val list = currentConfigs
                                        val steps = (dragOffsetY / heights[from]).roundToInt()
                                        if (steps == 0) return@detectDragGesturesAfterLongPress
                                        val step = steps.coerceIn(-1, 1)
                                        val to = (from + step).coerceIn(0, list.size - 1)
                                        if (to == from) return@detectDragGesturesAfterLongPress

                                        val newList = list.toMutableList()
                                        val item = newList.removeAt(from)
                                        newList.add(to, item)
                                        currentOnConfigsChanged(newList)
                                        haptics.tick()
                                        dragOffsetY -= step * heights[from]
                                        draggingIndex = to
                                    },
                                    onDragEnd = {
                                        val landing = draggingIndex
                                        val offset = dragOffsetY
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                        haptics.gestureEnd()
                                        if (landing >= 0 && abs(offset) > 0.5f) {
                                            settlingIndex = landing
                                            scope.launch {
                                                releaseAnim.snapTo(offset)
                                                releaseAnim.animateTo(0f, MacroMotion.bouncySpring())
                                                settlingIndex = -1
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                        settlingIndex = -1
                                    },
                                )
                            },
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = config.icon,
                            contentDescription = config.label,
                            tint = Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = config.label,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
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
                            uncheckedTrackColor = Background,
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
