package com.macrotracker.ui.screens.health

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.HealthAccess
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HealthConnectBrand
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

/**
 * What Health Connect is actually sharing, spelled out per data type.
 *
 * A partial grant is otherwise invisible: the refused types simply read as zero,
 * which on screen is indistinguishable from a quiet day, so "Health only shows
 * heart rate" gives no clue that the fix is one toggle away in another app.
 *
 * The primary action opens Health Connect rather than re-requesting in-app: once
 * its sheet has been dismissed a couple of times it stops appearing and the
 * request returns instantly having granted nothing, which is exactly the state
 * this card exists to get out of.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HealthAccessCard(
    access: List<HealthAccess>,
    /** Metrics switched off in DailyDash's own settings — a separate cause, same symptom. */
    disabledInApp: List<String>,
    onOpenHealthConnect: () -> Unit,
    onRequestInApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = access.filter { !it.isGranted }

    // Nothing to report only once we have actually read the grant state. Hiding
    // on an empty report made "no card" mean both "all good" and "never checked",
    // which is exactly the ambiguity this card exists to remove.
    if (access.isEmpty()) return

    if (missing.isEmpty() && disabledInApp.isEmpty()) {
        AllSharedRow(count = access.size, modifier = modifier)
        return
    }

    MacroCard(delayMs = 0, modifier = modifier) {
        Text(
            if (missing.isEmpty()) "Some metrics are switched off" else "Health Connect isn’t sharing everything",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            if (missing.isEmpty()) {
                "Every data type is shared, but some metrics are hidden in DailyDash."
            } else {
                "${missing.size} of ${access.size} data types aren’t shared, so they " +
                    "show up empty here. Turn them on in Health Connect, then pull to refresh."
            },
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (disabledInApp.isNotEmpty()) {
            Text(
                "Switched off in DailyDash → Settings → Connections: " +
                    disabledInApp.joinToString(),
                fontSize = 12.sp,
                color = Error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            access.forEach { entry ->
                val tint = if (entry.isGranted) Success else Error
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Background)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isGranted) Icons.Default.Check else Icons.Default.Clear,
                        contentDescription = if (entry.isGranted) "Shared" else "Not shared",
                        tint = tint,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        entry.label,
                        fontSize = 12.sp,
                        color = if (entry.isGranted) TextSecondary else TextPrimary,
                        fontWeight = if (entry.isGranted) FontWeight.Normal else FontWeight.SemiBold,
                    )
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccessAction(
                    label = "Open Health Connect",
                    emphasised = true,
                    onClick = onOpenHealthConnect,
                )
                AccessAction(
                    label = "Ask again",
                    emphasised = false,
                    onClick = onRequestInApp,
                )
            }
        }
    }
}

/**
 * The everything-is-fine state: one quiet line rather than a full card, so the
 * readout is always on screen without shouting when there is nothing to fix.
 */
@Composable
private fun AllSharedRow(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "Health Connect: all $count data types shared",
            fontSize = 12.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun AccessAction(label: String, emphasised: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (emphasised) HealthConnectBrand.copy(alpha = 0.14f) else Background,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasised) HealthConnectBrand else TextSecondary,
        )
    }
}
