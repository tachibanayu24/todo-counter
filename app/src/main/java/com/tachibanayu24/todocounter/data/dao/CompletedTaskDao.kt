package com.tachibanayu24.todocounter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tachibanayu24.todocounter.data.entity.CompletedTask

@Dao
interface CompletedTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<CompletedTask>)

    @Query("SELECT * FROM completed_tasks WHERE date = :date ORDER BY completedAt DESC")
    suspend fun getByDate(date: String): List<CompletedTask>
}
