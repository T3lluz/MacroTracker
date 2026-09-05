package com.macrotracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MacroLogEntity::class,
        GoalsEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun chatDao(): ChatDao
}
