package com.tachibanayu24.todocounter.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tachibanayu24.todocounter.data.AppDatabase
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.dao.DailyCompletionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "todo_counter_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideDailyCompletionDao(database: AppDatabase): DailyCompletionDao {
        return database.dailyCompletionDao()
    }

    @Provides
    fun provideCompletedTaskDao(database: AppDatabase): CompletedTaskDao {
        return database.completedTaskDao()
    }
}
