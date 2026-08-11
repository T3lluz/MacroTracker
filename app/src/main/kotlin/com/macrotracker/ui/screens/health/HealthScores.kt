package com.macrotracker.ui.screens.health

import com.macrotracker.data.health.HealthStats
import androidx.health.connect.client.records.SleepSessionRecord
import java.util.Locale
import kotlin.math.roundToInt

const val DEFAULT_ACTIVE_CAL_GOAL = 500.0
const val DEFAULT_EXERCISE_MINUTES_GOAL = 30.0
const val DEFAULT_FLOORS_GOAL = 10.0

/** Apple-style Activity progress for today. */
data class TodayActivitySnapshot(
    val steps: Long,
    val stepGoal: Long,
    val stepProgress: Float,
    val activeCalories: Double,
    val activeCalGoal: Double,
    val moveProgress: Float,
    val exerciseMinutes: Double,
    val exerciseGoal: Double,
    val exerciseProgress: Float,
    val distanceKm: Double,
    val floors: Double,
    val estimatedWalkMinutes: Double,
)

/**
 * Sleep quality 0–100 from duration vs goal + stage mix when available.
 * Garmin-ish: reward deep+REM, penalize short sleep / excess awake.
 */
data class SleepNightScore(
    val score: Int,
    val efficiencyPercent: Int?,
    val totalMinutes: Long,
    val deepMinutes: Long,
    val remMinutes: Long,
    val lightMinutes: Long,
    val awakeMinutes: Long,
    val label: String,
)

/** Simple recovery 0–100 from sleep score + resting HR context. */
data class RecoverySnapshot(
    val score: Int,
    val label: String,
    val restingHr: Long?,
    val sleepScore: Int?,
)

data class HeartRateZones(
    val zone1Rest: Int,
    val zone2Easy: Int,
    val zone3Cardio: Int,
    val zone4Hard: Int,
    val zone5Peak: Int,
) {
    val total: Int get() = zone1Rest + zone2Easy + zone3Cardio + zone4Hard + zone5Peak
    fun fraction(zone: Int): Float {
        if (total <= 0) return 0f
        val v = when (zone) {
            1 -> zone1Rest
            2 -> zone2Easy
            3 -> zone3Cardio
            4 -> zone4Hard
            5 -> zone5Peak
            else -> 0
        }
        return v.toFloat() / total
    }
}

fun computeTodayActivity(
    stats: HealthStats?,
    stepsToday: Long?,
    activeCalToday: Double?,
    distanceToday: Double?,
    floorsToday: Double?,
    stepGoal: Long = DEFAULT_STEP_GOAL,
    activeCalGoal: Double = DEFAULT_ACTIVE_CAL_GOAL,
    exerciseGoal: Double = DEFAULT_EXERCISE_MINUTES_GOAL,
): TodayActivitySnapshot {
    val steps = stepsToday ?: stats?.steps ?: 0L
    val active = activeCalToday ?: stats?.activeCaloriesBurned ?: 0.0
    val distance = distanceToday ?: stats?.distance ?: 0.0
    val floors = floorsToday ?: stats?.floorsClimbed ?: 0.0
    // ~120 steps/min casual walk estimate for "exercise minutes"
    val walkMin = if (steps > 0) steps / 120.0 else 0.0
    // Blend active calories into exercise proxy (Garmin-ish intensity minutes)
    val exerciseMin = maxOf(walkMin * 0.35, active / 12.0)

    return TodayActivitySnapshot(
        steps = steps,
        stepGoal = stepGoal,
        stepProgress = (steps.toFloat() / stepGoal).coerceIn(0f, 1.25f),
        activeCalories = active,
        activeCalGoal = activeCalGoal,
        moveProgress = (active / activeCalGoal).toFloat().coerceIn(0f, 1.25f),
        exerciseMinutes = exerciseMin,
        exerciseGoal = exerciseGoal,
        exerciseProgress = (exerciseMin / exerciseGoal).toFloat().coerceIn(0f, 1.25f),
        distanceKm = distance,
        floors = floors,
        estimatedWalkMinutes = walkMin,
    )
}

fun computeSleepNightScore(sessions: List<SleepSessionRecord>): SleepNightScore? {
    if (sessions.isEmpty()) return null
    val stages = sessions.flatMap { it.stages }
    val totalBed = sessions.sumOf {
        java.time.Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0)
    }
    if (totalBed <= 0) return null

    var deep = 0L
    var rem = 0L
    var light = 0L
    var awake = 0L
    stages.forEach { stage ->
        val m = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0)
        when (stage.stage) {
            SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
            SleepSessionRecord.STAGE_TYPE_REM -> rem += m
            SleepSessionRecord.STAGE_TYPE_LIGHT -> light += m
            SleepSessionRecord.STAGE_TYPE_AWAKE -> awake += m
        }
    }
    val asleep = (deep + rem + light).coerceAtLeast(0)
    val efficiency = if (stages.isNotEmpty()) {
        ((asleep.toDouble() / totalBed) * 100).roundToInt().coerceIn(0, 100)
    } else null

    // Duration score vs 8h
    val durationScore = ((asleep.coerceAtLeast(totalBed - awake).toDouble() / DEFAULT_SLEEP_GOAL_MINUTES) * 55.0)
        .coerceIn(0.0, 55.0)
    val stageScore = if (stages.isNotEmpty() && asleep > 0) {
        val deepPct = deep.toDouble() / asleep
        val remPct = rem.toDouble() / asleep
        ((deepPct.coerceIn(0.0, 0.25) / 0.25) * 20.0) +
            ((remPct.coerceIn(0.0, 0.25) / 0.25) * 15.0)
    } else {
        20.0
    }
    val awakePenalty = if (totalBed > 0) ((awake.toDouble() / totalBed) * 20.0).coerceIn(0.0, 20.0) else 0.0
    val score = (durationScore + stageScore + (efficiency?.let { it * 0.10 } ?: 10.0) - awakePenalty)
        .roundToInt()
        .coerceIn(0, 100)

    val label = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 55 -> "Fair"
        else -> "Poor"
    }

    return SleepNightScore(
        score = score,
        efficiencyPercent = efficiency,
        totalMinutes = asleep.coerceAtLeast(totalBed - awake),
        deepMinutes = deep,
        remMinutes = rem,
        lightMinutes = light,
        awakeMinutes = awake,
        label = label,
    )
}

fun computeRecovery(
    sleepScore: Int?,
    restingHr: Long?,
    avgRestingHrWeek: Long?,
): RecoverySnapshot {
    var score = 50
    sleepScore?.let { score = (score * 0.35 + it * 0.65).roundToInt() }
    if (restingHr != null && avgRestingHrWeek != null && avgRestingHrWeek > 0) {
        val delta = restingHr - avgRestingHrWeek
        score += when {
            delta <= -3 -> 12
            delta <= 0 -> 6
            delta <= 5 -> -4
            else -> -12
        }
    } else if (restingHr != null) {
        score += when {
            restingHr <= 55 -> 10
            restingHr <= 65 -> 4
            restingHr <= 75 -> 0
            else -> -8
        }
    }
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 80 -> "Ready"
        score >= 60 -> "Moderate"
        score >= 40 -> "Strained"
        else -> "Rest"
    }
    return RecoverySnapshot(score, label, restingHr, sleepScore)
}

/** 5-zone model relative to max observed / age-free: uses sample max as peak proxy. */
fun computeHeartRateZones(samples: List<Long>): HeartRateZones? {
    if (samples.size < 10) return null
    val peak = samples.max().toDouble().coerceAtLeast(120.0)
    // Zones as % of peak for the day (practical without user age)
    var z1 = 0
    var z2 = 0
    var z3 = 0
    var z4 = 0
    var z5 = 0
    samples.forEach { bpm ->
        val pct = bpm / peak
        when {
            pct < 0.60 -> z1++
            pct < 0.70 -> z2++
            pct < 0.80 -> z3++
            pct < 0.90 -> z4++
            else -> z5++
        }
    }
    return HeartRateZones(z1, z2, z3, z4, z5)
}

fun formatMinutesCompact(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatPace(minPerKm: Double?): String {
    if (minPerKm == null || minPerKm !in 5.0..30.0) return "—"
    val min = minPerKm.toInt()
    val sec = ((minPerKm - min) * 60).roundToInt().coerceIn(0, 59)
    return String.format(Locale.US, "%d:%02d /km", min, sec)
}
