package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DRAG_CAP = 32

@Stable
class DraggableWidgetListState<T>(
    initialItems: List<T>,
) {
    val workingList: SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(initialItems) }
    var onReorder: (List<T>) -> Unit = {}

    /** Layout index of the item being dragged; stable until drop. */
    var draggingIndex by mutableIntStateOf(-1)

    /** Insertion slot the finger currently targets (may jump multiple rows). */
    var targetIndex by mutableIntStateOf(-1)

    var fingerY by mutableFloatStateOf(0f)
    var grabOffsetY by mutableFloatStateOf(0f)

    /** Live layout metrics (root Y / height). */
    val tops = FloatArray(DRAG_CAP)
    val heights = FloatArray(DRAG_CAP)

    /** Frozen metrics captured at drag start so multi-row jumps stay stable. */
    val baseTops = FloatArray(DRAG_CAP)
    val baseHeights = FloatArray(DRAG_CAP)

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

    fun captureBaselines(count: Int) {
        val n = count.coerceAtMost(DRAG_CAP)
        for (i in 0 until n) {
            baseTops[i] = tops[i]
            baseHeights[i] = heights[i]
        }
    }

    /** Stride includes trailing spacing so gaps match Column/LazyColumn rhythm. */
    fun strideFor(index: Int, count: Int): Float {
        if (index < 0 || index >= count || index >= DRAG_CAP) return 0f
        return when {
            index + 1 < count && index + 1 < DRAG_CAP && baseTops[index + 1] > 0f ->
                (baseTops[index + 1] - baseTops[index]).coerceAtLeast(baseHeights[index])
            index > 0 && baseTops[index] > 0f ->
                (baseTops[index] - baseTops[index - 1]).coerceAtLeast(baseHeights[index])
            else -> baseHeights[index]
        }
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
    dragGestureOnItem: Boolean = true,
    itemContent: @Composable (index: Int, item: T, dragModifier: Modifier) -> Unit,
) {
    items(
        count = state.workingList.size,
        key = { index -> itemKey(state.workingList[index]) },
    ) { index ->
        DraggableWidgetItem(
            state = state,
            index = index,
            item = state.workingList[index],
            itemKey = itemKey,
            haptics = haptics,
            scope = scope,
            canDrag = isDraggableItem(state.workingList[index]),
            dragGestureOnItem = dragGestureOnItem,
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
    dragGestureOnItem: Boolean = true,
    itemSpacing: Dp = 0.dp,
    itemContent: @Composable (index: Int, item: T, dragModifier: Modifier) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberDraggableWidgetListState(items, onReorder)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        state.workingList.forEachIndexed { index, item ->
            key(itemKey(item)) {
                DraggableWidgetItem(
                    state = state,
                    index = index,
                    item = item,
                    itemKey = itemKey,
                    haptics = haptics,
                    scope = scope,
                    canDrag = isDraggableItem(item),
                    dragGestureOnItem = dragGestureOnItem,
                    itemContent = itemContent,
                )
            }
        }
    }
}

/**
 * Single item composable for both idle and dragging states.
 *
 * Slot model: the list order stays frozen while dragging; non-dragged rows
 * animate open a gap at [DraggableWidgetListState.targetIndex], and the held
 * row follows the finger. Reorder commits once on drop (supports multi-row jumps).
 */
@Composable
private fun <T> DraggableWidgetItem(
    state: DraggableWidgetListState<T>,
    index: Int,
    item: T,
    itemKey: (T) -> Any,
    haptics: HapticHelper,
    scope: CoroutineScope,
    canDrag: Boolean,
    dragGestureOnItem: Boolean,
    itemContent: @Composable (index: Int, item: T, dragModifier: Modifier) -> Unit,
) {
    val isDragging = state.isDragActive && index == state.draggingIndex
    val naturalTop = if (index < DRAG_CAP) {
        if (state.isDragActive) state.baseTops[index] else state.tops[index]
    } else {
        0f
    }
    val dragTranslation = if (isDragging) state.fingerY - state.grabOffsetY - naturalTop else 0f

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MacroMotion.bouncySpring(),
        label = "scale$index",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.94f else 1f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "alpha$index",
    )
    val elev by animateFloatAsState(
        targetValue = if (isDragging) 14f else 0f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "elev$index",
    )

    val dragModifier = if (canDrag) {
        Modifier.pointerInput(itemKey(item)) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    state.captureBaselines(state.workingList.size)
                    state.draggingIndex = index
                    state.targetIndex = index
                    state.fingerY = state.baseTops[index] + offset.y
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
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) {
                    dragTranslation
                } else if (index < DRAG_CAP && state.isDragActive) {
                    state.settle[index].value
                } else {
                    0f
                }
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = if (state.isDragActive) elev.dp.toPx() else 0f
            }
            .onGloballyPositioned { coords ->
                if (index < DRAG_CAP && !state.isDragActive) {
                    // Root coords work for both Column and LazyColumn item parents.
                    state.tops[index] = coords.positionInRoot().y
                    state.heights[index] = coords.size.height.toFloat()
                }
            }
            .then(if (dragGestureOnItem) dragModifier else Modifier),
    ) {
        itemContent(index, item, if (dragGestureOnItem) Modifier else dragModifier)
    }
}

private fun <T> finishDrag(
    state: DraggableWidgetListState<T>,
    scope: CoroutineScope,
    haptics: HapticHelper,
    gestureEnd: Boolean = true,
) {
    val from = state.draggingIndex
    val to = state.targetIndex
    val count = state.workingList.size

    // Clear drag identity before mutating the list so no row keeps isDragging
    // against a shifted index.
    state.draggingIndex = -1
    state.targetIndex = -1

    for (i in 0 until count.coerceAtMost(DRAG_CAP)) {
        state.nudgeJobs[i]?.cancel()
        state.nudgeJobs[i] = scope.launch {
            state.settle[i].snapTo(0f)
        }
    }

    if (from in 0 until count && to in 0 until count && from != to) {
        val item = state.workingList.removeAt(from)
        state.workingList.add(to, item)
        state.commitReorder()
    }
    if (gestureEnd) haptics.gestureEnd()
}

private fun <T> handleDragMove(
    state: DraggableWidgetListState<T>,
    scope: CoroutineScope,
    haptics: HapticHelper,
) {
    val from = state.draggingIndex
    val count = state.workingList.size
    if (from < 0 || from >= count || from >= DRAG_CAP) return

    val draggedTop = state.fingerY - state.grabOffsetY
    val draggedCy = draggedTop + state.baseHeights[from] / 2f
    val newTarget = resolveTargetIndex(state, from, count, draggedCy)

    if (newTarget != state.targetIndex) {
        state.targetIndex = newTarget
        haptics.tick()
    }

    val to = state.targetIndex
    val stride = state.strideFor(from, count)

    for (i in 0 until count.coerceAtMost(DRAG_CAP)) {
        if (i == from) continue
        val offset = when {
            from < to && i in (from + 1)..to -> -stride
            from > to && i in to until from -> stride
            else -> 0f
        }
        animateSettleTo(state, scope, i, offset)
    }
}

private fun resolveTargetIndex(
    state: DraggableWidgetListState<*>,
    from: Int,
    count: Int,
    draggedCy: Float,
): Int {
    var best = from
    var bestDist = Float.MAX_VALUE
    for (i in 0 until count.coerceAtMost(DRAG_CAP)) {
        val mid = state.baseTops[i] + state.baseHeights[i] / 2f
        val dist = abs(draggedCy - mid)
        if (dist < bestDist) {
            bestDist = dist
            best = i
        }
    }

    // Prefer crossing neighbor midpoints so small jitters don't flip slots.
    if (best == from) return from
    val fromMid = state.baseTops[from] + state.baseHeights[from] / 2f
    val bestMid = state.baseTops[best] + state.baseHeights[best] / 2f
    val crossed =
        (best > from && draggedCy > (fromMid + bestMid) / 2f) ||
            (best < from && draggedCy < (fromMid + bestMid) / 2f)
    return if (crossed) best else from
}

private fun animateSettleTo(
    state: DraggableWidgetListState<*>,
    scope: CoroutineScope,
    index: Int,
    target: Float,
) {
    val anim = state.settle[index]
    if (abs(anim.targetValue - target) < 0.5f && abs(anim.value - target) < 0.5f) return
    state.nudgeJobs[index]?.cancel()
    state.nudgeJobs[index] = scope.launch {
        anim.animateTo(target, MacroMotion.entranceSpring())
    }
}
