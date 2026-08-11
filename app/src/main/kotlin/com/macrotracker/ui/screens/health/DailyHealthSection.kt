package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Ring + accent palette (Apple Fitness–adjacent, tuned for DailyDash dark UI)
private val StepsC = Color(0xFF0A84FF)
private val SleepC = Color(0xFFBF5AF2)
private val MoveC = Color(0xFFFF375F)
private val RestC = Color(0xFFFF6961)
private val HrC = Color(0xFFFF453A)
private val Spo2C = Color(0xFF64D2FF)
private val FloorC = Color(0xFF30D158)
private val EnergyC = Color(0xFFFFD60A)
private val ProteinC = Color(0xFF32D74B)
private val RespC = Color(0xFF70D7FF)

private enum class HeroKind { SLEEP, STEPS, MOVE, RESTING, ENERGY }

/**
 * Daily Health — Whoop/Oura-inspired: one hero number, concentric rings,
 * thin goal bars, then a flat chip strip of whatever else exists today.
 */
@Composable
fun DailyHealthSection(
    stats: HealthStats?,
    weekInsights: WeekHealthInsights?,
    summary: DailySummary?,
    detailedSleep: List<SleepSessionRecord>,
    heartRateBpm: String?,
    restingHrBpm: String?,
    spo2Percent: String?,
    respRate: String?,
    delayMs: Long = 0L,
) {
    val activity = remember(stats) {
        computeTodayActivity(
            stats = stats,
            stepsToday = stats?.steps,
            activeCalToday = stats?.activeCaloriesBurned,
            distanceToday = stats?.distance,
            floorsToday = stats?.floorsClimbed,
        )
    }

    val nightScore = remember(detailedSleep, stats?.sleepMinutes) {
        computeSleepNightScore(detailedSleep)
            ?: stats?.sleepMinutes?.takeIf { it > 0 }?.let { mins ->
                val score = ((mins.toDouble() / DEFAULT_SLEEP_GOAL_MINUTES) * 100.0)
                    .roundToInt().coerceIn(0, 100)
                SleepNightScore(
                    score = score,
                    efficiencyPercent = null,
                    totalMinutes = mins,
                    deepMinutes = 0,
                    remMinutes = 0,
                    lightMinutes = 0,
                    awakeMinutes = 0,
                    label = when {
                        score >= 85 -> "Excellent"
                        score >= 70 -> "Good"
                        score >= 55 -> "Fair"
                        else -> "Poor"
                    },
                )
            }
    }

    val sleepMin = nightScore?.totalMinutes ?: stats?.sleepMinutes ?: 0L
    val sleepProgress = (sleepMin.toFloat() / DEFAULT_SLEEP_GOAL_MINUTES).coerceIn(0f, 1.25f)
    val resting = stats?.restingHeartRate?.takeIf { it > 0 }
        ?: restingHrBpm?.filter { it.isDigit() }?.toLongOrNull()
    val eaten = summary?.totalCalories?.takeIf { it > 0 }
    val protein = summary?.totalProtein?.takeIf { it > 0 }
    val burned = activity.activeCalories.takeIf { it > 0 }?.roundToInt()
    val quiet = activity.steps == 0L && sleepMin == 0L && activity.activeCalories <= 0

    val hasAny = !quiet || resting != null || eaten != null ||
        (!heartRateBpm.isNullOrBlank() && heartRateBpm != "–")
    if (!hasAny) return

    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE · MMM d"))
    }

    val hero = when {
        sleepMin >= (DEFAULT_SLEEP_GOAL_MINUTES * 0.85).toLong() -> HeroKind.SLEEP
        activity.steps >= (activity.stepGoal * 0.4).toLong() -> HeroKind.STEPS
        activity.activeCalories >= activity.activeCalGoal * 0.4 -> HeroKind.MOVE
        quiet && resting != null -> HeroKind.RESTING
        quiet && eaten != null -> HeroKind.ENERGY
        sleepMin > 0 -> HeroKind.SLEEP
        activity.steps > 0 -> HeroKind.STEPS
        resting != null -> HeroKind.RESTING
        eaten != null -> HeroKind.ENERGY
        else -> HeroKind.STEPS
    }

    val rhrDelta = weekInsights?.avgRestingHeartRate?.takeIf { it > 0 }?.let { avg ->
        resting?.let { it - avg }
    }
    val stepsVsWeek = weekInsights?.avgSteps?.takeIf { it > 0 && activity.steps > 0 }?.let {
        ((activity.steps - it).toDouble() / it) * 100.0
    }
    val sleepVsWeek = weekInsights?.avgSleepMinutes?.takeIf { it > 0 && sleepMin > 0 }?.let {
        ((sleepMin - it).toDouble() / it) * 100.0
    }

    val (heroLabel, heroValue, heroUnit, heroSub, heroAccent) = when (hero) {
        HeroKind.SLEEP -> HeroCopy(
            "Sleep",
            if (sleepMin > 0) formatMinutesCompact(sleepMin) else "—",
            null,
            buildString {
                nightScore?.let { append("Score ${it.score} · ${it.label}") }
                sleepVsWeek?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append(signedPct(it))
                    append(" vs week")
                }
                if (isEmpty()) append("Last night")
            },
            SleepC,
        )
        HeroKind.STEPS -> HeroCopy(
            "Steps",
            if (activity.steps > 0) String.format(Locale.US, "%,d", activity.steps) else "0",
            null,
            buildString {
                append("${pct(activity.stepProgress)}% of ${String.format(Locale.US, "%,d", activity.stepGoal)}")
                stepsVsWeek?.let {
                    append("  ·  ")
                    append(signedPct(it))
                    append(" vs week")
                }
            },
            StepsC,
        )
        HeroKind.MOVE -> HeroCopy(
            "Move",
            "${activity.activeCalories.roundToInt()}",
            "kcal",
            "${pct(activity.moveProgress)}% of ${activity.activeCalGoal.roundToInt()} kcal",
            MoveC,
        )
        HeroKind.RESTING -> HeroCopy(
            "Resting HR",
            "${resting ?: "—"}",
            "bpm",
            when {
                rhrDelta != null && rhrDelta != 0L ->
                    "${if (rhrDelta > 0) "+" else ""}$rhrDelta vs week avg"
                weekInsights?.avgRestingHeartRate?.let { it > 0 } == true ->
                    "Week avg ${weekInsights.avgRestingHeartRate}"
                else -> "Latest reading"
            },
            RestC,
        )
        HeroKind.ENERGY -> HeroCopy(
            "Energy",
            when {
                eaten == null -> "—"
                burned != null -> {
                    val net = eaten - burned
                    if (net > 0) "+$net" else "$net"
                }
                else -> "$eaten"
            },
            "kcal",
            buildString {
                if (eaten != null) append("$eaten in")
                if (burned != null) {
                    if (isNotEmpty()) append(" · ")
                    append("$burned out")
                }
            },
            EnergyC,
        )
    }

    val chips = buildList {
        if (hero != HeroKind.RESTING && resting != null) {
            add(Chip("Resting", "$resting bpm", RestC))
        }
        heartRateBpm?.takeIf { it != "–" && it.isNotBlank() }?.let {
            add(Chip("Heart", "$it bpm", HrC))
        }
        nightScore?.takeIf { it.deepMinutes + it.remMinutes > 0 }?.let { s ->
            add(Chip("Deep+REM", formatMinutesCompact(s.deepMinutes + s.remMinutes), SleepC))
        }
        nightScore?.efficiencyPercent?.let { add(Chip("Efficiency", "$it%", SleepC)) }
        if (activity.distanceKm > 0.05) {
            add(Chip("Distance", String.format(Locale.US, "%.1f km", activity.distanceKm), StepsC))
        }
        if (activity.floors > 0) {
            add(Chip("Floors", "${activity.floors.roundToInt()}", FloorC))
        }
        spo2Percent?.takeIf { it != "–" && it.isNotBlank() }?.let { add(Chip("SpO₂", "$it%", Spo2C)) }
        respRate?.takeIf { it != "–" && it.isNotBlank() }?.let { add(Chip("Resp", "$it rpm", RespC)) }
        weekInsights?.stepStreak?.takeIf { it > 1 }?.let { add(Chip("Streak", "$it days", StepsC)) }
        if (hero != HeroKind.ENERGY && eaten != null) {
            val net = burned?.let { eaten - it }
            add(
                Chip(
                    "Energy",
                    when {
                        net == null -> "$eaten kcal"
                        net > 0 -> "+$net kcal"
                        else -> "$net kcal"
                    },
                    EnergyC,
                ),
            )
        }
        if (protein != null) add(Chip("Protein", "${protein}g", ProteinC))
    }

    MacroCard(delayMs = delayMs) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Daily Health",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(dateLabel, fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rings fill the row height; right column shrinks to fit remaining width
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConcentricGoalRings(
                    stepsProgress = activity.stepProgress,
                    sleepProgress = sleepProgress,
                    moveProgress = activity.moveProgress,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            heroLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = heroAccent,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                heroValue,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                lineHeight = 32.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!heroUnit.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    heroUnit,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Text(
                            heroSub,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    GoalBar(
                        label = "Steps",
                        value = if (activity.steps > 0) {
                            String.format(Locale.US, "%,d", activity.steps)
                        } else {
                            "—"
                        },
                        trailing = String.format(Locale.US, "%,d", activity.stepGoal),
                        progress = activity.stepProgress,
                        color = StepsC,
                        compact = true,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    GoalBar(
                        label = "Sleep",
                        value = if (sleepMin > 0) formatMinutesCompact(sleepMin) else "—",
                        trailing = nightScore?.let { "${it.score}" } ?: "8h",
                        progress = sleepProgress,
                        color = SleepC,
                        compact = true,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    GoalBar(
                        label = "Move",
                        value = if (activity.activeCalories > 0) {
                            "${activity.activeCalories.roundToInt()}"
                        } else {
                            "—"
                        },
                        trailing = "${activity.activeCalGoal.roundToInt()}",
                        progress = activity.moveProgress,
                        color = MoveC,
                        compact = true,
                    )
                }
            }

            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                // Dense 2-column metric grid fills remaining width
                chips.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { chip ->
                            MetricCell(chip, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class HeroCopy(
    val label: String,
    val value: String,
    val unit: String?,
    val sub: String,
    val accent: Color,
)

private data class Chip(val label: String, val value: String, val color: Color)

@Composable
private fun GoalBar(
    label: String,
    value: String,
    trailing: String,
    progress: Float,
    color: Color,
    compact: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                "$value / $trailing",
                fontSize = if (compact) 11.sp else 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp),
        ) {
            drawRoundRect(
                color = color.copy(alpha = 0.15f),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f),
            )
            val w = size.width * progress.coerceIn(0f, 1f)
            if (w > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(chip: Chip, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(chip.color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(chip.label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        Text(
            chip.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Concentric Steps (outer) → Sleep → Move (inner). */
@Composable
fun ConcentricGoalRings(
    stepsProgress: Float,
    sleepProgress: Float,
    moveProgress: Float,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(stepsProgress, sleepProgress, moveProgress) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Canvas(modifier = modifier) {
        // Stroke scales with ring size so large rings stay visually balanced
        val stroke = (size.minDimension * 0.095f).coerceIn(9.dp.toPx(), 16.dp.toPx())
        val gap = stroke * 0.42f
        val rings = listOf(
            Triple(stepsProgress, StepsC, 0),
            Triple(sleepProgress, SleepC, 1),
            Triple(moveProgress, MoveC, 2),
        )
        rings.forEach { (progress, color, index) ->
            val inset = stroke / 2f + index * (stroke + gap)
            val diameter = size.minDimension - inset * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = color.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f) * reveal.value
            if (sweep > 0.5f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun pct(progress: Float) = (progress.coerceIn(0f, 1f) * 100).roundToInt()

private fun signedPct(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.0f", value)}%"
}

// Back-compat aliases
@Composable
fun DailyGoalRings(
    outerProgress: Float,
    middleProgress: Float,
    innerProgress: Float,
    outerColor: Color,
    middleColor: Color,
    innerColor: Color,
    modifier: Modifier = Modifier,
) {
    ConcentricGoalRings(outerProgress, middleProgress, innerProgress, modifier)
}

@Composable
fun AppleActivityRings(
    moveProgress: Float,
    exerciseProgress: Float,
    standProgress: Float,
    modifier: Modifier = Modifier,
) {
    ConcentricGoalRings(standProgress, exerciseProgress, moveProgress, modifier)
}
