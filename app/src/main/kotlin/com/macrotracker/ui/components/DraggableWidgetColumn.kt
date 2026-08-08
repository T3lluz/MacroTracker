package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DRAG_CAP = 32

@Stable
class DraggableWidgetListState<T>(
    initialItems: List<T>,
) {
    val workingList: SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(initialItems) }
    var onReorder: (List<T>) -> Unit = {}
    var draggingIndex by mutableIntStateOf(-1)
    /** Index playing the post-release settle animation, or -1. */
    var settlingIndex by mutableIntStateOf(-1)
    /** Finger Y in root coordinates while dragging. */
    var fingerY by mutableFloatStateOf(0f)
    /** Distance from item top to the grab point. */
    var grabOffsetY by mutableFloatStateOf(0f)
    /** Settling translation after release (dragged item only). */
    val releaseAnim = Animatable(0f)

    val tops = FloatArray(DRAG_CAP)
    val heights = FloatArray(DRAG_CAP)

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

    /** Rebuild slot tops from index-0 anchor + per-item heights. */
    fun recomputeTopsFromAnchor() {
        if (workingList.isEmpty()) return
        var y = tops[0]
        for (i in workingList.indices) {
            if (i >= DRAG_CAP) break
            tops[i] = y
            y += heights[i]
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
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    items(
        count = state.workingList.size,
        key = { index -> itemKey(state.workingList[index]) },
    ) { index ->
        val isDragging = state.isDragActive && index == state.draggingIndex
        // animateItem is a LazyItemScope API — skip on the dragged row so it
        // doesn't fight the finger-following graphicsLayer translation.
        val placementMod = if (!isDragging) {
            Modifier.animateItem(
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = MacroMotion.pressSpring<IntOffset>(),
            )
        } else {
            Modifier
        }
        Box(modifier = placementMod) {
            DraggableWidgetItem(
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
                DraggableWidgetItem(
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

/**
 * Long-press drag reorder.
 *
 * Layout slot positions are frozen while a drag is active so `positionInRoot`
 * (which includes `graphicsLayer` translation) cannot feed back into itself.
 * Persistence is committed once on release — not on every mid-drag swap.
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
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val currentIndex by rememberUpdatedState(index)
    val isDragging = state.isDragActive && index == state.draggingIndex
    val isSettling = !state.isDragActive && index == state.settlingIndex

    val naturalTop = if (index < DRAG_CAP) state.tops[index] else 0f
    val translationY = when {
        isDragging -> state.fingerY - state.grabOffsetY - naturalTop
        isSettling -> state.releaseAnim.value
        else -> 0f
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = MacroMotion.pressSpring(),
        label = "scale$index",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.96f else 1f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "alpha$index",
    )
    val elev by animateFloatAsState(
        targetValue = if (isDragging) 10f else 0f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "elev$index",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging || isSettling) 1f else 0f)
            .graphicsLayer {
                this.translationY = translationY
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = if (isDragging || isSettling) elev.dp.toPx() else 0f
            }
            .onGloballyPositioned { coords ->
                if (index >= DRAG_CAP) return@onGloballyPositioned
                state.heights[index] = coords.size.height.toFloat()
                // Freeze slot geometry while dragging — root coords include
                // graphicsLayer translation and would jitter the dragged card.
                if (!state.isDragActive) {
                    state.tops[index] = coords.positionInRoot().y
                }
            }
            .then(
                if (canDrag) {
                    Modifier.pointerInput(itemKey(item)) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset: Offset ->
                                val idx = currentIndex
                                if (idx !in state.workingList.indices) return@detectDragGesturesAfterLongPress
                                scope.launch {
                                    state.releaseAnim.snapTo(0f)
                                    state.settlingIndex = -1
                                }
                                state.draggingIndex = idx
                                state.grabOffsetY = offset.y
                                state.fingerY = state.tops[idx] + offset.y
                                haptics.gestureStart()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                state.fingerY += amount.y
                                handleDragMove(state, haptics)
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
                },
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
    val currentTranslation = if (landing in 0 until DRAG_CAP) {
        state.fingerY - state.grabOffsetY - state.tops[landing]
    } else {
        0f
    }
    state.draggingIndex = -1
    state.commitReorder()
    if (gestureEnd) haptics.gestureEnd()

    if (landing >= 0 && abs(currentTranslation) > 0.5f) {
        state.settlingIndex = landing
        scope.launch {
            state.releaseAnim.snapTo(currentTranslation)
            state.releaseAnim.animateTo(0f, MacroMotion.bouncySpring())
            state.settlingIndex = -1
        }
    } else {
        state.settlingIndex = -1
        scope.launch { state.releaseAnim.snapTo(0f) }
    }
}

private fun <T> handleDragMove(
    state: DraggableWidgetListState<T>,
    haptics: HapticHelper,
) {
    val cur = state.draggingIndex
    if (cur < 0 || cur >= state.workingList.size || cur >= DRAG_CAP) return
    if (state.heights[cur] <= 0f) return

    val draggedTop = state.fingerY - state.grabOffsetY
    val draggedCy = draggedTop + state.heights[cur] / 2f

    val target = state.workingList.indices.firstOrNull { i ->
        if (i == cur || i >= DRAG_CAP) return@firstOrNull false
        val h = state.heights[i]
        if (h <= 0f) return@firstOrNull false
        val mid = state.tops[i] + h / 2f
        (i > cur && draggedCy > mid) || (i < cur && draggedCy < mid)
    } ?: return

    // Adjacent step only — keeps slot geometry stable with uneven heights.
    val step = if (target > cur) cur + 1 else cur - 1
    if (step !in state.workingList.indices) return

    val from = cur
    val to = step

    val moved = state.workingList.removeAt(from)
    state.workingList.add(to, moved)

    val movedH = state.heights[from]
    if (to > from) {
        for (i in from until to) state.heights[i] = state.heights[i + 1]
    } else {
        for (i in from downTo to + 1) state.heights[i] = state.heights[i - 1]
    }
    state.heights[to] = movedH
    state.recomputeTopsFromAnchor()

    state.draggingIndex = to
    haptics.tick()
}
