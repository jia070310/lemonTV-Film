package com.lemon.yingshi.tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lemon.yingshi.tv.data.local.database.dao.FavoriteDao
import com.lemon.yingshi.tv.data.local.database.dao.OfflineDownloadDao
import com.lemon.yingshi.tv.data.local.database.dao.SkipConfigDao
import com.lemon.yingshi.tv.data.local.database.dao.WatchHistoryDao
import com.lemon.yingshi.tv.data.local.database.entity.FavoriteEntity
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadEntity
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import com.lemon.yingshi.tv.data.local.database.entity.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        SkipConfigEntity::class,
        FavoriteEntity::class,
        OfflineDownloadEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LomenDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun skipConfigDao(): SkipConfigDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun offlineDownloadDao(): OfflineDownloadDao
}
