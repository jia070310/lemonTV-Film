package com.lemon.yingshi.tv.domain.service

import com.lemon.yingshi.tv.data.local.database.dao.WatchHistoryDao
import com.lemon.yingshi.tv.data.local.database.entity.WatchHistoryEntity
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.utils.EpisodeLabelFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryService @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    private val coverStore: WatchHistoryCoverStore
) {
    private var cachedWatchHistory: List<WatchHistoryItem>? = null
    private var cacheTimestamp: Long = 0
    private val cacheDurationMs = 5 * 60 * 1000L

    fun getRecentWatchHistory(limit: Int = 30): Flow<List<WatchHistoryItem>> {
        return watchHistoryDao.getRecentWatchHistory(limit * 2).flatMapLatest { entities ->
            flow {
                val now = System.currentTimeMillis()
                val cached = cachedWatchHistory
                if (cached != null && (now - cacheTimestamp) < cacheDurationMs) {
                    emit(cached.take(limit))
                    return@flow
                }

                val sorted = entities.sortedByDescending { it.lastWatchedAt }
                val items = mutableListOf<WatchHistoryItem>()
                val seriesMap = mutableMapOf<String, WatchHistoryItem>()

                for (entity in sorted) {
                    if (entity.title.isNullOrBlank()) continue
                    val item = entity.toWatchHistoryItem()
                    if (entity.episodeId != null && MacCmsIds.isMacCmsId(entity.movieId)) {
                        val existing = seriesMap[entity.movieId]
                        if (existing == null || item.lastWatchedAt > existing.lastWatchedAt) {
                            seriesMap[entity.movieId] = item
                        }
                    } else {
                        items.add(item)
                    }
                }
                items.addAll(seriesMap.values)
                items.sortByDescending { it.lastWatchedAt }
                val result = items.take(limit)
                cachedWatchHistory = items.take(50)
                cacheTimestamp = now
                emit(result)
            }
        }
    }

    fun getTotalWatchTimeMs(): Flow<Long> = watchHistoryDao.getTotalWatchTimeMs()

    fun clearCache() {
        cachedWatchHistory = null
        cacheTimestamp = 0
    }

    suspend fun saveWatchProgress(
        mediaId: String,
        episodeId: String? = null,
        progress: Long,
        duration: Long,
        title: String? = null,
        posterUrl: String? = null,
        episodeTitle: String? = null,
        videoUrl: String? = null
    ) {
        val existing = watchHistoryDao.getWatchHistory(mediaId, episodeId)
        val entity = if (existing != null) {
            existing.copy(
                progress = progress,
                duration = duration,
                lastWatchedAt = System.currentTimeMillis(),
                watchCount = existing.watchCount + 1,
                title = title ?: existing.title,
                posterUrl = posterUrl ?: existing.posterUrl,
                localCoverPath = existing.localCoverPath,
                episodeTitle = EpisodeLabelFormatter.normalizeForDisplay(episodeTitle ?: existing.episodeTitle),
                videoUrl = videoUrl ?: existing.videoUrl
            )
        } else {
            WatchHistoryEntity(
                id = generateHistoryId(mediaId, episodeId),
                movieId = mediaId,
                episodeId = episodeId,
                progress = progress,
                duration = duration,
                lastWatchedAt = System.currentTimeMillis(),
                title = title,
                posterUrl = posterUrl,
                episodeTitle = EpisodeLabelFormatter.normalizeForDisplay(episodeTitle),
                videoUrl = videoUrl
            )
        }
        watchHistoryDao.insertWatchHistory(entity)
        enforceHistoryLimit()
        clearCache()
    }

    suspend fun hasLocalCover(mediaId: String, episodeId: String? = null): Boolean {
        val history = watchHistoryDao.getWatchHistory(mediaId, episodeId) ?: return false
        val path = history.localCoverPath ?: return false
        return File(path).exists()
    }

    suspend fun updateLocalCover(mediaId: String, episodeId: String?, localCoverPath: String) {
        val existing = watchHistoryDao.getWatchHistory(mediaId, episodeId) ?: return
        if (existing.localCoverPath == localCoverPath) return

        existing.localCoverPath?.let { oldPath ->
            if (oldPath != localCoverPath) {
                coverStore.deleteCover(oldPath)
            }
        }
        watchHistoryDao.insertWatchHistory(existing.copy(localCoverPath = localCoverPath))
        clearCache()
    }

    suspend fun updatePosterUrlIfMissing(mediaId: String, episodeId: String?, posterUrl: String) {
        if (posterUrl.isBlank()) return
        val existing = watchHistoryDao.getWatchHistory(mediaId, episodeId) ?: return
        if (!existing.posterUrl.isNullOrBlank()) return
        watchHistoryDao.insertWatchHistory(existing.copy(posterUrl = posterUrl))
        clearCache()
    }

    suspend fun getLastWatchPosition(mediaId: String, episodeId: String? = null): WatchPosition? {
        val history = if (episodeId != null) {
            watchHistoryDao.getWatchHistory(mediaId, episodeId)
        } else {
            watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
        }
        return history?.let {
            WatchPosition(
                progress = it.progress,
                duration = it.duration,
                episodeId = it.episodeId
            )
        }
    }

    suspend fun getPlaybackInfo(mediaId: String, episodeId: String?): PlaybackInfo? {
        val history = when {
            episodeId != null -> watchHistoryDao.getWatchHistory(mediaId, episodeId)
                ?: watchHistoryDao.getLatestWatchHistoryByEpisodeId(episodeId)
            else -> watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)
        } ?: return null

        val url = history.videoUrl ?: return null
        return PlaybackInfo(
            videoPath = url,
            title = history.title ?: "",
            episodeTitle = history.episodeTitle,
            mediaId = history.movieId,
            episodeId = history.episodeId,
            startPosition = history.progress
        )
    }

    suspend fun deleteWatchHistory(historyId: String) {
        val history = watchHistoryDao.getWatchHistoryById(historyId)
        coverStore.deleteCover(history?.localCoverPath)
        watchHistoryDao.deleteWatchHistory(historyId)
        clearCache()
    }

    suspend fun clearAllWatchHistory() {
        coverStore.clearAll()
        watchHistoryDao.clearAllWatchHistory()
        clearCache()
    }

    suspend fun getWatchProgressPercent(mediaId: String): Int {
        val history = watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId) ?: return 0
        return if (history.duration > 0) {
            (history.progress * 100 / history.duration).toInt()
        } else 0
    }

    suspend fun isWatched(mediaId: String, episodeId: String? = null): Boolean {
        val history = watchHistoryDao.getWatchHistory(mediaId, episodeId) ?: return false
        return history.progress > history.duration * 0.9
    }

    private suspend fun enforceHistoryLimit() {
        val count = watchHistoryDao.getWatchHistoryCount()
        if (count <= MAX_WATCH_HISTORY_COUNT) return

        val excess = count - MAX_WATCH_HISTORY_COUNT
        val oldestRecords = watchHistoryDao.getOldestWatchHistory(excess)
        oldestRecords.forEach { record ->
            coverStore.deleteCover(record.localCoverPath)
            watchHistoryDao.deleteWatchHistory(record.id)
        }
    }

    private fun generateHistoryId(mediaId: String, episodeId: String?): String =
        if (episodeId != null) "history_${mediaId}_${episodeId}" else "history_$mediaId"

    private fun WatchHistoryEntity.toWatchHistoryItem(): WatchHistoryItem {
        val episodeLabel = EpisodeLabelFormatter.normalizeForDisplay(episodeTitle)
        val coverPath = localCoverPath?.takeIf { File(it).exists() }
        return WatchHistoryItem(
            id = id,
            mediaId = movieId,
            title = title.orEmpty(),
            posterUrl = posterUrl,
            localCoverPath = coverPath,
            backdropUrl = coverPath ?: posterUrl,
            episodeTitle = episodeLabel,
            episodeNumber = null,
            seasonNumber = null,
            episodeId = episodeId,
            progress = progress,
            duration = duration,
            progressPercent = if (duration > 0) (progress * 100 / duration).toInt() else 0,
            lastWatchedAt = lastWatchedAt,
            isWatched = duration > 0 && progress > duration * 0.9
        )
    }

    companion object {
        const val MAX_WATCH_HISTORY_COUNT = 100
    }
}

data class WatchHistoryItem(
    val id: String,
    val mediaId: String,
    val title: String,
    val posterUrl: String?,
    val localCoverPath: String?,
    val backdropUrl: String?,
    val episodeTitle: String?,
    val episodeNumber: Int?,
    val seasonNumber: Int?,
    val episodeId: String?,
    val progress: Long,
    val duration: Long,
    val progressPercent: Int,
    val lastWatchedAt: Long,
    val isWatched: Boolean
) {
    fun coverImageModel(): Any? {
        localCoverPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0L) return file
        }
        return backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl?.takeIf { it.isNotBlank() }
    }
}

data class WatchPosition(
    val progress: Long,
    val duration: Long,
    val episodeId: String?
)

data class PlaybackInfo(
    val videoPath: String?,
    val title: String,
    val episodeTitle: String?,
    val mediaId: String,
    val episodeId: String?,
    val startPosition: Long
)
