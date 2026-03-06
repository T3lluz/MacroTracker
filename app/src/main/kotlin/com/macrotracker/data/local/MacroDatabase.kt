package com.macrotracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MacroLogEntity::class, GoalsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
}

