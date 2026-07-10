package com.lemon.yingshi.tv.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lemon.yingshi.tv.data.local.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getRecentWatchHistory(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE movieId = :movieId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getLatestWatchHistoryByMovieId(movieId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE movieId = :movieId AND (episodeId = :episodeId OR (:episodeId IS NULL AND episodeId IS NULL))")
    suspend fun getWatchHistory(movieId: String, episodeId: String?): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getWatchHistoryById(id: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getLatestWatchHistoryByEpisodeId(episodeId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)

    @Update
    suspend fun updateWatchHistory(history: WatchHistoryEntity)

    @Query("UPDATE watch_history SET progress = :progress, lastWatchedAt = :timestamp, watchCount = watchCount + 1 WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteWatchHistory(id: String)

    @Query("DELETE FROM watch_history WHERE movieId = :movieId")
    suspend fun deleteWatchHistoryByMovieId(movieId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllWatchHistory()

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun getWatchHistoryCount(): Int

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt ASC LIMIT :limit")
    suspend fun getOldestWatchHistory(limit: Int): List<WatchHistoryEntity>

    @Query("SELECT COALESCE(SUM(progress), 0) FROM watch_history")
    fun getTotalWatchTimeMs(): Flow<Long>
}
