package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DRAG_CAP = 32

@Stable
class DraggableWidgetListState<T>(
    initialItems: List<T>,
) {
    val workingList: SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(initialItems) }
    var onReorder: (List<T>) -> Unit = {}
    var draggingIndex by mutableIntStateOf(-1)
    var fingerY by mutableFloatStateOf(0f)
    var grabOffsetY by mutableFloatStateOf(0f)

    val tops = FloatArray(DRAG_CAP)
    val heights = FloatArray(DRAG_CAP)
    val settle = Array(DRAG_CAP) { Animatable(0f) }
    val nudgeJobs = arrayOfNulls<Job>(DRAG_CAP)

    val isDragActive: Boolean get() = draggingIndex >= 0

    fun syncExternalItems(items: List<T>) {
        if (draggingIndex < 0 && workingList.toList() != items) {
            workingList.clear()
            workingList.addAll(items)
        }
    }

    fun commitReorder() {
        onReorder(workingList.toList())
    }
}

@Composable
fun <T> rememberDraggableWidgetListState(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
): DraggableWidgetListState<T> {
    val state = remember { DraggableWidgetListState(items) }
    val latestOnReorder by rememberUpdatedState(onReorder)
    state.onReorder = { latestOnReorder(it) }
    LaunchedEffect(items, state.draggingIndex) {
        state.syncExternalItems(items)
    }
    return state
}

fun <T> LazyListScope.draggableWidgetItems(
    state: DraggableWidgetListState<T>,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    scope: CoroutineScope,
    isDraggableItem: (T) -> Boolean = { true },
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    items(
        count = state.workingList.size,
        key = { index -> itemKey(state.workingList[index]) },
    ) { index ->
        draggableWidgetItem(
            state = state,
            index = index,
            item = state.workingList[index],
            itemKey = itemKey,
            haptics = haptics,
            scope = scope,
            canDrag = isDraggableItem(state.workingList[index]),
            itemContent = itemContent,
        )
    }
}

@Composable
fun <T> DraggableWidgetColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemKey: (T) -> Any = { it.hashCode() },
    isDraggableItem: (item: T) -> Boolean = { true },
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberDraggableWidgetListState(items, onReorder)

    Column(modifier = modifier.fillMaxWidth()) {
        state.workingList.forEachIndexed { index, item ->
            key(itemKey(item)) {
                draggableWidgetItem(
                    state = state,
                    index = index,
                    item = item,
                    itemKey = itemKey,
                    haptics = haptics,
                    scope = scope,
                    canDrag = isDraggableItem(item),
                    itemContent = itemContent,
                )
            }
        }
    }
}

@Composable
private fun <T> draggableWidgetItem(
    state: DraggableWidgetListState<T>,
    index: Int,
    item: T,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    scope: CoroutineScope,
    canDrag: Boolean,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    if (state.isDragActive) {
        DraggableWidgetItemActive(
            state = state,
            index = index,
            item = item,
            itemKey = itemKey,
            haptics = haptics,
            scope = scope,
            canDrag = canDrag,
            itemContent = itemContent,
        )
    } else {
        DraggableWidgetItemIdle(
            state = state,
            index = index,
            item = item,
            itemKey = itemKey,
            haptics = haptics,
            scope = scope,
            canDrag = canDrag,
            itemContent = itemContent,
        )
    }
}

@Composable
private fun <T> DraggableWidgetItemIdle(
    state: DraggableWidgetListState<T>,
    index: Int,
    item: T,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    scope: CoroutineScope,
    canDrag: Boolean,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val scrollIdle = !LocalTickersPaused.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (scrollIdle) {
                    Modifier.onGloballyPositioned { coords ->
                        if (index < DRAG_CAP) {
                            state.tops[index] = coords.positionInParent().y
                            state.heights[index] = coords.size.height.toFloat()
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (canDrag && scrollIdle) Modifier.pointerInput(itemKey(item)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            state.draggingIndex = index
                            state.fingerY = state.tops[index] + offset.y
                            state.grabOffsetY = offset.y
                            haptics.gestureStart()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            state.fingerY += amount.y
                            handleDragMove(state, scope, haptics)
                        },
                        onDragEnd = {
                            finishDrag(state, scope, haptics)
                        },
                        onDragCancel = {
                            finishDrag(state, scope, haptics, gestureEnd = false)
                        },
                    )
                } else Modifier,
            ),
    ) {
        itemContent(index, item)
    }
}

@Composable
private fun <T> DraggableWidgetItemActive(
    state: DraggableWidgetListState<T>,
    index: Int,
    item: T,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    scope: CoroutineScope,
    canDrag: Boolean,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val isDragging = state.isDragActive && index == state.draggingIndex
    val naturalTop = state.tops[index]
    val dragTranslation = if (isDragging) (state.fingerY - state.grabOffsetY - naturalTop) else 0f

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale$index",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.93f else 1f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "alpha$index",
    )
    val elev by animateFloatAsState(
        targetValue = if (isDragging) 12f else 0f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "elev$index",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragTranslation
                else if (index < DRAG_CAP) state.settle[index].value else 0f
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = if (state.isDragActive) elev.dp.toPx() else 0f
            }
            .onGloballyPositioned { coords ->
                if (index < DRAG_CAP) {
                    state.tops[index] = coords.positionInParent().y
                    state.heights[index] = coords.size.height.toFloat()
                }
            }
            .then(
                if (canDrag) Modifier.pointerInput(itemKey(item)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            state.draggingIndex = index
                            state.fingerY = state.tops[index] + offset.y
                            state.grabOffsetY = offset.y
                            haptics.gestureStart()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            state.fingerY += amount.y
                            handleDragMove(state, scope, haptics)
                        },
                        onDragEnd = {
                            finishDrag(state, scope, haptics)
                        },
                        onDragCancel = {
                            finishDrag(state, scope, haptics, gestureEnd = false)
                        },
                    )
                } else Modifier
            ),
    ) {
        itemContent(index, item)
    }
}

private fun finishDrag(
    state: DraggableWidgetListState<*>,
    scope: CoroutineScope,
    haptics: HapticHelper,
    gestureEnd: Boolean = true,
) {
    val landing = state.draggingIndex
    for (i in state.workingList.indices) {
        if (i >= DRAG_CAP) continue
        state.nudgeJobs[i]?.cancel()
        state.nudgeJobs[i] = scope.launch {
            state.settle[i].animateTo(
                0f,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            )
        }
    }
    if (landing in 0 until DRAG_CAP) {
        val currentTranslation = state.fingerY - state.grabOffsetY - state.tops[landing]
        state.nudgeJobs[landing]?.cancel()
        state.nudgeJobs[landing] = scope.launch {
            state.settle[landing].snapTo(currentTranslation)
            state.settle[landing].animateTo(
                0f,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            )
        }
    }
    state.draggingIndex = -1
    if (gestureEnd) haptics.gestureEnd()
}

private fun <T> handleDragMove(
    state: DraggableWidgetListState<T>,
    scope: CoroutineScope,
    haptics: HapticHelper,
) {
    val cur = state.draggingIndex
    if (cur < 0 || cur >= state.workingList.size) return

    val draggedTop = state.fingerY - state.grabOffsetY
    val draggedBot = draggedTop + state.heights[cur]
    val draggedCy = draggedTop + state.heights[cur] / 2f

    for (i in state.workingList.indices) {
        if (i == cur || i >= DRAG_CAP) continue
        val iTop = state.tops[i]
        val iH = state.heights[i]
        val iBot = iTop + iH

        val overlap = when {
            i > cur -> (draggedBot - iTop).coerceIn(0f, iH)
            else -> (iBot - draggedTop).coerceIn(0f, iH)
        }
        val fraction = overlap / iH
        val nudgeTarget = if (i > cur) -state.heights[cur] * fraction
        else state.heights[cur] * fraction

        state.nudgeJobs[i]?.cancel()
        state.nudgeJobs[i] = scope.launch {
            state.settle[i].animateTo(
                nudgeTarget,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        }
    }

    val target = state.workingList.indices.firstOrNull { i ->
        if (i == cur) return@firstOrNull false
        val h = state.heights[i]
        val iTop = state.tops[i]
        val threshold = h * 0.30f
        (i > cur && draggedCy > iTop + threshold) ||
            (i < cur && draggedCy < iTop + h - threshold)
    }

    if (target != null) {
        val dir = if (target > cur) -1f else 1f

        state.nudgeJobs[target]?.cancel()
        state.nudgeJobs[target] = scope.launch {
            state.settle[target].snapTo(dir * state.heights[cur])
            state.settle[target].animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }

        for (i in state.workingList.indices) {
            if (i == cur || i == target || i >= DRAG_CAP) continue
            state.nudgeJobs[i]?.cancel()
            state.nudgeJobs[i] = scope.launch {
                state.settle[i].animateTo(
                    0f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                )
            }
        }

        state.workingList.add(target, state.workingList.removeAt(cur))
        state.draggingIndex = target
        haptics.tick()
        state.commitReorder()
    }
}
