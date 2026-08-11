package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.HealthStats
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MoveRing = Color(0xFFFF2D55)
private val ExerciseRing = Color(0xFF30D158)
private val StepsRing = Color(0xFF0A84FF)
private val SleepAccent = Color(0xFF5C6BC0)
private val RecoveryAccent = Color(0xFF26C6DA)

@Composable
fun HealthActivityHighlights(
    stats: HealthStats?,
    weekInsights: WeekHealthInsights?,
    delayMs: Long = 40L,
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
    val hasActivity = activity.steps > 0 || activity.activeCalories > 0
    val hasWeek = weekInsights != null && weekInsights.activeDays > 0
    if (!hasActivity && !hasWeek) return

    val recovery = remember(stats, weekInsights) {
        val sleepMin = stats?.sleepMinutes ?: 0L
        val sleepScoreProxy = if (sleepMin > 0) {
            ((sleepMin.toDouble() / DEFAULT_SLEEP_GOAL_MINUTES) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            weekInsights?.sleepScore?.takeIf { it > 0 }
        }
        computeRecovery(
            sleepScore = sleepScoreProxy,
            restingHr = stats?.restingHeartRate?.takeIf { it > 0 },
            avgRestingHrWeek = weekInsights?.avgRestingHeartRate?.takeIf { it > 0 },
        )
    }

    MacroCard(delayMs = delayMs) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Highlights",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Lead with sleep hours + resting HR when available
            val sleepMin = stats?.sleepMinutes ?: 0L
            val restingHr = stats?.restingHeartRate?.takeIf { it > 0 }
            if (sleepMin > 0 || restingHr != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sleepMin > 0) {
                        val sleepScore = recovery.sleepScore
                            ?: weekInsights?.sleepScore?.takeIf { it > 0 }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Background, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text("Sleep", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                formatMinutesCompact(sleepMin),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleepAccent,
                            )
                            if (sleepScore != null) {
                                Text(
                                    "Score $sleepScore · ${sleepLabel(sleepScore)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                    if (restingHr != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Background, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text("Resting HR", fontSize = 11.sp, color = TextSecondary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$restingHr",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoveRing,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "bpm",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            weekInsights?.avgRestingHeartRate?.takeIf { it > 0 }?.let { avg ->
                                Text("Week avg $avg", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            if (hasActivity) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActivityRings(
                        moveProgress = activity.moveProgress,
                        exerciseProgress = activity.exerciseProgress,
                        stepsProgress = activity.stepProgress,
                        modifier = Modifier.size(120.dp),
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RingLegendRow(
                            color = MoveRing,
                            label = "Move",
                            value = "${activity.activeCalories.roundToInt()}",
                            unit = "kcal",
                            goal = "${activity.activeCalGoal.roundToInt()}",
                        )
                        RingLegendRow(
                            color = ExerciseRing,
                            label = "Exercise",
                            value = "${activity.exerciseMinutes.roundToInt()}",
                            unit = "min",
                            goal = "${activity.exerciseGoal.roundToInt()}",
                        )
                        RingLegendRow(
                            color = StepsRing,
                            label = "Steps",
                            value = String.format(Locale.US, "%,d", activity.steps),
                            unit = "",
                            goal = String.format(Locale.US, "%,d", activity.stepGoal),
                        )
                    }
                }

                if (activity.distanceKm > 0 || activity.floors > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (activity.distanceKm > 0) {
                            MiniMetric(
                                label = "Distance",
                                value = String.format(Locale.US, "%.1f km", activity.distanceKm),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (activity.floors > 0) {
                            MiniMetric(
                                label = "Floors",
                                value = "${activity.floors.roundToInt()}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (activity.estimatedWalkMinutes > 5) {
                            MiniMetric(
                                label = "Walk time",
                                value = "${activity.estimatedWalkMinutes.roundToInt()} min",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            val sleepAlreadyHero = sleepMin > 0
            if (recovery.restingHr != null || (weekInsights?.readinessScore ?: 0) > 0 ||
                (!sleepAlreadyHero && recovery.sleepScore != null)
            ) {
                Spacer(modifier = Modifier.height(if (hasActivity || sleepMin > 0 || restingHr != null) 18.dp else 14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if ((weekInsights?.readinessScore ?: 0) > 0) {
                        ScoreTile(
                            title = "Readiness",
                            score = weekInsights!!.readinessScore,
                            subtitle = readinessLabel(weekInsights.readinessScore),
                            accent = Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ScoreTile(
                        title = "Recovery",
                        score = recovery.score,
                        subtitle = recovery.label,
                        accent = RecoveryAccent,
                        modifier = Modifier.weight(1f),
                    )
                    if (!sleepAlreadyHero) {
                        val sleepScore = recovery.sleepScore ?: weekInsights?.sleepScore?.takeIf { it > 0 }
                        if (sleepScore != null) {
                            ScoreTile(
                                title = "Sleep",
                                score = sleepScore,
                                subtitle = sleepLabel(sleepScore),
                                accent = SleepAccent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            weekInsights?.let { week ->
                if (week.activeDays > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "This week",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WowTile("Steps", week.stepsWow, Modifier.weight(1f))
                        WowTile("Sleep", week.sleepWow, Modifier.weight(1f), isSleep = true)
                        WowTile("Move", week.activeCalWow, Modifier.weight(1f), isCalories = true)
                    }

                    if (week.stepStreak > 1 || week.bestStepDay != null || week.sleepDebtMinutes > 45) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (week.stepStreak > 1) {
                                HighlightLine("${week.stepStreak}-day step goal streak")
                            }
                            week.bestStepDay?.let { best ->
                                HighlightLine(
                                    "Best day ${best.date.format(DateTimeFormatter.ofPattern("EEE"))} · ${
                                        String.format(Locale.US, "%,d", best.stats.steps)
                                    } steps",
                                )
                            }
                            if (week.sleepDebtMinutes > 45) {
                                val h = week.sleepDebtMinutes / 60
                                val m = week.sleepDebtMinutes % 60
                                HighlightLine("Sleep debt ~${h}h ${m}m vs 8h")
                            } else if (week.avgSleepMinutes >= DEFAULT_SLEEP_GOAL_MINUTES) {
                                HighlightLine("Sleep on target this week")
                            }
                            week.avgPaceMinPerKm?.let { pace ->
                                if (pace in 8.0..20.0) {
                                    HighlightLine("Avg pace ${formatPace(pace)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekScoresStrip(insights: WeekHealthInsights) {
    if (insights.moveScore <= 0 && insights.sleepScore <= 0 && insights.readinessScore <= 0) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (insights.readinessScore > 0) {
            CompactScore("Ready", insights.readinessScore, Primary, Modifier.weight(1f))
        }
        if (insights.moveScore > 0) {
            CompactScore("Move", insights.moveScore, MoveRing, Modifier.weight(1f))
        }
        if (insights.sleepScore > 0) {
            CompactScore("Sleep", insights.sleepScore, SleepAccent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityRings(
    moveProgress: Float,
    exerciseProgress: Float,
    stepsProgress: Float,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(moveProgress, exerciseProgress, stepsProgress) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val gap = 6.dp.toPx()
        val rings = listOf(
            Triple(moveProgress, MoveRing, 0),
            Triple(exerciseProgress, ExerciseRing, 1),
            Triple(stepsProgress, StepsRing, 2),
        )
        rings.forEach { (progress, color, index) ->
            val inset = index * (stroke + gap)
            val diameter = size.minDimension - inset * 2 - stroke
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
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
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f) * reveal.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun RingLegendRow(
    color: Color,
    label: String,
    value: String,
    unit: String,
    goal: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(
                buildString {
                    append(value)
                    if (unit.isNotEmpty()) append(" $unit")
                    append(" / $goal")
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun ScoreTile(
    title: String,
    score: Int,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(title, fontSize = 11.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$score",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        ThinProgress(progress = score / 100f, color = accent)
    }
}

@Composable
private fun CompactScore(
    label: String,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, color = TextSecondary)
        Text("$score", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(6.dp))
        ThinProgress(progress = score / 100f, color = color)
    }
}

@Composable
private fun ThinProgress(progress: Float, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        drawRoundRect(
            color = Border.copy(alpha = 0.55f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}

@Composable
private fun WowTile(
    label: String,
    delta: MetricDelta,
    modifier: Modifier = Modifier,
    isSleep: Boolean = false,
    isCalories: Boolean = false,
) {
    val valueText = when {
        isSleep && delta.current > 0 -> formatMinutesCompact(delta.current.roundToInt().toLong())
        isCalories && delta.current > 0 -> "${delta.current.roundToInt()}"
        delta.current > 0 -> String.format(Locale.US, "%,.0f", delta.current)
        else -> "—"
    }
    val pct = delta.percentChange
    val pctColor = when {
        pct == null -> TextSecondary
        pct >= 0 -> Success
        else -> Error
    }
    Column(
        modifier = modifier
            .background(Background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, color = TextSecondary)
        Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(
            if (pct == null) "vs last week" else "${formatSignedPercent(pct)} vs last",
            fontSize = 10.sp,
            color = pctColor,
        )
    }
}

@Composable
private fun HighlightLine(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun readinessLabel(score: Int): String = when {
    score >= 80 -> "Peak"
    score >= 60 -> "Solid"
    score >= 40 -> "Okay"
    else -> "Low"
}

private fun sleepLabel(score: Int): String = when {
    score >= 85 -> "Excellent"
    score >= 70 -> "Good"
    score >= 55 -> "Fair"
    else -> "Poor"
}

