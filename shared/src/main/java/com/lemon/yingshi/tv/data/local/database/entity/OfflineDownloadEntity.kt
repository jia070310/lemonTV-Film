package com.lemon.yingshi.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_downloads",
    indices = [
        Index(value = ["mediaId", "episodeId"]),
        Index(value = ["serverUrl"]),
        Index(value = ["status"])
    ]
)
data class OfflineDownloadEntity(
    @PrimaryKey val id: String,
    val serverUrl: String,
    val mediaId: String,
    val episodeId: String?,
    val title: String,
    val episodeTitle: String?,
    val posterUrl: String?,
    val videoUrl: String,
    val status: String,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val localPlaybackUrl: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object OfflineDownloadStatus {
    const val PENDING = "pending"
    const val DOWNLOADING = "downloading"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}
