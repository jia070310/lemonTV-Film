package com.lemon.yingshi.tv.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lemon.yingshi.tv.data.local.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites WHERE serverUrl = :serverUrl ORDER BY addedAt DESC")
    fun getFavoritesByServerUrl(serverUrl: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE mediaId = :mediaId AND serverUrl = :serverUrl LIMIT 1")
    suspend fun getFavoriteByMediaId(mediaId: String, serverUrl: String): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId AND serverUrl = :serverUrl)")
    suspend fun isFavorite(mediaId: String, serverUrl: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId AND serverUrl = :serverUrl")
    suspend fun deleteFavorite(mediaId: String, serverUrl: String)

    @Query("DELETE FROM favorites WHERE serverUrl = :serverUrl")
    suspend fun clearFavoritesByServerUrl(serverUrl: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE serverUrl = :serverUrl")
    suspend fun getFavoriteCount(serverUrl: String): Int
}
