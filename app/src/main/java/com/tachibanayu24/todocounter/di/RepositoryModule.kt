package com.tachibanayu24.todocounter.di

import android.content.Context
import com.tachibanayu24.todocounter.api.ITasksRepository
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.data.SyncManager
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.dao.DailyCompletionDao
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTasksRepository(@ApplicationContext context: Context): ITasksRepository {
        return TasksRepository(context)
    }

    @Provides
    @Singleton
    fun provideCompletionRepository(dailyCompletionDao: DailyCompletionDao): CompletionRepository {
        return CompletionRepository(dailyCompletionDao)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        tasksRepository: ITasksRepository,
        completionRepository: CompletionRepository,
        completedTaskDao: CompletedTaskDao
    ): SyncManager {
        return SyncManager(tasksRepository, completionRepository, completedTaskDao)
    }
}
