package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.text.DecimalFormat
import kotlin.math.abs

/** Large featured metric — use for sleep hours, resting HR, etc. */
@Composable
fun HealthHeroMetric(
    label: String,
    value: String,
    unit: String? = null,
    subtitle: String? = null,
    accent: Color,
    modifier: Modifier = Modifier,
    percentageChange: Double? = null,
    @DrawableRes iconRes: Int? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!unit.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        if (subtitle != null || percentageChange != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                }
                if (percentageChange != null) {
                    HealthPercentageChange(percentageChange)
                }
            }
        }
    }
}

@Composable
fun HealthStatCard(
    modifier: Modifier = Modifier,
    metricName: String,
    value: String,
    percentageChange: Double?,
    @DrawableRes iconRes: Int,
    color: Color,
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = metricName,
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = metricName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100))
                    },
                    label = "statValue",
                ) { animatedValue ->
                    Text(
                        text = animatedValue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (percentageChange != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    HealthPercentageChange(percentageChange)
                }
            }
        }
    }
}

@Composable
fun HealthPercentageChange(percentage: Double) {
    val isPositive = percentage >= 0
    val color = if (isPositive) Success else Error
    val icon = if (isPositive) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
    val formatter = DecimalFormat("0.0'%'")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPositive) "Increase" else "Decrease",
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatter.format(abs(percentage)),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun HealthMiniStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        if (subtitle != null) {
            Text(subtitle, fontSize = 10.sp, color = tint.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
        }
    }
}
