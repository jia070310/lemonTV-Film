package com.lemon.yingshi.tv.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDownloadDao {

    @Query("SELECT * FROM offline_downloads WHERE serverUrl = :serverUrl ORDER BY updatedAt DESC")
    fun getDownloadsByServer(serverUrl: String): Flow<List<OfflineDownloadEntity>>

    @Query("SELECT * FROM offline_downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): OfflineDownloadEntity?

    @Query("SELECT * FROM offline_downloads WHERE id = :id LIMIT 1")
    fun observeDownloadById(id: String): Flow<OfflineDownloadEntity?>

    @Query(
        """
        SELECT * FROM offline_downloads
        WHERE serverUrl = :serverUrl AND mediaId = :mediaId
        AND ((:episodeId IS NULL AND episodeId IS NULL) OR episodeId = :episodeId)
        LIMIT 1
        """
    )
    suspend fun findDownload(serverUrl: String, mediaId: String, episodeId: String?): OfflineDownloadEntity?

    @Query("SELECT COUNT(*) FROM offline_downloads WHERE serverUrl = :serverUrl")
    fun observeDownloadCount(serverUrl: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM offline_downloads
        WHERE serverUrl = :serverUrl
        AND status IN ('pending', 'downloading')
        """
    )
    fun observeActiveDownloadCount(serverUrl: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_downloads WHERE serverUrl = :serverUrl AND status = :status")
    fun observeDownloadCountByStatus(serverUrl: String, status: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: OfflineDownloadEntity)

    @Update
    suspend fun updateDownload(entity: OfflineDownloadEntity)

    @Query("DELETE FROM offline_downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("SELECT * FROM offline_downloads WHERE status = :status ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextByStatus(status: String): OfflineDownloadEntity?

    @Query(
        """
        UPDATE offline_downloads
        SET status = :pendingStatus, updatedAt = :updatedAt
        WHERE status = :downloadingStatus
        """
    )
    suspend fun resetStaleDownloading(
        pendingStatus: String,
        downloadingStatus: String,
        updatedAt: Long
    )

    @Query("SELECT * FROM offline_downloads WHERE status = :status")
    suspend fun getAllByStatus(status: String): List<OfflineDownloadEntity>

    @Query("SELECT * FROM offline_downloads WHERE serverUrl = :serverUrl")
    suspend fun getDownloadsSnapshot(serverUrl: String): List<OfflineDownloadEntity>

    @Query(
        """
        UPDATE offline_downloads
        SET status = :pausedStatus, updatedAt = :updatedAt
        WHERE serverUrl = :serverUrl AND status IN ('pending', 'downloading')
        """
    )
    suspend fun pauseActiveDownloads(
        serverUrl: String,
        pausedStatus: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE offline_downloads
        SET status = :pendingStatus, updatedAt = :updatedAt
        WHERE serverUrl = :serverUrl AND status = :pausedStatus
        """
    )
    suspend fun resumePausedDownloads(
        serverUrl: String,
        pendingStatus: String,
        pausedStatus: String,
        updatedAt: Long
    )

    @Query("DELETE FROM offline_downloads WHERE serverUrl = :serverUrl")
    suspend fun clearDownloads(serverUrl: String)
}
