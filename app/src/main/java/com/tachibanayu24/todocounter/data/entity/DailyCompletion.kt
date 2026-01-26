package com.tachibanayu24.todocounter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_completions")
data class DailyCompletion(
    @PrimaryKey
    val date: String,  // YYYY-MM-DD format
    val completedCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
