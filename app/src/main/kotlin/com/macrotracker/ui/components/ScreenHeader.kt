package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.TextSecondary

/**
 * Shared 32.sp bold page title used by Home, Health, Settings, Help, AI, Stats.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (leading != null) Modifier.padding(start = 8.dp) else Modifier),
        ) {
            Text(
                title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderColor,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ScreenHeaderSpacer() {
    Spacer(modifier = Modifier.height(48.dp))
}
