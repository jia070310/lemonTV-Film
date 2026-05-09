package com.lomen.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lomen.tv.data.scraper.FolderSeriesNameParser
import com.lomen.tv.data.local.database.dao.TmdbMediaDao
import com.lomen.tv.data.local.database.dao.WebDavMediaDao
import com.lomen.tv.data.local.database.entity.WebDavMediaEntity
import com.lomen.tv.data.repository.ResourceLibraryRepository
import com.lomen.tv.domain.model.ResourceLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 剧集系列数据类（合并同一部剧的集数）
data class TvShowSeries(
    val id: String,  // 使用剧集名称作为ID
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Float?,
    val overview: String?,
    val episodes: List<WebDavMediaEntity>,  // 该剧的所有集数
    val episodeCount: Int
)

@HiltViewModel
class ResourceLibraryViewModel @Inject constructor(
    private val webDavMediaDao: WebDavMediaDao,
    private val tmdbMediaDao: TmdbMediaDao,
    private val repository: ResourceLibraryRepository,
    private val mediaTypeSortPreferences: com.lomen.tv.data.preferences.MediaTypeSortPreferences,
    private val watchHistoryService: com.lomen.tv.domain.service.WatchHistoryService
) : ViewModel() {

    companion object {
        private const val TAG = "ResourceLibraryVM"
    }

    private val _currentLibraryMedia = MutableStateFlow<List<WebDavMediaEntity>>(emptyList())
    val currentLibraryMedia: StateFlow<List<WebDavMediaEntity>> = _currentLibraryMedia.asStateFlow()

    private val _movies = MutableStateFlow<List<WebDavMediaEntity>>(emptyList())
    val movies: StateFlow<List<WebDavMediaEntity>> = _movies.asStateFlow()

    private val _tvShows = MutableStateFlow<List<TvShowSeries>>(emptyList())
    val tvShows: StateFlow<List<TvShowSeries>> = _tvShows.asStateFlow()

    private val _anime = MutableStateFlow<List<TvShowSeries>>(emptyList())
    val anime: StateFlow<List<TvShowSeries>> = _anime.asStateFlow()

    private val _concerts = MutableStateFlow<List<WebDavMediaEntity>>(emptyList())
    val concerts: StateFlow<List<WebDavMediaEntity>> = _concerts.asStateFlow()

    private val _variety = MutableStateFlow<List<TvShowSeries>>(emptyList())
    val variety: StateFlow<List<TvShowSeries>> = _variety.asStateFlow()

    private val _documentaries = MutableStateFlow<List<WebDavMediaEntity>>(emptyList())
    val documentaries: StateFlow<List<WebDavMediaEntity>> = _documentaries.asStateFlow()

    private val _others = MutableStateFlow<List<WebDavMediaEntity>>(emptyList())
    val others: StateFlow<List<WebDavMediaEntity>> = _others.asStateFlow()

    // 媒体类型排序顺序（从DataStore读取）
    val mediaSortOrder: StateFlow<List<com.lomen.tv.domain.model.MediaType>> = 
        mediaTypeSortPreferences.sortOrder.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.lomen.tv.data.preferences.MediaTypeSortPreferences.DEFAULT_SORT_ORDER
        )

    private val _resourceLibraries = MutableStateFlow<List<ResourceLibrary>>(emptyList())
    val resourceLibraries: StateFlow<List<ResourceLibrary>> = _resourceLibraries.asStateFlow()

    private val _currentLibraryId = MutableStateFlow<String?>(null)
    val currentLibraryId: StateFlow<String?> = _currentLibraryId.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized")
        
        // 从DataStore加载资源库列表
        viewModelScope.launch {
            repository.libraries.collect { libraries ->
                Log.d(TAG, "Loaded ${libraries.size} libraries from DataStore")
                _resourceLibraries.value = libraries
            }
        }

        // 从DataStore加载当前选中的资源库ID
        viewModelScope.launch {
            repository.currentLibraryId.collect { id ->
                Log.d(TAG, "Current library ID from DataStore: $id")
                _currentLibraryId.value = id
            }
        }

        // 监听当前资源库的媒体变化
        viewModelScope.launch {
            currentLibraryId.collect { libraryId ->
                Log.d(TAG, "Library ID changed: $libraryId")
                if (libraryId != null) {
                    // 加载该资源库的所有媒体
                    launch {
                        Log.d(TAG, "Loading media for library: $libraryId")
                        webDavMediaDao.getByLibraryId(libraryId).collect { media ->
                            val tmdbIds = media.mapNotNull { it.tmdbId?.toIntOrNull() }.distinct()
                            val tmdbMap = if (tmdbIds.isNotEmpty()) {
                                withContext(Dispatchers.IO) {
                                    tmdbMediaDao.getByTmdbIds(tmdbIds)
                                }.associateBy { it.tmdbId.toString() }
                            } else {
                                emptyMap()
                            }

                            val mergedMedia = media.map { item ->
                                val tmdb = item.tmdbId?.let { tmdbMap[it] }
                                if (tmdb != null) {
                                    item.copy(
                                        posterUrl = tmdb.posterUrl ?: item.posterUrl,
                                        backdropUrl = tmdb.backdropUrl ?: item.backdropUrl,
                                        overview = tmdb.overview ?: item.overview,
                                        rating = tmdb.rating ?: item.rating
                                    )
                                } else {
                                    item
                                }
                            }

                            Log.d(TAG, "Loaded ${mergedMedia.size} media items from database")
                            _currentLibraryMedia.value = mergedMedia
                            
                            // 不做去重，直接使用原始媒体数据进行分类
                            // 剧集应该保留所有集数，不能去重
                            val categorized = mergedMedia.map { mediaItem ->
                                val titleLower = mediaItem.title.lowercase()
                                val genresLower = (mediaItem.genres ?: "").lowercase()
                                val isAnimeByKeyword =
                                    titleLower.contains("动漫") ||
                                        titleLower.contains("动画") ||
                                        titleLower.contains("番剧") ||
                                        titleLower.contains("anime") ||
                                        genresLower.contains("animation") ||
                                        genresLower.contains("anime")

                                // 优先采用刮削/目录写入的 [MediaType]，避免「纪录片目录 + TMDB 判成剧」在首页被标题关键词二次误判
                                val category = when (mediaItem.type) {
                                    com.lomen.tv.domain.model.MediaType.ANIME ->
                                        com.lomen.tv.domain.model.MediaType.ANIME
                                    com.lomen.tv.domain.model.MediaType.VARIETY ->
                                        com.lomen.tv.domain.model.MediaType.VARIETY
                                    com.lomen.tv.domain.model.MediaType.DOCUMENTARY ->
                                        com.lomen.tv.domain.model.MediaType.DOCUMENTARY
                                    com.lomen.tv.domain.model.MediaType.CONCERT ->
                                        com.lomen.tv.domain.model.MediaType.CONCERT
                                    com.lomen.tv.domain.model.MediaType.MOVIE ->
                                        com.lomen.tv.domain.model.MediaType.MOVIE
                                    com.lomen.tv.domain.model.MediaType.OTHER ->
                                        com.lomen.tv.domain.model.MediaType.OTHER
                                    com.lomen.tv.domain.model.MediaType.TV_SHOW -> {
                                        when {
                                            isAnimeByKeyword ->
                                                com.lomen.tv.domain.model.MediaType.ANIME
                                            titleLower.contains("综艺") ||
                                                titleLower.contains("真人秀") ||
                                                titleLower.contains("脱口秀") ||
                                                titleLower.contains("选秀") ||
                                                genresLower.contains("variety") ||
                                                genresLower.contains("reality") ->
                                                com.lomen.tv.domain.model.MediaType.VARIETY
                                            titleLower.contains("纪录片") ||
                                                titleLower.contains("纪录") ||
                                                genresLower.contains("documentary") ->
                                                com.lomen.tv.domain.model.MediaType.DOCUMENTARY
                                            else ->
                                                com.lomen.tv.domain.model.MediaType.TV_SHOW
                                        }
                                    }
                                }

                                mediaItem to category
                            }

                            // 按分类填充不同列表
                            _movies.value = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.MOVIE }
                                .map { it.first }
                            val varietyList = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.VARIETY }
                                .map { it.first }
                            _variety.value = groupVarietyByShow(varietyList)
                            _concerts.value = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.CONCERT }
                                .map { it.first }
                            _documentaries.value = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.DOCUMENTARY }
                                .map { it.first }
                            _others.value = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.OTHER }
                                .map { it.first }

                            // 动漫：按系列分组（与电视剧一致）
                            val animeList = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.ANIME }
                                .map { it.first }
                            _anime.value = groupTvShowsBySeries(animeList)
                            
                            // 电视剧：合并同一部剧的集数（仅 TV_SHOW 分类）
                            val tvShowList = categorized
                                .filter { it.second == com.lomen.tv.domain.model.MediaType.TV_SHOW }
                                .map { it.first }
                            _tvShows.value = groupTvShowsBySeries(tvShowList)
                            
                            Log.d(TAG, "Movies: ${_movies.value.size}, TV Shows: ${_tvShows.value.size}, Anime: ${_anime.value.size}, " +
                                    "Concerts: ${_concerts.value.size}, Variety: ${_variety.value.size}, " +
                                    "Documentaries: ${_documentaries.value.size}, Others: ${_others.value.size}")
                        }
                    }
                } else {
                    Log.d(TAG, "No library selected, clearing media")
                    _currentLibraryMedia.value = emptyList()
                    _movies.value = emptyList()
                    _tvShows.value = emptyList()
                    _anime.value = emptyList()
                    _concerts.value = emptyList()
                    _variety.value = emptyList()
                    _documentaries.value = emptyList()
                    _others.value = emptyList()
                }
            }
        }
    }

    fun addLibrary(library: ResourceLibrary) {
        viewModelScope.launch {
            repository.addLibrary(library)
        }
    }

    fun updateLibrary(library: ResourceLibrary) {
        viewModelScope.launch {
            repository.updateLibrary(library)
        }
    }

    fun removeLibrary(libraryId: String) {
        viewModelScope.launch {
            // 先删除该资源库的所有媒体数据
            webDavMediaDao.deleteByLibraryId(libraryId)
            Log.d(TAG, "Deleted all media for library: $libraryId")
            // 再删除资源库本身
            repository.removeLibrary(libraryId)
            Log.d(TAG, "Deleted library: $libraryId")
            // 清空播放记录
            try {
                watchHistoryService.clearAllWatchHistory()
                Log.d(TAG, "Cleared all watch history")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear watch history", e)
            }
        }
    }

    fun setCurrentLibrary(libraryId: String?) {
        viewModelScope.launch {
            repository.setCurrentLibrary(libraryId)
        }
    }

    fun getLibraryById(libraryId: String): ResourceLibrary? {
        return resourceLibraries.value.find { it.id == libraryId }
    }

    fun getCurrentLibrary(): ResourceLibrary? {
        val currentId = currentLibraryId.value ?: return null
        return getLibraryById(currentId)
    }

    /**
     * 清除当前资源库的媒体缓存（用于设置页"一键清除缓存"）
     * 删除数据库中的webdav_media记录，同时清空播放记录
     */
    fun clearAllCache() {
        viewModelScope.launch {
            val libraryId = currentLibraryId.value
            if (libraryId != null) {
                webDavMediaDao.deleteByLibraryId(libraryId)
                Log.d(TAG, "Cleared media cache for library: $libraryId")
            } else {
                Log.d(TAG, "No current library, skip clearAllCache")
            }
            // 清空播放记录
            try {
                watchHistoryService.clearAllWatchHistory()
                Log.d(TAG, "Cleared all watch history")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear watch history", e)
            }
        }
    }

    // 将剧集按系列分组（按标题分组）
    private fun groupTvShowsBySeries(tvShows: List<WebDavMediaEntity>): List<TvShowSeries> {
        // 按“归一化标题”分组，避免 "卧底娇娃" 与 "卧底娇娃 ()" 被拆成两张卡片
        val grouped = tvShows.groupBy { entity ->
            canonicalSeriesTitle(entity.title)
        }
        
        return grouped.map { (title, episodes) ->
            // 封面优先使用组内任一有图条目，避免“第一集无图导致整组无封面”
            val withPoster = episodes.firstOrNull { !it.posterUrl.isNullOrBlank() } ?: episodes.first()
            val bestRated = episodes.maxWithOrNull(
                compareBy<WebDavMediaEntity> { it.rating ?: 0f }.thenByDescending { it.updatedAt }
            ) ?: episodes.first()
            TvShowSeries(
                id = title,  // 使用标题作为ID
                title = title,
                posterUrl = withPoster.posterUrl,
                year = withPoster.year ?: bestRated.year,
                rating = bestRated.rating,
                overview = bestRated.overview ?: withPoster.overview,
                episodes = episodes.sortedBy { it.episodeNumber },  // 按集数排序
                // 采用“已更新到第X集”的口径：优先最大集号，缺失时回退数量
                episodeCount = episodes.mapNotNull { it.episodeNumber }.maxOrNull() ?: episodes.size
            )
        }.sortedByDescending { it.episodeCount }  // 按集数排序，集数多的在前
    }

    private fun canonicalSeriesTitle(rawTitle: String): String {
        val strippedSeason = FolderSeriesNameParser.stripAllSeasonMarkers(rawTitle.trim())
        return strippedSeason
            // 清理空括号：(), （）, [] 等，避免产生“同名+空括号”重复卡片
            // 注意：[] {} 的右括号在正则里需要转义，否则会触发 PatternSyntaxException
            .replace(Regex("""\(\s*\)|（\s*）|\[\s*\]|\{\s*\}"""), "")
            // 统一多余空白
            .replace(Regex("""\s+"""), " ")
            // 去除结尾常见分隔符
            .trim()
            .trimEnd('.', '-', '_', '·')
            .ifBlank { rawTitle.trim() }
    }

    /**
     * 综艺：同一节目多季合并为一张卡片与同一详情入口；季与分集在详情页切换。
     * 分组键为去掉季标记后的节目名；入口 id 取按季、集排序后的首条真实 [WebDavMediaEntity.id]。
     */
    private fun groupVarietyByShow(items: List<WebDavMediaEntity>): List<TvShowSeries> {
        if (items.isEmpty()) return emptyList()
        val grouped = items.groupBy { entity ->
            FolderSeriesNameParser.stripAllSeasonMarkers(entity.title.trim()).ifBlank { entity.title.trim() }
        }
        return grouped.map { (canonicalTitle, episodes) ->
            val sorted = episodes.sortedWith(
                compareBy<WebDavMediaEntity> { it.seasonNumber ?: 0 }
                    .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
            )
            val withPoster = sorted.firstOrNull { !it.posterUrl.isNullOrBlank() } ?: sorted.first()
            val bestRated = sorted.maxWithOrNull(
                compareBy<WebDavMediaEntity> { it.rating ?: 0f }.thenByDescending { it.updatedAt }
            ) ?: sorted.first()
            TvShowSeries(
                id = sorted.first().id,
                title = canonicalTitle,
                posterUrl = withPoster.posterUrl,
                year = withPoster.year ?: bestRated.year,
                rating = bestRated.rating,
                overview = bestRated.overview ?: withPoster.overview,
                episodes = sorted,
                episodeCount = sorted.size
            )
        }.sortedByDescending { series ->
            series.episodes.maxOfOrNull { it.updatedAt } ?: 0L
        }
    }
}
