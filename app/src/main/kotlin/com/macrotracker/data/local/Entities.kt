package com.macrotracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macro_logs")
data class MacroLogEntity(
    @PrimaryKey val id: String,
    val date: String,       // yyyy-MM-dd
    val foodName: String,
    val calories: Int,
    val protein: Int,
)

@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey val id: Int = 0,    // singleton row
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
)

