package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun <T> DraggableWidgetColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    isDraggableItem: (item: T) -> Boolean = { true },
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope   = rememberCoroutineScope()

    val workingList   = remember { mutableStateListOf<T>().also { it.addAll(items) } }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var fingerY       by remember { mutableFloatStateOf(0f) }
    var grabOffsetY   by remember { mutableFloatStateOf(0f) }

    // Sync external list changes while idle
    if (draggingIndex < 0 && workingList.toList() != items) {
        workingList.clear()
        workingList.addAll(items)
    }

    val tops    = remember { FloatArray(32) }
    val heights = remember { FloatArray(32) }

    // settle[i] = translationY offset for slot i when NOT dragging (spring bounce on swap / drop)
    val settle    = remember { Array(32) { Animatable(0f) } }
    val nudgeJobs = remember { arrayOfNulls<Job>(32) }

    Column(modifier = modifier.fillMaxWidth()) {
        workingList.forEachIndexed { index, item ->
            val canDrag = isDraggableItem(item)
            val isDragging = index == draggingIndex
            val naturalTop = tops[index]
            val dragTranslation = if (isDragging) (fingerY - grabOffsetY - naturalTop) else 0f

            val scale by animateFloatAsState(
                targetValue   = if (isDragging) 1.03f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label         = "scale$index",
            )
            val alpha by animateFloatAsState(
                targetValue   = if (isDragging) 0.93f else 1f,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                label         = "alpha$index",
            )
            val elev by animateFloatAsState(
                targetValue   = if (isDragging) 12f else 0f,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                label         = "elev$index",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY    = if (isDragging) dragTranslation else settle[index].value
                        scaleX          = scale
                        scaleY          = scale
                        this.alpha      = alpha
                        shadowElevation = elev.dp.toPx()
                    }
                    .onGloballyPositioned { coords ->
                        if (index < 32) {
                            tops[index]    = coords.positionInParent().y
                            heights[index] = coords.size.height.toFloat()
                        }
                    }
                    .then(
                        if (canDrag) Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    draggingIndex = index
                                    fingerY     = tops[index] + offset.y
                                    grabOffsetY = offset.y
                                    haptics.gestureStart()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    fingerY += amount.y

                                    val cur = draggingIndex
                                    if (cur < 0 || cur >= workingList.size) return@detectDragGesturesAfterLongPress

                                    val draggedTop = fingerY - grabOffsetY
                                    val draggedBot = draggedTop + heights[cur]
                                    val draggedCy  = draggedTop + heights[cur] / 2f

                                // ── Continuous proportional nudge of neighbours ───────────────
                                // Each neighbour slides away smoothly in proportion to how much
                                // the dragged card has overlapped it — gives a fluid "pushing" feel.
                                for (i in workingList.indices) {
                                    if (i == cur) continue
                                    val iTop  = tops[i]
                                    val iH    = heights[i]
                                    val iBot  = iTop + iH

                                    val overlap = when {
                                        i > cur -> (draggedBot - iTop).coerceIn(0f, iH)
                                        else    -> (iBot  - draggedTop).coerceIn(0f, iH)
                                    }
                                    val fraction    = overlap / iH                  // 0..1
                                    val nudgeTarget = if (i > cur) -heights[cur] * fraction
                                                      else          heights[cur] * fraction

                                    nudgeJobs[i]?.cancel()
                                    nudgeJobs[i] = scope.launch {
                                        settle[i].animateTo(
                                            nudgeTarget,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness    = Spring.StiffnessHigh,   // fast follow
                                            ),
                                        )
                                    }
                                }

                                // ── Swap commit at 30 % into the neighbour ────────────────────
                                val target = workingList.indices.firstOrNull { i ->
                                    if (i == cur) return@firstOrNull false
                                    val h         = heights[i]
                                    val iTop      = tops[i]
                                    val threshold = h * 0.30f
                                    (i > cur && draggedCy > iTop + threshold) ||
                                    (i < cur && draggedCy < iTop + h - threshold)
                                }

                                if (target != null) {
                                    val dir = if (target > cur) -1f else 1f

                                    // Spring-bounce the displaced card from its current nudge pos → 0
                                    nudgeJobs[target]?.cancel()
                                    nudgeJobs[target] = scope.launch {
                                        // Snap to the full displacement first so bounce feels crisp
                                        settle[target].snapTo(dir * heights[cur])
                                        settle[target].animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness    = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }

                                    // Reset all other neighbour nudges cleanly
                                    for (i in workingList.indices) {
                                        if (i == cur || i == target) continue
                                        nudgeJobs[i]?.cancel()
                                        nudgeJobs[i] = scope.launch {
                                            settle[i].animateTo(
                                                0f,
                                                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                            )
                                        }
                                    }

                                    workingList.add(target, workingList.removeAt(cur))
                                    draggingIndex = target
                                    haptics.tick()
                                    onReorder(workingList.toList())
                                }
                            },
                            onDragEnd = {
                                val landing = draggingIndex
                                // Settle all nudges to 0 with a spring
                                for (i in workingList.indices) {
                                    nudgeJobs[i]?.cancel()
                                    nudgeJobs[i] = scope.launch {
                                        settle[i].animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                        )
                                    }
                                }
                                // Drop the dragged card with a spring bounce into its slot
                                val currentTranslation = fingerY - grabOffsetY - tops[landing]
                                nudgeJobs[landing]?.cancel()
                                nudgeJobs[landing] = scope.launch {
                                    settle[landing].snapTo(currentTranslation)
                                    settle[landing].animateTo(
                                        0f,
                                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                    )
                                }
                                draggingIndex = -1
                                haptics.gestureEnd()
                            },
                            onDragCancel = {
                                val landing = draggingIndex
                                for (i in workingList.indices) {
                                    nudgeJobs[i]?.cancel()
                                    nudgeJobs[i] = scope.launch {
                                        settle[i].animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                        )
                                    }
                                }
                                val currentTranslation = fingerY - grabOffsetY - tops[landing]
                                nudgeJobs[landing]?.cancel()
                                nudgeJobs[landing] = scope.launch {
                                    settle[landing].snapTo(currentTranslation)
                                    settle[landing].animateTo(
                                        0f,
                                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                    )
                                }
                                draggingIndex = -1
                            },
                        )
                    } else Modifier
                    ),
            ) {
                itemContent(index, item)
            }
        }
    }
}
