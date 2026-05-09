package com.lomen.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lomen.tv.data.local.database.dao.TmdbEpisodeDao
import com.lomen.tv.data.local.database.dao.WebDavMediaDao
import com.lomen.tv.data.local.database.entity.TmdbEpisodeEntity
import com.lomen.tv.data.local.database.entity.WebDavMediaEntity
import com.lomen.tv.data.preferences.LibraryScanPreferences
import com.lomen.tv.data.preferences.TmdbApiPreferences
import com.lomen.tv.data.scraper.FileFingerprintManager
import com.lomen.tv.data.scraper.MediaScraper
import com.lomen.tv.data.scraper.ScrapedMedia
import com.lomen.tv.data.scraper.SmartMediaScraper
import com.lomen.tv.data.scraper.TmdbScraper
import com.lomen.tv.data.remote.RemoteFileClient
import com.lomen.tv.data.webdav.WebDavClient
import com.lomen.tv.data.webdav.WebDavFile
import com.lomen.tv.data.guangya.GuangyaApiClient
import com.lomen.tv.domain.model.ResourceLibrary
import com.lomen.tv.domain.service.TmdbMetadataSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MediaSyncViewModel @Inject constructor(
    private val webDavMediaDao: WebDavMediaDao,
    private val tmdbEpisodeDao: TmdbEpisodeDao,
    private val tmdbApiPreferences: TmdbApiPreferences,
    private val libraryScanPreferences: LibraryScanPreferences,
    private val tmdbMetadataSyncManager: TmdbMetadataSyncManager
) : ViewModel() {

    companion object {
        private const val TAG = "MediaSyncViewModel"
    }

    private val mediaScraper = MediaScraper()
    private val smartScraper = SmartMediaScraper() // 新增智能刮削器

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncProgress = MutableStateFlow(0 to 0) // (current, total)
    val syncProgress: StateFlow<Pair<Int, Int>> = _syncProgress.asStateFlow()

    sealed class SyncState {
        object Idle : SyncState()
        object Scanning : SyncState()
        object Scraping : SyncState()
        object Completed : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private fun buildErrorMessage(stage: String, throwable: Throwable?): String {
        if (throwable == null) return "${stage} failed: unknown"
        val root = generateSequence(throwable) { it.cause }.last()
        val detail = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
        return "${stage} failed: $detail"
    }

    /**
     * 至少配置 API Key 或 Read Token 其一，且同步到 [TmdbScraper] 后再刮削。
     */
    private suspend fun ensureTmdbForScrape(): Boolean {
        val key = tmdbApiPreferences.apiKey.first()
        val token = tmdbApiPreferences.apiReadToken.first()
        if (key.isBlank() && token.isBlank()) {
            _syncState.value = SyncState.Error(TmdbApiPreferences.MSG_TMDB_REQUIRED_FOR_SCAN)
            return false
        }
        TmdbScraper.getInstance().setApiKey(
            key.takeIf { it.isNotBlank() },
            token.takeIf { it.isNotBlank() }
        )
        return true
    }

    /**
     * 全量同步 - 清空后重新刮削所有文件
     */
    fun syncLibrary(library: ResourceLibrary) {
        viewModelScope.launch {
            try {
                if (!ensureTmdbForScrape()) return@launch
                Log.d(TAG, "开始全量同步资源库: ${library.name}")
                _syncState.value = SyncState.Scanning
                _syncProgress.value = 0 to 0

                // 1. 扫描WebDAV文件
                Log.d(TAG, "开始扫描WebDAV文件...")
                val scanConcurrency = libraryScanPreferences.scanConcurrency.first()
                val client: RemoteFileClient = when (library.type) {
                    ResourceLibrary.LibraryType.WEBDAV -> WebDavClient(library, scanConcurrency)
                    ResourceLibrary.LibraryType.GUANGYA -> GuangyaApiClient(library, scanConcurrency)
                    else -> WebDavClient(library, scanConcurrency)
                }
                val filesResult = withContext(Dispatchers.IO) {
                    client.listAllVideoFiles { count ->
                        _syncProgress.value = count to 0 // 扫描阶段，总数未知
                    }
                }

                if (filesResult.isFailure) {
                    val error = filesResult.exceptionOrNull()
                    Log.e(TAG, "扫描失败: ${error?.message}", error)
                    _syncState.value = SyncState.Error(buildErrorMessage("扫描", error))
                    return@launch
                }

                val files = filesResult.getOrNull() ?: emptyList()
                Log.d(TAG, "扫描完成，找到 ${files.size} 个视频文件")
                
                if (files.isEmpty()) {
                    _syncState.value = SyncState.Completed
                    return@launch
                }

                // 2. 刮削媒体信息
                Log.d(TAG, "开始刮削媒体信息...")
                _syncState.value = SyncState.Scraping
                _syncProgress.value = 0 to files.size

                // 先清空该资源库的旧数据
                withContext(Dispatchers.IO) {
                    webDavMediaDao.deleteByLibraryId(library.id)
                }

                // 执行刮削和保存
                performScrapeAndSave(library, client, files)

            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _syncState.value = SyncState.Error(buildErrorMessage("同步", e))
            }
        }
    }

    /**
     * 增量同步 - 只处理新增和修改的文件
     */
    fun syncLibraryIncremental(library: ResourceLibrary) {
        viewModelScope.launch {
            try {
                if (!ensureTmdbForScrape()) return@launch
                Log.d(TAG, "开始增量同步资源库: ${library.name}")
                _syncState.value = SyncState.Scanning
                _syncProgress.value = 0 to 0

                // 1. 扫描WebDAV文件
                Log.d(TAG, "开始扫描WebDAV文件...")
                val scanConcurrency = libraryScanPreferences.scanConcurrency.first()
                val client: RemoteFileClient = when (library.type) {
                    ResourceLibrary.LibraryType.WEBDAV -> WebDavClient(library, scanConcurrency)
                    ResourceLibrary.LibraryType.GUANGYA -> GuangyaApiClient(library, scanConcurrency)
                    else -> WebDavClient(library, scanConcurrency)
                }
                val filesResult = withContext(Dispatchers.IO) {
                    client.listAllVideoFiles { count ->
                        _syncProgress.value = count to 0 // 扫描阶段，总数未知
                    }
                }

                if (filesResult.isFailure) {
                    val error = filesResult.exceptionOrNull()
                    Log.e(TAG, "扫描失败: ${error?.message}", error)
                    _syncState.value = SyncState.Error(buildErrorMessage("扫描", error))
                    return@launch
                }

                val files = filesResult.getOrNull() ?: emptyList()
                Log.d(TAG, "扫描完成，找到 ${files.size} 个视频文件")
                
                if (files.isEmpty()) {
                    _syncState.value = SyncState.Completed
                    return@launch
                }

                // 2. 获取数据库中的文件指纹
                val existingFingerprints = withContext(Dispatchers.IO) {
                    webDavMediaDao.getFingerprintsByLibraryId(library.id)
                }
                val fingerprintMap = existingFingerprints.associate { 
                    it.filePath to it.fileFingerprint 
                }
                Log.d(TAG, "数据库中已有 ${fingerprintMap.size} 个文件的指纹")

                // 3. 计算差异
                val newFiles = mutableListOf<WebDavFile>()
                val modifiedFiles = mutableListOf<WebDavFile>()
                val unchangedPaths = mutableSetOf<String>()

                files.forEach { file ->
                    val fingerprint = FileFingerprintManager.generateFingerprint(file)
                    val existingFingerprint = fingerprintMap[file.path]

                    when {
                        existingFingerprint == null -> {
                            // 新增文件
                            newFiles.add(file)
                        }
                        existingFingerprint != fingerprint -> {
                            // 修改过的文件
                            modifiedFiles.add(file)
                        }
                        else -> {
                            // 未变化的文件
                            unchangedPaths.add(file.path)
                        }
                    }
                }

                // 4. 找出已删除的文件
                val currentPaths = files.map { it.path }.toSet()
                val deletedPaths = fingerprintMap.keys.filter { it !in currentPaths }

                Log.d(TAG, "增量分析结果: 新增 ${newFiles.size}, 修改 ${modifiedFiles.size}, 删除 ${deletedPaths.size}, 未变 ${unchangedPaths.size}")

                // 5. 删除已不存在的文件记录
                if (deletedPaths.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        webDavMediaDao.deleteByFilePaths(library.id, deletedPaths)
                    }
                    Log.d(TAG, "已删除 ${deletedPaths.size} 个不存在的文件记录")
                }

                // 6. 合并新增和修改的文件进行刮削
                val filesToScrape = newFiles + modifiedFiles
                // 记录哪些是修改的文件，用于后续保持 createdAt
                val modifiedPaths = modifiedFiles.map { it.path }.toSet()

                if (filesToScrape.isEmpty()) {
                    Log.d(TAG, "没有需要刮削的新文件或修改的文件")
                    _syncState.value = SyncState.Completed
                    return@launch
                }

                // 7. 刮削媒体信息
                Log.d(TAG, "开始刮削 ${filesToScrape.size} 个新增/修改的文件...")
                _syncState.value = SyncState.Scraping
                _syncProgress.value = 0 to filesToScrape.size

                // 执行刮削和保存
                performScrapeAndSave(library, client, filesToScrape, modifiedPaths)

            } catch (e: Exception) {
                Log.e(TAG, "Incremental sync failed", e)
                _syncState.value = SyncState.Error(buildErrorMessage("增量同步", e))
            }
        }
    }

    /**
     * 执行刮削和保存的通用方法
     */
    private suspend fun performScrapeAndSave(
        library: ResourceLibrary,
        client: RemoteFileClient,
        files: List<WebDavFile>,
        modifiedPaths: Set<String> = emptySet()
    ) {
        try {
            // 使用智能刮削器进行批量刮削（剧集聚类优化）
            Log.d(TAG, "使用智能刮削器进行批量刮削...")
            val coverCache = mutableMapOf<String, String?>() // 缓存目录封面，避免重复请求
            val fileByPath = files.associateBy { it.path }
            val modifiedCreatedAtMap = if (modifiedPaths.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    webDavMediaDao.getCreatedAtByFilePaths(modifiedPaths.toList())
                }.associate { it.filePath to it.createdAt }
            } else {
                emptyMap()
            }

            suspend fun enrichWithLocalCover(scraped: ScrapedMedia): ScrapedMedia {
                val directoryPath = scraped.filePath.substringBeforeLast("/", "")
                if (scraped.posterUrl == null && directoryPath.isNotEmpty()) {
                    val localCover = coverCache.getOrPut(directoryPath) {
                        client.getCoverImage(directoryPath)
                    }
                    if (localCover != null) {
                        return scraped.copy(posterUrl = localCover, source = "${scraped.source}+local")
                    }
                }
                return scraped
            }

            fun toEntity(scraped: ScrapedMedia): WebDavMediaEntity {
                val sourceFile = fileByPath[scraped.filePath]
                val fingerprint = if (sourceFile != null) {
                    FileFingerprintManager.generateFingerprint(sourceFile)
                } else {
                    // 兜底：极端情况下找不到源文件信息时，仍使用路径生成稳定指纹
                    FileFingerprintManager.generateFingerprint(scraped.filePath, 0, 0)
                }

                // 如果是修改的文件，保持原有的 createdAt
                val isModified = scraped.filePath in modifiedPaths
                val currentTime = System.currentTimeMillis()

                // 修改文件使用历史 createdAt，避免逐条查询数据库造成尾段卡顿
                val createdAt = if (isModified) modifiedCreatedAtMap[scraped.filePath] ?: currentTime else currentTime

                return WebDavMediaEntity(
                    id = scraped.id,
                    libraryId = library.id,
                    title = scraped.title,
                    originalTitle = scraped.originalTitle,
                    overview = scraped.overview,
                    posterUrl = scraped.posterUrl,
                    backdropUrl = scraped.backdropUrl,
                    year = scraped.year,
                    rating = scraped.rating,
                    genres = scraped.genres.joinToString(","),
                    type = scraped.type,  // 使用正确的类型字段
                    isMovie = scraped.isMovie,  // 保留用于向后兼容
                    seasonNumber = scraped.seasonNumber,
                    episodeNumber = scraped.episodeNumber,
                    tmdbId = scraped.tmdbId,  // 保存TMDB ID
                    filePath = scraped.filePath,
                    fileName = scraped.fileName,
                    fileFingerprint = fingerprint,
                    source = scraped.source,
                    createdAt = createdAt,  // 修改的文件保持原 createdAt
                    updatedAt = currentTime  // 总是更新 updatedAt
                )
            }

            val scrapedResults = smartScraper.scrapeBatchOptimized(
                files = files,
                onProgress = { current, total ->
                    _syncProgress.value = current to total
                    Log.d(TAG, "刮削进度: $current / $total")
                },
                client = client,
                onItemsReady = { partial ->
                    // 流式写库：刮削每成功一批就先显示到首页，避免必须等到100%
                    val partialEntities = partial
                        .map { enrichWithLocalCover(it) }
                        .map { toEntity(it) }
                    withContext(Dispatchers.IO) {
                        webDavMediaDao.insertAll(partialEntities)
                    }
                }
            )

            // 为每个结果尝试获取本地封面图片
            val entities = scrapedResults
                .map { enrichWithLocalCover(it) }
                .map { toEntity(it) }

            withContext(Dispatchers.IO) {
                // 最终再全量覆盖一次，确保任何漏掉的条目也能入库
                webDavMediaDao.insertAll(entities)
            }

            // 同步预拉单集信息（tmdbId + season），确保“刮削完成”时详情页就有单集封面/标题可读。
            // 拉取失败的季，再回退到后台队列慢慢补齐。
            val failedEpisodeTasks = preloadEpisodeMetadataNow(entities)

            // 将 tmdbId 放入后台补全队列：媒体主信息兜底补齐
            viewModelScope.launch(Dispatchers.IO) {
                val tmdbIds = entities
                    .mapNotNull { it.tmdbId?.toIntOrNull() }
                    .distinct()
                tmdbIds.forEach { tmdbMetadataSyncManager.enqueueMedia(it, priority = 0) }
                failedEpisodeTasks.forEach { (tmdbId, season) ->
                    tmdbMetadataSyncManager.enqueueSeasonEpisodes(tmdbId, season, priority = 0)
                }
            }

            Log.d(TAG, "刮削完成，共保存 ${entities.size} 条记录")
            _syncState.value = SyncState.Completed
        } catch (e: Exception) {
            Log.e(TAG, "performScrapeAndSave failed", e)
            _syncState.value = SyncState.Error(buildErrorMessage("刮削", e))
        }
    }

    private suspend fun preloadEpisodeMetadataNow(
        entities: List<WebDavMediaEntity>
    ): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val tasks = entities
            .asSequence()
            .filter { it.type != com.lomen.tv.domain.model.MediaType.MOVIE }
            .mapNotNull { entity ->
                val tmdbId = entity.tmdbId?.toIntOrNull() ?: return@mapNotNull null
                val season = (entity.seasonNumber ?: 1).coerceAtLeast(1)
                tmdbId to season
            }
            .distinct()
            .toList()

        if (tasks.isEmpty()) return@withContext emptyList()

        val scraper = TmdbScraper.getInstance()
        val failed = mutableListOf<Pair<Int, Int>>()
        val now = System.currentTimeMillis()

        tasks.forEach { (tmdbId, season) ->
            val episodes = runCatching {
                scraper.getTvSeasonEpisodes(tmdbId.toString(), season).orEmpty()
            }.getOrElse { emptyList() }

            if (episodes.isEmpty()) {
                failed += (tmdbId to season)
            } else {
                val cached = episodes.map { ep ->
                    TmdbEpisodeEntity(
                        tmdbId = tmdbId,
                        seasonNumber = season,
                        episodeNumber = ep.episodeNumber,
                        name = ep.name.takeIf { it.isNotBlank() },
                        overview = ep.overview?.takeIf { it.isNotBlank() },
                        stillUrl = ep.stillUrl,
                        airDate = ep.airDate,
                        runtimeMinutes = ep.runtime.takeIf { it > 0 },
                        updatedAt = now
                    )
                }
                tmdbEpisodeDao.upsertAll(cached)
            }
        }

        failed
    }

    fun clearLibraryMedia(libraryId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                webDavMediaDao.deleteByLibraryId(libraryId)
            }
        }
    }

    fun resetState() {
        _syncState.value = SyncState.Idle
        _syncProgress.value = 0 to 0
    }
}
