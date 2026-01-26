package com.tachibanayu24.todocounter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.dao.DailyCompletionDao
import com.tachibanayu24.todocounter.data.entity.CompletedTask
import com.tachibanayu24.todocounter.data.entity.DailyCompletion

@Database(
    entities = [DailyCompletion::class, CompletedTask::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun completedTaskDao(): CompletedTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS completed_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        date TEXT NOT NULL,
                        completedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_completed_tasks_date ON completed_tasks(date)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_completed_tasks_taskId ON completed_tasks(taskId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "todo_counter_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
