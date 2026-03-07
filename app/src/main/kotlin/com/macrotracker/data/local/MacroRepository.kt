package com.macrotracker.data.local

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
) {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun saveLog(log: MacroLogEntity) = dao.insertLog(log)

    suspend fun deleteLog(id: String) = dao.deleteLog(id)

    suspend fun getLogsForDate(date: String): List<MacroLogEntity> = dao.getLogsForDate(date)

    suspend fun getDailySummary(date: String): DailySummary {
        val totalCal = dao.getTotalCaloriesForDate(date)
        val totalProt = dao.getTotalProteinForDate(date)
        val goals = dao.getGoals() ?: GoalsEntity()
        return DailySummary(
            date = date,
            totalCalories = totalCal,
            totalProtein = totalProt,
            calorieGoal = goals.calorieGoal,
            proteinGoal = goals.proteinGoal,
        )
    }

    suspend fun getDailySummariesRange(rangeDays: Int): List<DailySummary> {
        val today = LocalDate.now()
        val goals = dao.getGoals() ?: GoalsEntity()
        return (0 until rangeDays).map { i ->
            val date = today.minusDays((rangeDays - 1 - i).toLong())
            val dateStr = date.format(dateFormat)
            val totalCal = dao.getTotalCaloriesForDate(dateStr)
            val totalProt = dao.getTotalProteinForDate(dateStr)
            DailySummary(
                date = dateStr,
                totalCalories = totalCal,
                totalProtein = totalProt,
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
            )
        }
    }

    suspend fun getDailySummariesBetween(startDate: LocalDate, endDate: LocalDate): List<DailySummary> {
        val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val goals = dao.getGoals() ?: GoalsEntity()
        return (0 until days).map { i ->
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.format(dateFormat)
            val totalCal = dao.getTotalCaloriesForDate(dateStr)
            val totalProt = dao.getTotalProteinForDate(dateStr)
            DailySummary(
                date = dateStr,
                totalCalories = totalCal,
                totalProtein = totalProt,
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
            )
        }
    }

    suspend fun saveGoals(calories: Int, protein: Int) {
        dao.upsertGoals(GoalsEntity(id = 0, calorieGoal = calories, proteinGoal = protein))
    }

    suspend fun getGoals(): GoalsEntity = dao.getGoals() ?: GoalsEntity()

    suspend fun clearAllData() {
        dao.clearAllLogs()
        dao.clearAllGoals()
    }
}
