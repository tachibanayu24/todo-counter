package com.tachibanayu24.todocounter.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completed_tasks",
    indices = [Index(value = ["date"]), Index(value = ["taskId"], unique = true)]
)
data class CompletedTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,        // Google TasksのタスクID
    val title: String,         // タスク名
    val date: String,          // 完了日 (YYYY-MM-DD format)
    val completedAt: Long      // 完了日時 (ミリ秒)
)
