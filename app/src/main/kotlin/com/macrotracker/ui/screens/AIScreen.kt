package com.macrotracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.R
import com.macrotracker.data.remote.NutritionEstimate
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.screens.ai.DishSuggestion
import com.macrotracker.ui.screens.ai.refineDraft
import com.macrotracker.ui.screens.ai.suggestionStripTitle
import com.macrotracker.ui.screens.ai.suggestionsForDraft
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.AiViewModel
import com.macrotracker.ui.viewmodel.NutritionChatMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Pill nav = 64dp + 8dp bottom pad; keep a little air above it. */
private val PillNavClearance = 80.dp

private val PortionOptions = listOf(0.5f, 1f, 1.5f, 2f)

/** Scroll far enough that the last (newest) message's bottom sits in view. */
private suspend fun LazyListState.followChatBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    // First jump to the last item, then nudge so tall estimate cards aren't clipped.
    animateScrollToItem(lastIndex)
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val overflow = (lastItem.offset + lastItem.size) - layoutInfo.viewportEndOffset
    if (overflow > 0) {
        animateScrollBy(overflow.toFloat())
    }
}

@Composable
fun AIScreen(
    onNavigateToCameraScan: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loggedCount by viewModel.loggedCount.collectAsState()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    var draft by rememberSaveable { mutableStateOf("") }
    val liveSuggestions = remember(draft) { suggestionsForDraft(draft) }
    val showSuggestions = !loading && liveSuggestions.isNotEmpty()
    var forceFollow by remember { mutableStateOf(true) }

    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val lastIndex = info.totalItemsCount - 1
            if (lastIndex < 0) return@derivedStateOf true
            // Within ~120px of the true bottom of the list.
            lastVisible.index >= lastIndex - 1 &&
                (lastVisible.offset + lastVisible.size) >= (info.viewportEndOffset - 120)
        }
    }

    val lastMessageId = messages.lastOrNull()?.id
    val lastEstimateKey = (messages.lastOrNull() as? NutritionChatMessage.Doctor)
        ?.estimate
        ?.let { "${it.calories}:${it.protein}:${it.confidence}" }

    // Keep the conversation pinned to the newest bubble when the user is following along.
    LaunchedEffect(lastMessageId, loading, lastEstimateKey, showSuggestions) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!(forceFollow || nearBottom)) return@LaunchedEffect
        // Two passes: once for the new bubble, again after tall estimate cards measure.
        delay(16)
        listState.followChatBottom()
        delay(48)
        listState.followChatBottom()
        forceFollow = false
    }

    // Also re-follow when IME opens/closes and changes the available chat height.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (messages.isNotEmpty() && (forceFollow || nearBottom)) {
            delay(16)
            listState.followChatBottom()
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || loading) return
        haptics.click()
        draft = ""
        forceFollow = true
        viewModel.sendFoodQuery(text)
    }

    fun onSuggestion(suggestion: DishSuggestion) {
        haptics.tick()
        if (suggestion.replacesDraft || draft.isBlank()) {
            draft = ""
            forceFollow = true
            viewModel.sendFoodQuery(suggestion.query)
        } else {
            draft = refineDraft(draft, suggestion)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
    ) {
        AiChatHeader(
            loggedCount = loggedCount,
            loading = loading,
            onCameraScan = {
                haptics.click()
                onNavigateToCameraScan()
            },
            onClear = {
                haptics.tick()
                viewModel.clearChat()
                draft = ""
                forceFollow = true
            },
            onCancel = {
                haptics.tick()
                forceFollow = true
                viewModel.cancelEstimate()
            },
            canClear = messages.size > 1 && !loading,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                when (message) {
                    is NutritionChatMessage.Doctor -> DoctorBubble(
                        message = message,
                        onLog = { estimate ->
                            haptics.confirm()
                            forceFollow = true
                            viewModel.logEstimate(message.id, estimate)
                        },
                        onRetry = { query ->
                            haptics.click()
                            forceFollow = true
                            viewModel.retryQuery(query)
                        },
                        onOpenSettings = {
                            haptics.click()
                            onNavigateToAiSettings()
                        },
                    )
                    is NutritionChatMessage.User -> UserBubble(text = message.text)
                    is NutritionChatMessage.Typing -> TypingBubble(
                        onCancel = {
                            haptics.tick()
                            forceFollow = true
                            viewModel.cancelEstimate()
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSuggestions,
            enter = fadeIn(MacroMotion.fadeTween()) + slideInVertically(
                animationSpec = MacroMotion.slideTween(),
                initialOffsetY = { it / 4 },
            ),
            exit = fadeOut(MacroMotion.fadeTween()),
        ) {
            SuggestionStrip(
                title = suggestionStripTitle(draft),
                suggestions = liveSuggestions,
                refining = draft.isNotBlank(),
                onPick = ::onSuggestion,
            )
        }

        ChatComposer(
            value = draft,
            onValueChange = { draft = it },
            onSend = { send() },
            enabled = !loading,
        )
    }
}

@Composable
private fun AiChatHeader(
    loggedCount: Int,
    loading: Boolean,
    onCameraScan: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    canClear: Boolean,
) {
    val motion = rememberInfiniteTransition(label = "headerLife")
    val avocadoY by motion.animateFloat(
        initialValue = -5f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avocadoY",
    )
    val avocadoRot by motion.animateFloat(
        initialValue = -8f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avocadoRot",
    )
    val appleY by motion.animateFloat(
        initialValue = 5f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appleY",
    )
    val saladY by motion.animateFloat(
        initialValue = -3f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "saladY",
    )
    val eggY by motion.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eggY",
    )
    val clankerScale by motion.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clankerScale",
    )

    val status = when {
        loading -> "Crunching macros…"
        loggedCount > 0 -> "Logged $loggedCount this chat"
        else -> "Ask me about any meal"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Primary.copy(alpha = 0.10f),
                        Background,
                    ),
                ),
            )
            .padding(top = 40.dp, start = 16.dp, end = 12.dp, bottom = 8.dp),
    ) {
        // Floating foods — open header, not a card
        Image(
            painter = painterResource(R.drawable.ic_food_avocado),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 86.dp, top = 2.dp)
                .size(30.dp)
                .graphicsLayer {
                    translationY = avocadoY
                    rotationZ = avocadoRot
                },
        )
        Image(
            painter = painterResource(R.drawable.ic_food_apple),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 48.dp, top = 28.dp)
                .size(24.dp)
                .graphicsLayer { translationY = appleY },
        )
        Image(
            painter = painterResource(R.drawable.ic_food_salad),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
                .size(28.dp)
                .graphicsLayer { translationY = saladY },
        )
        Image(
            painter = painterResource(R.drawable.ic_food_egg),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 112.dp, top = 34.dp)
                .size(20.dp)
                .graphicsLayer {
                    translationY = eggY
                    alpha = 0.92f
                },
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Primary.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                                ),
                                radius = size.minDimension * 0.58f,
                            )
                        },
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_clanker),
                        contentDescription = "Clanker",
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = clankerScale
                                scaleY = clankerScale
                            },
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Dr. Clanker",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeaderColor,
                    )
                    Text(
                        status,
                        fontSize = 13.sp,
                        color = if (loading) Primary else TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            ) {
                HeaderAction(
                    icon = Icons.Outlined.CameraAlt,
                    label = "Scan",
                    emphasized = true,
                    onClick = onCameraScan,
                )
                if (loading) {
                    HeaderAction(
                        icon = Icons.Outlined.Close,
                        label = "Cancel",
                        onClick = onCancel,
                    )
                } else if (canClear) {
                    HeaderAction(
                        icon = Icons.Outlined.DeleteSweep,
                        label = "New",
                        onClick = onClear,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (emphasized) Primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (emphasized) Primary.copy(alpha = 0.35f) else Border.copy(alpha = 0.7f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasized) Primary else TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) Primary else TextPrimary,
        )
    }
}

@Composable
private fun SuggestionStrip(
    title: String,
    suggestions: List<DishSuggestion>,
    refining: Boolean,
    onPick: (DishSuggestion) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items(suggestions, key = { it.label }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .background(Surface)
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (suggestion.iconRes != null) {
                        Image(
                            painter = painterResource(suggestion.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (refining) {
                        Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        suggestion.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorBubble(
    message: NutritionChatMessage.Doctor,
    onLog: (NutritionEstimate) -> Unit,
    onRetry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_clanker),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Surface)
                .border(1.dp, Border, CircleShape)
                .padding(2.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (message.isError) Error.copy(alpha = 0.10f) else Surface)
                    .border(
                        1.dp,
                        if (message.isError) Error.copy(alpha = 0.4f) else Border,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    color = if (message.isError) Error else TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            if (message.isError) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    message.retryQuery?.let { query ->
                        SmallActionChip(
                            icon = Icons.Outlined.Refresh,
                            label = "Retry",
                            onClick = { onRetry(query) },
                        )
                    }
                    if (message.showSettingsCta) {
                        SmallActionChip(
                            icon = Icons.Outlined.Settings,
                            label = "AI settings",
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            val estimate = message.estimate
            if (estimate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                EstimateCard(
                    estimate = estimate,
                    logged = message.estimateLogged,
                    onLog = onLog,
                )
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun EstimateCard(
    estimate: NutritionEstimate,
    logged: Boolean,
    onLog: (NutritionEstimate) -> Unit,
) {
    var portion by remember(estimate.foodName, estimate.calories, estimate.protein) {
        mutableFloatStateOf(1f)
    }
    var editMode by remember(estimate.foodName) { mutableStateOf(false) }
    var caloriesText by remember(estimate.calories) { mutableStateOf(estimate.calories.toString()) }
    var proteinText by remember(estimate.protein) { mutableStateOf(estimate.protein.toString()) }

    val baseCalories = caloriesText.toIntOrNull()?.coerceAtLeast(0) ?: estimate.calories
    val baseProtein = proteinText.toIntOrNull()?.coerceAtLeast(0) ?: estimate.protein
    val finalCalories = (baseCalories * portion).roundToInt()
    val finalProtein = (baseProtein * portion).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        Text(
            estimate.foodName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            estimate.servingDescription,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatBox(
                label = "kcal",
                value = finalCalories.toString(),
                modifier = Modifier.weight(1f),
            )
            StatBox(
                label = "protein",
                value = "${finalProtein}g",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ConfidenceBar(confidence = estimate.confidence)

        if (!logged) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Portion",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                PortionOptions.forEach { option ->
                    val selected = portion == option
                    val label = if (option % 1f == 0f) "${option.toInt()}×" else "${option}×"
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Primary else Background)
                            .border(1.dp, if (selected) Primary else Border, RoundedCornerShape(8.dp))
                            .clickable { portion = option }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Text(
                if (editMode) "Hide edit" else "Edit values",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { editMode = !editMode },
            )

            AnimatedVisibility(visible = editMode) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    MacroTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it.filter { c -> c.isDigit() } },
                        placeholder = "Calories",
                        keyboardType = KeyboardType.Number,
                    )
                    MacroTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it.filter { c -> c.isDigit() } },
                        placeholder = "Protein (g)",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            MacroButton(
                text = "Log ${finalCalories} kcal · ${finalProtein}g",
                onClick = {
                    onLog(
                        estimate.copy(
                            calories = finalCalories,
                            protein = finalProtein,
                            servingDescription = if (portion == 1f) {
                                estimate.servingDescription
                            } else {
                                "${estimate.servingDescription} × $portion"
                            },
                        ),
                    )
                },
                variant = ButtonVariant.SECONDARY,
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Logged to today",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Secondary.copy(alpha = 0.12f))
                    .border(1.dp, Secondary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ConfidenceBar(confidence: String) {
    val level = when (confidence.lowercase()) {
        "high" -> 1f
        "low" -> 0.34f
        else -> 0.67f
    }
    val label = when (confidence.lowercase()) {
        "high" -> "High confidence"
        "low" -> "Rough estimate"
        else -> "Medium confidence"
    }
    val color = when (confidence.lowercase()) {
        "high" -> Secondary
        "low" -> Error
        else -> Primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("${(level * 100).roundToInt()}%", fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = Border,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun TypingBubble(onCancel: () -> Unit) {
    val bounce = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_clanker),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Surface)
                .border(1.dp, Border, CircleShape)
                .padding(2.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val y by bounce.animateFloat(
                    initialValue = 0f,
                    targetValue = -4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, delayMillis = index * 100),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { translationY = y }
                        .clip(CircleShape)
                        .background(TextSecondary),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Cancel",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val bottomPad = if (imeOpen) 10.dp else navBottom + PillNavClearance
    val canSend = enabled && value.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = bottomPad)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 4,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Describe a meal…",
                        color = TextSecondary,
                        fontSize = 15.sp,
                    )
                }
                inner()
            },
        )
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (canSend) Primary else Border),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun ResultPill(label: String, value: String, modifier: Modifier = Modifier) {
    StatBox(label = label, value = value, modifier = modifier)
}
