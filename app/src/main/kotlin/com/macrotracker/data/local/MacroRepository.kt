package com.macrotracker.data.local

import android.content.Context
import com.macrotracker.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class DailySummary(
    val date: String,
    val totalCalories: Int,
    val totalProtein: Int,
    val calorieGoal: Int,
    val proteinGoal: Int,
)

@Singleton
class MacroRepository @Inject constructor(
    private val dao: MacroDao,
    @ApplicationContext private val context: Context,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Every write goes through here, so this is the one place that has to tell
    // the home-screen widgets the numbers moved. Doing it per screen meant
    // logging from Health / AI / the scanner never refreshed them.
    suspend fun saveLog(log: MacroLogEntity) {
        dao.insertLog(log)
        WidgetUpdater.notifyDataChanged(context)
    }

    suspend fun deleteLog(id: String) {
        dao.deleteLog(id)
        WidgetUpdater.notifyDataChanged(context)
    }

    suspend fun getLogsForDate(date: String): List<MacroLogEntity> = dao.getLogsForDate(date)

    /** Fetches today's summary in 2 DB round-trips (batch totals + goals). */
    suspend fun getDailySummary(date: String): DailySummary {
        val totals = dao.getTotalsForDates(listOf(date)).firstOrNull()
        val goals  = dao.getGoals() ?: GoalsEntity()
        return DailySummary(
            date          = date,
            totalCalories = totals?.totalCalories ?: 0,
            totalProtein  = totals?.totalProtein  ?: 0,
            calorieGoal   = goals.calorieGoal,
            proteinGoal   = goals.proteinGoal,
        )
    }

    /**
     * Fetches summaries for the last [rangeDays] days in 2 DB round-trips:
     * one batch totals query + one goals query (was N×2 + 1 before).
     */
    suspend fun getDailySummariesRange(rangeDays: Int): List<DailySummary> {
        val today = LocalDate.now()
        val dates = (0 until rangeDays).map { i ->
            today.minusDays((rangeDays - 1 - i).toLong()).format(dateFormat)
        }
        val goals     = dao.getGoals() ?: GoalsEntity()
        val totalsMap = dao.getTotalsForDates(dates).associateBy { it.date }
        return dates.map { dateStr ->
            val totals = totalsMap[dateStr]
            DailySummary(
                date          = dateStr,
                totalCalories = totals?.totalCalories ?: 0,
                totalProtein  = totals?.totalProtein  ?: 0,
                calorieGoal   = goals.calorieGoal,
                proteinGoal   = goals.proteinGoal,
            )
        }
    }


    suspend fun saveGoals(calories: Int, protein: Int) {
        dao.upsertGoals(GoalsEntity(id = 0, calorieGoal = calories, proteinGoal = protein))
        WidgetUpdater.notifyDataChanged(context)
    }

    suspend fun getGoals(): GoalsEntity = dao.getGoals() ?: GoalsEntity()

}
