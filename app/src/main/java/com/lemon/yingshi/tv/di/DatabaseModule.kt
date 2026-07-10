package com.lemon.yingshi.tv.di

import android.content.Context
import androidx.room.Room
import com.lemon.yingshi.tv.data.local.database.LomenDatabase
import com.lemon.yingshi.tv.data.local.database.dao.SkipConfigDao
import com.lemon.yingshi.tv.data.local.database.dao.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LomenDatabase {
        return Room.databaseBuilder(
            context,
            LomenDatabase::class.java,
            "lomen_tv_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWatchHistoryDao(database: LomenDatabase): WatchHistoryDao {
        return database.watchHistoryDao()
    }

    @Provides
    fun provideSkipConfigDao(database: LomenDatabase): SkipConfigDao {
        return database.skipConfigDao()
    }
}
