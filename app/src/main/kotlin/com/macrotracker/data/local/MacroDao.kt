package com.macrotracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MacroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MacroLogEntity)

    @Query("DELETE FROM macro_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT * FROM macro_logs WHERE date = :date ORDER BY id DESC")
    suspend fun getLogsForDate(date: String): List<MacroLogEntity>

    @Query("SELECT * FROM macro_logs ORDER BY date DESC, id DESC")
    suspend fun getAllLogs(): List<MacroLogEntity>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM macro_logs WHERE date = :date")
    suspend fun getTotalCaloriesForDate(date: String): Int

    @Query("SELECT COALESCE(SUM(protein), 0) FROM macro_logs WHERE date = :date")
    suspend fun getTotalProteinForDate(date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: GoalsEntity)

    @Query("SELECT * FROM goals WHERE id = 0")
    suspend fun getGoals(): GoalsEntity?

    @Query("DELETE FROM macro_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM goals")
    suspend fun clearAllGoals()
}

