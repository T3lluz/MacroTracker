package com.macrotracker.ui.screens.health

import androidx.compose.ui.graphics.Color
import com.macrotracker.data.health.HealthStats
import com.macrotracker.ui.theme.Primary
import java.util.Locale

enum class HealthMetric {
    STEPS,
    HEART_RATE,
    SLEEP,
    CALORIES,
    RESTING_HEART_RATE,
    OXYGEN_SATURATION,
    RESPIRATORY_RATE,
    DISTANCE,
    FLOORS_CLIMBED,
}

fun HealthStats.valueOf(metric: HealthMetric): Double = when (metric) {
    HealthMetric.STEPS -> steps.toDouble()
    HealthMetric.HEART_RATE -> avgHeartRate.toDouble()
    HealthMetric.SLEEP -> sleepMinutes / 60.0
    HealthMetric.CALORIES -> activeCaloriesBurned
    HealthMetric.RESTING_HEART_RATE -> restingHeartRate.toDouble()
    HealthMetric.OXYGEN_SATURATION -> oxygenSaturation
    HealthMetric.RESPIRATORY_RATE -> respiratoryRate
    HealthMetric.DISTANCE -> distance
    HealthMetric.FLOORS_CLIMBED -> floorsClimbed
}

fun HealthMetric.tint(): Color = when (this) {
    HealthMetric.STEPS -> Primary
    HealthMetric.HEART_RATE -> Color(0xFFEF5350)
    HealthMetric.SLEEP -> Color(0xFF7C4DFF)
    HealthMetric.CALORIES -> Color(0xFFFF9800)
    HealthMetric.RESTING_HEART_RATE -> Color(0xFFE57373)
    HealthMetric.OXYGEN_SATURATION -> Color(0xFF42A5F5)
    HealthMetric.RESPIRATORY_RATE -> Color(0xFF26C6DA)
    HealthMetric.DISTANCE -> Color(0xFF26A69A)
    HealthMetric.FLOORS_CLIMBED -> Color(0xFF66BB6A)
}

fun HealthMetric.chipLabel(): String = when (this) {
    HealthMetric.STEPS -> "Steps"
    HealthMetric.HEART_RATE -> "HR"
    HealthMetric.SLEEP -> "Sleep"
    HealthMetric.CALORIES -> "Active"
    HealthMetric.RESTING_HEART_RATE -> "Resting"
    HealthMetric.OXYGEN_SATURATION -> "SpO₂"
    HealthMetric.RESPIRATORY_RATE -> "Resp"
    HealthMetric.DISTANCE -> "Distance"
    HealthMetric.FLOORS_CLIMBED -> "Floors"
}

/** Prefer smooth area charts for rate-like metrics; bars for cumulative totals. */
fun HealthMetric.prefersAreaChart(): Boolean = when (this) {
    HealthMetric.HEART_RATE,
    HealthMetric.RESTING_HEART_RATE,
    HealthMetric.OXYGEN_SATURATION,
    HealthMetric.RESPIRATORY_RATE,
    HealthMetric.SLEEP,
    -> true
    else -> false
}

fun formatMetricValue(metric: HealthMetric, value: Double, compact: Boolean = false): String {
    if (value <= 0) return "—"
    return when (metric) {
        HealthMetric.STEPS -> if (compact && value >= 1000) {
            String.format(Locale.US, "%.1fk", value / 1000.0)
        } else {
            String.format(Locale.US, "%,d", value.toInt())
        }
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "${value.toInt()}"
        HealthMetric.SLEEP -> {
            val h = value.toInt()
            val m = ((value - h) * 60).toInt()
            if (compact) String.format(Locale.US, "%.1fh", value) else "${h}h ${m}m"
        }
        HealthMetric.CALORIES -> String.format(Locale.US, "%,d", value.toInt())
        HealthMetric.OXYGEN_SATURATION -> String.format(Locale.US, "%.1f%%", value)
        HealthMetric.RESPIRATORY_RATE -> String.format(Locale.US, "%.1f", value)
        HealthMetric.DISTANCE -> String.format(Locale.US, "%.2f", value)
        HealthMetric.FLOORS_CLIMBED -> String.format(Locale.US, "%.1f", value)
    }
}

fun formatMetricUnit(metric: HealthMetric): String = when (metric) {
    HealthMetric.STEPS -> "steps"
    HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "bpm"
    HealthMetric.SLEEP -> ""
    HealthMetric.CALORIES -> "kcal"
    HealthMetric.OXYGEN_SATURATION -> ""
    HealthMetric.RESPIRATORY_RATE -> "rpm"
    HealthMetric.DISTANCE -> "km"
    HealthMetric.FLOORS_CLIMBED -> "floors"
}
