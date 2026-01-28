package com.tachibanayu24.todocounter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tachibanayu24.todocounter.data.entity.CompletedTask

data class DailyCount(
    val date: String,
    val count: Int
)

@Dao
interface CompletedTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<CompletedTask>)

    @Query("SELECT * FROM completed_tasks WHERE date = :date ORDER BY completedAt DESC")
    suspend fun getByDate(date: String): List<CompletedTask>

    @Query("SELECT COUNT(*) FROM completed_tasks WHERE date = :date")
    suspend fun getCountByDate(date: String): Int

    @Query("SELECT date, COUNT(*) as count FROM completed_tasks GROUP BY date")
    suspend fun getDailyCountsAll(): List<DailyCount>

    @Query("SELECT date, COUNT(*) as count FROM completed_tasks WHERE date >= :startDate GROUP BY date")
    suspend fun getDailyCountsFrom(startDate: String): List<DailyCount>
}
