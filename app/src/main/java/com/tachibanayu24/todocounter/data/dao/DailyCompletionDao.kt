package com.tachibanayu24.todocounter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tachibanayu24.todocounter.data.entity.DailyCompletion

@Dao
interface DailyCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: DailyCompletion)

    @Query("SELECT * FROM daily_completions WHERE date = :date")
    suspend fun getByDate(date: String): DailyCompletion?

    @Query("SELECT * FROM daily_completions WHERE date >= :startDate ORDER BY date ASC")
    suspend fun getFromDate(startDate: String): List<DailyCompletion>

    @Query("SELECT * FROM daily_completions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRange(startDate: String, endDate: String): List<DailyCompletion>

    @Query("SELECT SUM(completedCount) FROM daily_completions")
    suspend fun getTotalCompleted(): Int?

    @Query("SELECT MAX(completedCount) FROM daily_completions")
    suspend fun getMaxCompleted(): Int?

    @Query("SELECT COUNT(*) FROM daily_completions WHERE completedCount > 0")
    suspend fun getActiveDaysCount(): Int

    @Query("SELECT MIN(date) FROM daily_completions")
    suspend fun getFirstRecordDate(): String?
}
