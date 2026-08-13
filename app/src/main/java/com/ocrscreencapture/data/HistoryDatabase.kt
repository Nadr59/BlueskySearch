package com.ocrscreencapture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryItem::class, ImageAnalysisItem::class],
    version = 2,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun imageAnalysisDao(): ImageAnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `image_analysis_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `keywords` TEXT NOT NULL DEFAULT '',
                        `detectedText` TEXT NOT NULL DEFAULT '',
                        `analysis` TEXT NOT NULL DEFAULT '',
                        `websites` TEXT NOT NULL DEFAULT '',
                        `rawResponse` TEXT NOT NULL DEFAULT '',
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "ocr_history_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
