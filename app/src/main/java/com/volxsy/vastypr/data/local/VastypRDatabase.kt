package com.volxsy.vastypr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Skill: android-room-database
@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class VastypRDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
