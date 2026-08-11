package com.macrotracker.ui.screens.health

import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.local.DailySummary
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Default daily step target used for progress / streak calculations. */
const val DEFAULT_STEP_GOAL = 10_000L
const val DEFAULT_SLEEP_GOAL_MINUTES = 8 * 60L

data class MetricDelta(
    val current: Double,
    val previous: Double,
    val percentChange: Double?,
) {
    val hasPrevious: Boolean get() = previous > 0
}

data class WeekHealthInsights(
    val totalSteps: Long,
    val avgSteps: Long,
    val stepGoalDays: Int,
    val stepStreak: Int,
    val stepGoalProgress: Float,
    val totalActiveCalories: Double,
    val avgActiveCalories: Double,
    val totalDistanceKm: Double,
    val avgPaceMinPerKm: Double?,
    val avgSleepMinutes: Long,
    val sleepConsistencyHours: Double?,
    val sleepDebtMinutes: Long,
    val avgHeartRate: Long,
    val avgRestingHeartRate: Long,
    val avgSpo2: Double,
    val avgRespiratoryRate: Double,
    val bestStepDay: DailyHealthStats?,
    val bestSleepDay: DailyHealthStats?,
    val activeDays: Int,
    /** 0–100 Garmin-style weekly move / training load proxy. */
    val moveScore: Int,
    /** 0–100 weekly sleep quality from duration + consistency. */
    val sleepScore: Int,
    /** 0–100 blend of move + sleep + resting HR. */
    val readinessScore: Int,
    val stepsWow: MetricDelta,
    val sleepWow: MetricDelta,
    val activeCalWow: MetricDelta,
    val distanceWow: MetricDelta,
) {
    val insightLines: List<String>
        get() = buildList {
            if (stepStreak > 1) {
                add("$stepStreak-day step-goal streak")
            } else if (stepGoalDays > 0) {
                add("Hit step goal on $stepGoalDays day${if (stepGoalDays == 1) "" else "s"}")
            }
            if (sleepDebtMinutes > 45) {
                val h = sleepDebtMinutes / 60
                val m = sleepDebtMinutes % 60
                add("Sleep debt ~${h}h ${m}m vs 8h target")
            } else if (avgSleepMinutes >= DEFAULT_SLEEP_GOAL_MINUTES) {
                add("Sleep on target this week")
            }
            sleepConsistencyHours?.let { sd ->
                if (sd <= 0.75) add("Sleep timing looks consistent")
                else if (sd >= 1.5) add("Sleep length varies a lot day-to-day")
            }
            avgPaceMinPerKm?.let { pace ->
                if (pace in 8.0..20.0) {
                    val min = pace.toInt()
                    val sec = ((pace - min) * 60).roundToInt()
                    add("Avg moving pace ~$min:${String.format(Locale.US, "%02d", sec)} /km")
                }
            }
            stepsWow.percentChange?.let { pct ->
                val dir = if (pct >= 0) "up" else "down"
                add("Steps $dir ${String.format(Locale.US, "%.0f", abs(pct))}% vs prior week")
            }
            if (avgRestingHeartRate > 0) {
                add("Avg resting HR ${avgRestingHeartRate} bpm")
            }
            if (isEmpty() && totalSteps > 0) {
                add("Keep logging — trends get sharper with more days")
            }
        }
}

fun computeWeekInsights(
    current: List<DailyHealthStats>,
    previous: List<DailyHealthStats> = emptyList(),
    stepGoal: Long = DEFAULT_STEP_GOAL,
    sleepGoalMinutes: Long = DEFAULT_SLEEP_GOAL_MINUTES,
): WeekHealthInsights {
    val stepDays = current.filter { it.stats.steps > 0 }
    val sleepDays = current.filter { it.stats.sleepMinutes > 0 }
    val hrDays = current.filter { it.stats.avgHeartRate > 0 }
    val rhrDays = current.filter { it.stats.restingHeartRate > 0 }
    val spo2Days = current.filter { it.stats.oxygenSaturation > 0 }
    val respDays = current.filter { it.stats.respiratoryRate > 0 }
    val activeCalDays = current.filter { it.stats.activeCaloriesBurned > 0 }
    val distanceDays = current.filter { it.stats.distance > 0 }

    val totalSteps = current.sumOf { it.stats.steps }
    val totalActive = current.sumOf { it.stats.activeCaloriesBurned }
    val totalDistance = current.sumOf { it.stats.distance }
    val avgSleep = if (sleepDays.isNotEmpty()) sleepDays.sumOf { it.stats.sleepMinutes } / sleepDays.size else 0L

    val stepGoalDays = current.count { it.stats.steps >= stepGoal }
    val stepStreak = computeTrailingStepStreak(current, stepGoal)

    // Rough pace from distance + estimated walking time (~0.8 m/step → minutes).
    val avgPace = if (totalDistance > 0.3 && totalSteps > 500) {
        val estimatedMinutes = totalSteps * 0.8 / 80.0 // ~80 m/min casual walk
        estimatedMinutes / totalDistance
    } else null

    val sleepHours = sleepDays.map { it.stats.sleepMinutes / 60.0 }
    val sleepSd = if (sleepHours.size >= 3) stdDev(sleepHours) else null
    val sleepDebt = sleepDays.sumOf { (sleepGoalMinutes - it.stats.sleepMinutes).coerceAtLeast(0) }

    fun avgOrZero(list: List<DailyHealthStats>, selector: (DailyHealthStats) -> Double): Double =
        if (list.isEmpty()) 0.0 else list.sumOf(selector) / list.size

    val prevSteps = previous.sumOf { it.stats.steps }.toDouble()
    val prevSleep = previous.filter { it.stats.sleepMinutes > 0 }
        .let { if (it.isEmpty()) 0.0 else it.sumOf { d -> d.stats.sleepMinutes }.toDouble() / it.size }
    val prevActive = previous.sumOf { it.stats.activeCaloriesBurned }
    val prevDistance = previous.sumOf { it.stats.distance }

    val avgStepsDay = if (stepDays.isNotEmpty()) totalSteps / stepDays.size else 0L
    val avgRhr = avgOrZero(rhrDays) { it.stats.restingHeartRate.toDouble() }.toLong()
    val moveScore = computeWeekMoveScore(
        avgSteps = avgStepsDay,
        stepGoal = stepGoal,
        stepGoalDays = stepGoalDays,
        avgActiveCalories = avgOrZero(activeCalDays) { it.stats.activeCaloriesBurned },
        activeDays = stepDays.size.coerceAtLeast(activeCalDays.size),
    )
    val sleepScore = computeWeekSleepScore(
        avgSleepMinutes = avgSleep,
        sleepGoalMinutes = sleepGoalMinutes,
        sleepConsistencyHours = sleepSd,
        sleepDebtMinutes = sleepDebt,
        sleepDays = sleepDays.size,
    )
    val readinessScore = computeWeekReadiness(moveScore, sleepScore, avgRhr)

    return WeekHealthInsights(
        totalSteps = totalSteps,
        avgSteps = avgStepsDay,
        stepGoalDays = stepGoalDays,
        stepStreak = stepStreak,
        stepGoalProgress = (totalSteps.toFloat() / (stepGoal * 7f)).coerceIn(0f, 1.5f),
        totalActiveCalories = totalActive,
        avgActiveCalories = avgOrZero(activeCalDays) { it.stats.activeCaloriesBurned },
        totalDistanceKm = totalDistance,
        avgPaceMinPerKm = avgPace,
        avgSleepMinutes = avgSleep,
        sleepConsistencyHours = sleepSd,
        sleepDebtMinutes = sleepDebt,
        avgHeartRate = avgOrZero(hrDays) { it.stats.avgHeartRate.toDouble() }.toLong(),
        avgRestingHeartRate = avgRhr,
        avgSpo2 = avgOrZero(spo2Days) { it.stats.oxygenSaturation },
        avgRespiratoryRate = avgOrZero(respDays) { it.stats.respiratoryRate },
        bestStepDay = current.maxByOrNull { it.stats.steps }?.takeIf { it.stats.steps > 0 },
        bestSleepDay = current.maxByOrNull { it.stats.sleepMinutes }?.takeIf { it.stats.sleepMinutes > 0 },
        activeDays = current.count {
            it.stats.steps > 0 || it.stats.activeCaloriesBurned > 0 || it.stats.sleepMinutes > 0
        },
        moveScore = moveScore,
        sleepScore = sleepScore,
        readinessScore = readinessScore,
        stepsWow = MetricDelta(totalSteps.toDouble(), prevSteps, percentDelta(totalSteps.toDouble(), prevSteps)),
        sleepWow = MetricDelta(avgSleep.toDouble(), prevSleep, percentDelta(avgSleep.toDouble(), prevSleep)),
        activeCalWow = MetricDelta(totalActive, prevActive, percentDelta(totalActive, prevActive)),
        distanceWow = MetricDelta(totalDistance, prevDistance, percentDelta(totalDistance, prevDistance)),
    )
}

private fun computeWeekMoveScore(
    avgSteps: Long,
    stepGoal: Long,
    stepGoalDays: Int,
    avgActiveCalories: Double,
    activeDays: Int,
): Int {
    if (avgSteps <= 0 && avgActiveCalories <= 0) return 0
    val stepPart = ((avgSteps.toDouble() / stepGoal) * 55.0).coerceIn(0.0, 55.0)
    val goalPart = (stepGoalDays / 7.0 * 25.0).coerceIn(0.0, 25.0)
    val calPart = ((avgActiveCalories / DEFAULT_ACTIVE_CAL_GOAL) * 15.0).coerceIn(0.0, 15.0)
    val consistency = (activeDays / 7.0 * 5.0).coerceIn(0.0, 5.0)
    return (stepPart + goalPart + calPart + consistency).roundToInt().coerceIn(0, 100)
}

private fun computeWeekSleepScore(
    avgSleepMinutes: Long,
    sleepGoalMinutes: Long,
    sleepConsistencyHours: Double?,
    sleepDebtMinutes: Long,
    sleepDays: Int,
): Int {
    if (avgSleepMinutes <= 0 || sleepDays == 0) return 0
    val duration = ((avgSleepMinutes.toDouble() / sleepGoalMinutes) * 60.0).coerceIn(0.0, 60.0)
    val consistencyBonus = when {
        sleepConsistencyHours == null -> 10.0
        sleepConsistencyHours <= 0.5 -> 25.0
        sleepConsistencyHours <= 1.0 -> 18.0
        sleepConsistencyHours <= 1.5 -> 10.0
        else -> 4.0
    }
    val debtPenalty = ((sleepDebtMinutes / 60.0) * 3.0).coerceIn(0.0, 20.0)
    return (duration + consistencyBonus - debtPenalty).roundToInt().coerceIn(0, 100)
}

private fun computeWeekReadiness(moveScore: Int, sleepScore: Int, avgRestingHr: Long): Int {
    if (moveScore == 0 && sleepScore == 0) return 0
    var score = when {
        moveScore > 0 && sleepScore > 0 -> (moveScore * 0.35 + sleepScore * 0.65)
        sleepScore > 0 -> sleepScore.toDouble()
        else -> moveScore * 0.7
    }
    if (avgRestingHr > 0) {
        score += when {
            avgRestingHr <= 55 -> 8.0
            avgRestingHr <= 65 -> 4.0
            avgRestingHr <= 75 -> 0.0
            else -> -8.0
        }
    }
    return score.roundToInt().coerceIn(0, 100)
}

data class MacroRangeInsights(
    val avgCalories: Double,
    val avgProtein: Double,
    val totalCalories: Int,
    val totalProtein: Int,
    val loggedDays: Int,
    val rangeDays: Int,
    val calorieAdherence: Float?,
    val proteinAdherence: Float?,
    val bestCalorieDay: DailySummary?,
    val bestProteinDay: DailySummary?,
)

fun computeMacroInsights(
    history: List<DailySummary>,
    rangeDays: Int,
    calorieGoal: Int,
    proteinGoal: Int,
): MacroRangeInsights {
    val logged = history.filter { it.totalCalories > 0 || it.totalProtein > 0 }
    val avgCal = if (logged.isNotEmpty()) logged.map { it.totalCalories }.average() else 0.0
    val avgProt = if (logged.isNotEmpty()) logged.map { it.totalProtein }.average() else 0.0
    val calAdherence = if (calorieGoal > 0 && logged.isNotEmpty()) {
        logged.count {
            val ratio = it.totalCalories.toFloat() / calorieGoal
            ratio in 0.85f..1.15f
        }.toFloat() / logged.size
    } else null
    val protAdherence = if (proteinGoal > 0 && logged.isNotEmpty()) {
        logged.count { it.totalProtein >= (proteinGoal * 0.9f).toInt() }.toFloat() / logged.size
    } else null

    return MacroRangeInsights(
        avgCalories = avgCal,
        avgProtein = avgProt,
        totalCalories = history.sumOf { it.totalCalories },
        totalProtein = history.sumOf { it.totalProtein },
        loggedDays = logged.size,
        rangeDays = rangeDays,
        calorieAdherence = calAdherence,
        proteinAdherence = protAdherence,
        bestCalorieDay = history.maxByOrNull { it.totalCalories }?.takeIf { it.totalCalories > 0 },
        bestProteinDay = history.maxByOrNull { it.totalProtein }?.takeIf { it.totalProtein > 0 },
    )
}

data class HeartRateDayStats(
    val minBpm: Long,
    val maxBpm: Long,
    val avgBpm: Long,
    val sampleCount: Int,
    val restingEstimate: Long?,
)

fun computeHeartRateDayStats(samples: List<Long>): HeartRateDayStats? {
    if (samples.isEmpty()) return null
    val avg = samples.average().toLong()
    val resting = samples.sorted().let { sorted ->
        val take = (sorted.size * 0.1).toInt().coerceAtLeast(1)
        sorted.take(take).average().toLong()
    }
    return HeartRateDayStats(
        minBpm = samples.min(),
        maxBpm = samples.max(),
        avgBpm = avg,
        sampleCount = samples.size,
        restingEstimate = resting,
    )
}

private fun computeTrailingStepStreak(days: List<DailyHealthStats>, goal: Long): Int {
    val today = LocalDate.now()
    var streak = 0
    var cursor = today
    val byDate = days.associateBy { it.date }
    while (true) {
        val day = byDate[cursor] ?: break
        if (day.stats.steps < goal) break
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun percentDelta(current: Double, previous: Double): Double? {
    if (previous <= 0.0 || current <= 0.0) return null
    return ((current - previous) / previous) * 100.0
}

private fun stdDev(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}

fun formatSignedPercent(pct: Double?): String {
    if (pct == null) return "—"
    val sign = if (pct >= 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.0f", pct)}%"
}
