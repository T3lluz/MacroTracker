package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Surface
import kotlinx.coroutines.delay

@Composable
fun MacroCard(
    modifier: Modifier = Modifier,
    delayMs: Long = 0,
    borderColor: Color = Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Track whether this card has already animated in. Persists across recompositions
    // so navigating away and back does not re-trigger the entrance fade.
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (hasAnimated) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            if (delayMs > 0) delay(delayMs)
            alpha.animateTo(1f, animationSpec = tween(200))
            hasAnimated = true
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer { this.alpha = alpha.value },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
