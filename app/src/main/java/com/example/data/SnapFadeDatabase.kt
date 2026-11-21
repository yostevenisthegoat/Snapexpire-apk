package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ManagedScreenshotEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ScreenshotConverters::class)
abstract class SnapFadeDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var instance: SnapFadeDatabase? = null

        fun getInstance(context: Context): SnapFadeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnapFadeDatabase::class.java,
                    "snapfade_database"
                ).build().also { instance = it }
            }
        }
    }
}
