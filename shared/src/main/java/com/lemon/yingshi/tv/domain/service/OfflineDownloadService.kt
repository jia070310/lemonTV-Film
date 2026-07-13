package com.lemon.yingshi.tv.domain.service

import com.lemon.yingshi.tv.data.local.database.dao.OfflineDownloadDao
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadEntity
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadStatus
import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class OfflineDownloadSummary(
    val downloadingCount: Int,
    val completedCount: Int
) {
    val totalCount: Int get() = downloadingCount + completedCount
}

data class OfflineDownloadItem(
    val id: String,
    val mediaId: String,
    val episodeId: String?,
    val title: String,
    val episodeTitle: String?,
    val posterUrl: String?,
    val videoUrl: String,
    val status: String,
    val progress: Int,
    val localPlaybackUrl: String?,
    val errorMessage: String?,
    val updatedAt: Long
) {
    val isCompleted: Boolean get() = status == OfflineDownloadStatus.COMPLETED
    val isPaused: Boolean get() = status == OfflineDownloadStatus.PAUSED
    val isActive: Boolean get() = status == OfflineDownloadStatus.PENDING ||
        status == OfflineDownloadStatus.DOWNLOADING
    val canPause: Boolean get() = isActive
    val canResume: Boolean get() = isPaused || status == OfflineDownloadStatus.FAILED
}

@Singleton
class OfflineDownloadService @Inject constructor(
    private val offlineDownloadDao: OfflineDownloadDao,
    private val macCmsPreferences: MacCmsPreferences,
    private val offlineDownloadManager: OfflineDownloadManager
) {

    fun getAllDownloads(): Flow<List<OfflineDownloadItem>> {
        return macCmsPreferences.serverUrl.flatMapLatest { serverUrl ->
            offlineDownloadDao.getDownloadsByServer(serverUrl).map { list ->
                list.map { it.toItem() }
            }
        }
    }

    fun observeDownloadCount(): Flow<Int> {
        return macCmsPreferences.serverUrl.flatMapLatest { serverUrl ->
            offlineDownloadDao.observeDownloadCount(serverUrl)
        }
    }

    fun observeDownloadSummary(): Flow<OfflineDownloadSummary> {
        return macCmsPreferences.serverUrl.flatMapLatest { serverUrl ->
            combine(
                offlineDownloadDao.observeActiveDownloadCount(serverUrl),
                offlineDownloadDao.observeDownloadCountByStatus(
                    serverUrl,
                    OfflineDownloadStatus.COMPLETED
                )
            ) { downloadingCount, completedCount ->
                OfflineDownloadSummary(
                    downloadingCount = downloadingCount,
                    completedCount = completedCount
                )
            }
        }
    }

    suspend fun findDownload(mediaId: String, episodeId: String?): OfflineDownloadItem? {
        return offlineDownloadDao.findDownload(currentServerUrl(), mediaId, episodeId)?.toItem()
    }

    suspend fun enqueueDownload(
        mediaId: String,
        episodeId: String?,
        title: String,
        episodeTitle: String?,
        posterUrl: String?,
        videoUrl: String
    ): OfflineDownloadItem {
        val serverUrl = currentServerUrl()
        val id = buildDownloadId(serverUrl, mediaId, episodeId)
        val existing = offlineDownloadDao.findDownload(serverUrl, mediaId, episodeId)
        if (existing != null) {
            if (existing.status == OfflineDownloadStatus.FAILED) {
                offlineDownloadManager.deleteFiles(existing.id)
                val retry = existing.copy(
                    status = OfflineDownloadStatus.PENDING,
                    progress = 0,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    localPlaybackUrl = null,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                offlineDownloadDao.insertDownload(retry)
                offlineDownloadManager.enqueue(retry)
                return retry.toItem()
            }
            return existing.toItem()
        }

        val entity = OfflineDownloadEntity(
            id = id,
            serverUrl = serverUrl,
            mediaId = mediaId,
            episodeId = episodeId,
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = posterUrl,
            videoUrl = videoUrl.trim(),
            status = OfflineDownloadStatus.PENDING
        )
        offlineDownloadDao.insertDownload(entity)
        offlineDownloadManager.enqueue(entity)
        return entity.toItem()
    }

    suspend fun deleteDownload(id: String) {
        offlineDownloadManager.cancel(id)
        offlineDownloadManager.deleteFiles(id)
        offlineDownloadDao.deleteDownload(id)
    }

    suspend fun pauseDownload(id: String) {
        offlineDownloadManager.pause(id)
    }

    suspend fun resumeDownload(id: String) {
        val entity = offlineDownloadDao.getDownloadById(id) ?: return
        when (entity.status) {
            OfflineDownloadStatus.PAUSED -> offlineDownloadManager.resume(id)
            OfflineDownloadStatus.FAILED -> {
                offlineDownloadManager.deleteFiles(id)
                val retry = entity.copy(
                    status = OfflineDownloadStatus.PENDING,
                    progress = 0,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    localPlaybackUrl = null,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                offlineDownloadDao.updateDownload(retry)
                offlineDownloadManager.enqueue(retry)
            }
        }
    }

    suspend fun pauseAllDownloads() {
        offlineDownloadManager.pauseAll(currentServerUrl())
    }

    suspend fun resumeAllDownloads() {
        offlineDownloadManager.resumeAll(currentServerUrl())
    }

    suspend fun clearAllDownloads() {
        val serverUrl = currentServerUrl()
        offlineDownloadManager.cancelAll()
        offlineDownloadDao.getDownloadsSnapshot(serverUrl).forEach { download ->
            offlineDownloadManager.deleteFiles(download.id)
        }
        offlineDownloadDao.clearDownloads(serverUrl)
    }

    private suspend fun currentServerUrl(): String = macCmsPreferences.serverUrl.first()

    companion object {
        fun buildDownloadId(serverUrl: String, mediaId: String, episodeId: String?): String {
            val episodePart = episodeId ?: "movie"
            return "${serverUrl.hashCode()}_${sanitizeIdPart(mediaId)}_${sanitizeIdPart(episodePart)}"
        }

        private fun sanitizeIdPart(value: String): String =
            value.replace(':', '_').replace('/', '_').replace('\\', '_')
    }
}

private fun OfflineDownloadEntity.toItem() = OfflineDownloadItem(
    id = id,
    mediaId = mediaId,
    episodeId = episodeId,
    title = title,
    episodeTitle = episodeTitle,
    posterUrl = posterUrl,
    videoUrl = videoUrl,
    status = status,
    progress = progress,
    localPlaybackUrl = localPlaybackUrl,
    errorMessage = errorMessage,
    updatedAt = updatedAt
)
