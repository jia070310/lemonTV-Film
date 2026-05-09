package com.lomen.tv.data.scraper

import android.util.Log
import com.lomen.tv.data.remote.RemoteFileClient
import com.lomen.tv.data.webdav.WebDavFile
import com.lomen.tv.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 智能批量刮削器
 * 优化策略：
 * 1. 剧集聚类：同一部剧的多集只查询一次API
 * 2. 内存缓存：已查询的结果缓存
 * 3. 并发控制：限制并发数避免API限流
 */
class SmartMediaScraper {
    companion object {
        private const val TAG = "SmartMediaScraper"
        // 慢网/TV 端环境下 TMDB 请求可能 >12s，过短会导致大量“回退本地信息”，进而没有封面/简介/单集信息
        private const val CONCURRENT_LIMIT = 10
        private const val SINGLE_SERIES_TIMEOUT_MS = 35_000L
    }

    /**
     * 最终展示类型：**目录分库类型** > 文件名解析的细分类型（纪录/综艺/动漫等）> TMDB 推断的 TV/电影。
     * 避免 TMDB multi 把「纪录片目录下的节目」强行压成 [MediaType.TV_SHOW]。
     */
    private fun resolveFinalScrapedType(
        filePath: String,
        mediaInfo: MediaInfo,
        tmdbInferredType: MediaType
    ): MediaType {
        LibraryFolderTypeHints.detectMediaTypeFromPath(filePath)?.let { return it }
        when (mediaInfo.type) {
            MediaType.DOCUMENTARY,
            MediaType.VARIETY,
            MediaType.CONCERT,
            MediaType.ANIME -> return mediaInfo.type
            else -> { }
        }
        return tmdbInferredType
    }

    private val tmdbScraper = TmdbScraper.getInstance()
    private val doubanScraper = DoubanScraper()

    // 内存缓存：剧名 -> 刮削结果
    private val cache = ConcurrentHashMap<String, ScrapeCacheEntry>()

    data class ScrapeCacheEntry(
        val result: ScrapedMedia?,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 智能批量刮削
     * 1. 先按剧名聚类
     * 2. 每部剧只查询一次API
     * 3. 应用到该剧的所有集数
     * 
     * @param files 文件列表
     * @param client WebDAV 客户端，用于读取本地 nfo 文件（可选）
     * @param onProgress 进度回调
     */
    suspend fun scrapeBatchOptimized(
        files: List<WebDavFile>,
        onProgress: (Int, Int) -> Unit,
        client: RemoteFileClient? = null,
        onItemsReady: suspend (List<ScrapedMedia>) -> Unit = {}
    ): List<ScrapedMedia> = withContext(Dispatchers.IO) {
        val total = files.size
        Log.d(TAG, "开始智能批量刮削，共 $total 个文件")

        // 第一步：按剧名聚类
        val clusters = clusterBySeries(files)
        Log.d(TAG, "聚类完成，共 ${clusters.size} 部剧/电影")

        // 第二步：流式并发刮削每部剧的主信息（限制并发数 + 单条超时）
        val processedCount = AtomicInteger(0)
        val seriesResults = mutableMapOf<String, ScrapedMedia?>()
        val seriesResultsLock = Mutex()
        val fileResults = mutableMapOf<String, ScrapedMedia>()
        val fileResultsLock = Mutex()
        val semaphore = Semaphore(CONCURRENT_LIMIT)

        coroutineScope {
            clusters.entries.map { (seriesKey, clusterFiles) ->
                async {
                    semaphore.withPermit {
                        val result = try {
                            // 检查缓存
                            val cached = cache[seriesKey]
                            if (cached != null && System.currentTimeMillis() - cached.timestamp < 30 * 60 * 1000) {
                                Log.d(TAG, "使用缓存: $seriesKey")
                                cached.result
                            } else {
                                val scraped = withTimeoutOrNull(SINGLE_SERIES_TIMEOUT_MS) {
                                    scrapeSeriesInfo(clusterFiles.first(), client)
                                }
                                if (scraped == null) {
                                    Log.w(TAG, "刮削超时/失败，回退本地信息: $seriesKey")
                                }
                                cache[seriesKey] = ScrapeCacheEntry(scraped)
                                scraped
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "刮削异常，回退本地信息: $seriesKey", e)
                            null
                        }

                        seriesResultsLock.withLock {
                            seriesResults[seriesKey] = result
                        }
                        val partialReady = clusterFiles.mapNotNull { file ->
                            createScrapedMedia(file, result)
                        }
                        if (partialReady.isNotEmpty()) {
                            fileResultsLock.withLock {
                                partialReady.forEach { fileResults[it.filePath] = it }
                            }
                            onItemsReady(partialReady)
                        }
                        val current = processedCount.addAndGet(clusterFiles.size).coerceAtMost(total)
                        onProgress(current, total)
                    }
                }
            }.awaitAll()
        }

        // 第三步：按输入顺序汇总完整结果
        val finalResults = files.mapNotNull { file -> fileResults[file.path] }

        Log.d(TAG, "智能批量刮削完成: ${finalResults.size} / $total")
        finalResults
    }

    /**
     * 按剧名聚类
     * 同一部剧的不同集数归为一类
     */
    private fun clusterBySeries(files: List<WebDavFile>): Map<String, List<WebDavFile>> {
        return files.groupBy { getSeriesKey(it) }
    }

    /**
     * 获取剧集的唯一标识键
     * 用于判断哪些文件属于同一部剧
     */
    private fun getSeriesKey(file: WebDavFile): String {
        val parentFolder = file.path.substringBeforeLast("/", "").substringAfterLast("/", "")
        val mediaInfo = MediaInfoExtractor.extract(file.name, parentFolder, file.path)

        // 使用"父文件夹名_剧名_季数"作为键，确保不同文件夹下的剧集不会被错误聚类
        return if (mediaInfo.season != null) {
            "${parentFolder}_${mediaInfo.title}_S${mediaInfo.season}"
        } else {
            // 电影使用"父文件夹名_剧名_年份"作为键
            "${parentFolder}_${mediaInfo.title}_${mediaInfo.year ?: ""}"
        }
    }

    /**
     * 刮削剧集/电影的主信息
     * 只查询一次API获取该剧的基本信息
     * 
     * 核心原则：
     * 1. 优先搜索本地 .nfo 文件
     * 2. 其次使用TMDB的multi搜索，让TMDB自己判断类型
     */
    private suspend fun scrapeSeriesInfo(
        file: WebDavFile,
        client: RemoteFileClient? = null
    ): ScrapedMedia? {
        val parentFolder = file.path.substringBeforeLast("/", "").substringAfterLast("/", "")
        val mediaInfo = MediaInfoExtractor.extract(file.name, parentFolder, file.path)

        Log.d(TAG, "刮削媒体信息: ${mediaInfo.title}, year: ${mediaInfo.year}, type: ${mediaInfo.type}, hasEpisode: ${mediaInfo.season != null || mediaInfo.episode != null}, tmdbId: ${mediaInfo.tmdbId}")

        // 第一步：优先搜索本地 .nfo 文件
        val nfoResult = parseNfoFile(file, client)
        if (nfoResult != null) {
            Log.d(TAG, "使用本地 .nfo 文件信息: ${nfoResult.title}")
            return nfoResult
        }

        // 特殊处理：演唱会/音乐会
        // 对于演唱会，尝试搜索艺人名称，然后使用本地封面
        if (mediaInfo.type == MediaType.CONCERT) {
            Log.d(TAG, "检测到演唱会类型，尝试特殊处理: ${mediaInfo.title}")
            val concertResult = scrapeConcert(mediaInfo, file)
            if (concertResult != null) {
                Log.d(TAG, "演唱会刮削成功: ${concertResult.title}")
                return concertResult
            }
            Log.d(TAG, "演唱会刮削失败，继续正常流程")
        }

        // 优先使用tmdbId获取信息
        if (mediaInfo.tmdbId != null) {
            Log.d(TAG, "使用tmdbId获取信息: ${mediaInfo.tmdbId}")
            // 根据媒体类型获取信息
            val mediaType = if (mediaInfo.season != null || mediaInfo.episode != null) "tv" else "movie"
            val tmdbResult = tmdbScraper.getMediaByTmdbId(mediaInfo.tmdbId, mediaType)
            if (tmdbResult != null) {
                Log.d(TAG, "根据tmdbId获取信息成功: ${tmdbResult.title}")
                return ScrapedMedia(
                    id = tmdbResult.id,
                    title = tmdbResult.title,
                    originalTitle = tmdbResult.originalTitle,
                    overview = tmdbResult.overview,
                    posterUrl = tmdbResult.posterUrl,
                    backdropUrl = tmdbResult.backdropUrl,
                    year = tmdbResult.year,
                    rating = tmdbResult.rating,
                    genres = tmdbResult.genres,
                    type = resolveFinalScrapedType(file.path, mediaInfo, mediaInfo.type),
                    seasonNumber = mediaInfo.season,
                    episodeNumber = mediaInfo.episode,
                    filePath = file.path,
                    fileName = file.name,
                    source = "tmdb"
                )
            }
            Log.d(TAG, "根据tmdbId获取信息失败，继续正常流程")
        }

        // 第一步：优先使用TMDB的multi搜索，让TMDB自己判断类型
        // 对于有季数信息的剧集，添加季数到搜索标题中，提高匹配准确率
        val searchTitle = if (mediaInfo.season != null && mediaInfo.season > 1) {
            "${mediaInfo.title} 第${mediaInfo.season}季"
        } else {
            mediaInfo.title
        }
        val (multiResult, tmdbMediaType) = tmdbScraper.searchMulti(searchTitle, mediaInfo.year)
        
        if (multiResult != null && tmdbMediaType != null) {
            // 改进的类型判断逻辑：
            // 1. 如果TMDB明确返回tv类型，强制归类为TV_SHOW
            // 2. 如果有季/集信息，归类为TV_SHOW
            // 3. 否则归类为MOVIE
            val hasEpisodeInfo = mediaInfo.season != null || mediaInfo.episode != null
            val finalType = when {
                tmdbMediaType == "tv" -> {
                    // TMDB明确返回电视剧，强制归类为TV_SHOW
                    MediaType.TV_SHOW
                }
                hasEpisodeInfo -> {
                    // 有季/集信息，归类为TV_SHOW（包括电视剧、综艺、纪录片剧集等）
                    MediaType.TV_SHOW
                }
                else -> {
                    // 没有季/集信息，归类为MOVIE（包括电影、演唱会、纪录片电影等）
                    MediaType.MOVIE
                }
            }
            
            // 关键修复：如果文件结构显示是电视剧，但TMDB返回了电影结果，重新搜索TV表
            var finalResult = multiResult
            if (finalType == MediaType.TV_SHOW && tmdbMediaType != "tv") {
                // 有季/集信息，但TMDB返回了电影结果，重新搜索TV表
                val tvResult = tmdbScraper.searchTv(mediaInfo.title, mediaInfo.year)
                if (tvResult != null) {
                    finalResult = tvResult
                }
            }
            
            // 对于电视剧且有季数信息，尝试获取对应季的信息
            if (finalType == MediaType.TV_SHOW && mediaInfo.season != null) {
                val tvSeasonResult = tmdbScraper.getTvSeasonDetails(finalResult.id, mediaInfo.season)
                if (tvSeasonResult != null) {
                    // 只使用季信息中的封面和其他信息，保留原始电视剧标题
                    finalResult = finalResult.copy(
                        posterUrl = tvSeasonResult.posterUrl ?: finalResult.posterUrl,
                        backdropUrl = tvSeasonResult.backdropUrl ?: finalResult.backdropUrl,
                        overview = tvSeasonResult.overview ?: finalResult.overview,
                        rating = tvSeasonResult.rating ?: finalResult.rating
                    )
                } else {
                    // 如果获取季信息失败，尝试获取总剧信息
                    val tvDetailsResult = tmdbScraper.getTvDetails(finalResult.id)
                    if (tvDetailsResult != null) {
                        finalResult = tvDetailsResult
                    }
                }
            }
            
            // 如果搜索返回的结果没有封面，尝试获取详情
            finalResult = if (finalResult.posterUrl == null && finalResult.backdropUrl == null) {
                if (finalType == MediaType.TV_SHOW) {
                    tmdbScraper.getTvDetails(finalResult.id) ?: finalResult
                } else {
                    tmdbScraper.getMovieDetails(finalResult.id) ?: finalResult
                }
            } else {
                finalResult
            }
            
            return ScrapedMedia(
                id = finalResult.id,
                title = finalResult.title,
                originalTitle = finalResult.originalTitle,
                overview = finalResult.overview,
                posterUrl = finalResult.posterUrl,
                backdropUrl = finalResult.backdropUrl,
                year = finalResult.year,
                rating = finalResult.rating,
                genres = finalResult.genres,
                type = resolveFinalScrapedType(file.path, mediaInfo, finalType),
                seasonNumber = mediaInfo.season,
                episodeNumber = mediaInfo.episode,
                filePath = file.path,
                fileName = file.name,
                source = "tmdb",
                tmdbId = mediaInfo.tmdbId ?: finalResult.id  // 优先使用文件名中的tmdbId，否则使用TMDB返回的ID
            )
        }

        // 第二步：如果multi搜索失败，根据文件结构决定搜索方向
        // 有季/集信息 → 搜TV表
        // 没有季/集信息 → 搜Movie表
        val hasEpisodeInfo = mediaInfo.season != null || mediaInfo.episode != null
        val tmdbResult = if (hasEpisodeInfo) {
            // 有季/集信息，优先搜TV表，包含季数信息
            val tvSearchTitle = if (mediaInfo.season != null && mediaInfo.season > 1) {
                "${mediaInfo.title} 第${mediaInfo.season}季"
            } else {
                mediaInfo.title
            }
            tmdbScraper.searchTv(tvSearchTitle, mediaInfo.year)
        } else {
            // 没有季/集信息，优先搜Movie表
            tmdbScraper.searchMovie(mediaInfo.title, mediaInfo.year)
        }

        if (tmdbResult != null) {
            // 核心原则：类型由"文件结构"决定
            // 有季/集信息 → TV_SHOW，没有 → MOVIE
            val finalType = if (hasEpisodeInfo) {
                MediaType.TV_SHOW  // 有季/集信息，归类为TV_SHOW
            } else {
                MediaType.MOVIE    // 没有季/集信息，归类为MOVIE
            }
            
            // 对于电视剧且有季数信息，尝试获取对应季的信息
            var finalResult = tmdbResult
            if (finalType == MediaType.TV_SHOW && mediaInfo.season != null) {
                val tvSeasonResult = tmdbScraper.getTvSeasonDetails(finalResult.id, mediaInfo.season)
                if (tvSeasonResult != null) {
                    // 只使用季信息中的封面和其他信息，保留原始电视剧标题
                    finalResult = finalResult.copy(
                        posterUrl = tvSeasonResult.posterUrl ?: finalResult.posterUrl,
                        backdropUrl = tvSeasonResult.backdropUrl ?: finalResult.backdropUrl,
                        overview = tvSeasonResult.overview ?: finalResult.overview,
                        rating = tvSeasonResult.rating ?: finalResult.rating
                    )
                } else {
                    // 如果获取季信息失败，尝试获取总剧信息
                    val tvDetailsResult = tmdbScraper.getTvDetails(finalResult.id)
                    if (tvDetailsResult != null) {
                        finalResult = tvDetailsResult
                    }
                }
            } else if (finalResult.posterUrl == null && finalResult.backdropUrl == null) {
                // 如果搜索返回的结果没有封面，尝试获取详情
                finalResult = if (finalType == MediaType.TV_SHOW) {
                    tmdbScraper.getTvDetails(finalResult.id) ?: finalResult
                } else {
                    tmdbScraper.getMovieDetails(finalResult.id) ?: finalResult
                }
            }
            
            return ScrapedMedia(
                id = finalResult.id,
                title = finalResult.title,
                originalTitle = finalResult.originalTitle,
                overview = finalResult.overview,
                posterUrl = finalResult.posterUrl,
                backdropUrl = finalResult.backdropUrl,
                year = finalResult.year,
                rating = finalResult.rating,
                genres = finalResult.genres,
                type = resolveFinalScrapedType(file.path, mediaInfo, finalType),
                seasonNumber = mediaInfo.season,
                episodeNumber = mediaInfo.episode,
                filePath = file.path,
                fileName = file.name,
                source = "tmdb",
                tmdbId = mediaInfo.tmdbId ?: finalResult.id  // 优先使用文件名中的tmdbId，否则使用TMDB返回的ID
            )
        }

        // TMDB失败，尝试豆瓣
        val doubanResult = doubanScraper.search(mediaInfo.title, mediaInfo.year)
        if (doubanResult != null) {
            return ScrapedMedia(
                id = doubanResult.id,
                title = doubanResult.title,
                originalTitle = doubanResult.originalTitle,
                overview = doubanResult.overview,
                posterUrl = doubanResult.posterUrl,
                backdropUrl = doubanResult.backdropUrl,
                year = doubanResult.year,
                rating = doubanResult.rating,
                genres = doubanResult.genres,
                type = resolveFinalScrapedType(file.path, mediaInfo, mediaInfo.type),
                seasonNumber = mediaInfo.season,
                episodeNumber = mediaInfo.episode,
                filePath = file.path,
                fileName = file.name,
                source = "douban",
                tmdbId = mediaInfo.tmdbId
            )
        }

        return null
    }

    /**
     * 特殊处理演唱会刮削
     * 策略：
     * 1. 尝试多种搜索组合：
     *    - 艺人名 + 年份（如"刘德华99"）
     *    - 艺人名 + 年份 + "演唱会"（如"刘德华1999演唱会"）
     *    - 艺人名 + "演唱会" + 年份（如"刘德华演唱会99"）
     * 2. 如果找到演唱会信息，使用演唱会封面
     * 3. 如果找不到，尝试搜索艺人信息作为备选
     */
    private suspend fun scrapeConcert(
        mediaInfo: MediaInfo,
        file: WebDavFile
    ): ScrapedMedia? {
        Log.d(TAG, "开始刮削演唱会: ${mediaInfo.title}")

        // 直接使用完整标题搜索（保留"演唱会"关键词）
        val fullTitle = mediaInfo.title
        Log.d(TAG, "使用完整标题搜索: '$fullTitle'")
        
        // 策略1：直接使用完整标题搜索
        val result1 = tmdbScraper.searchMovie(fullTitle, mediaInfo.year)
        if (result1 != null) {
            Log.d(TAG, "策略1成功：找到演唱会 '$fullTitle'")
            return createConcertMedia(result1, mediaInfo, file, "tmdb")
        }

        // 策略2：使用原始文件名搜索
        val originalFileName = file.name.replace(Regex("\\.[^.]+$"), "") // 去掉扩展名
        if (originalFileName != fullTitle) {
            Log.d(TAG, "策略2：使用原始文件名搜索: '$originalFileName'")
            val result2 = tmdbScraper.searchMovie(originalFileName, mediaInfo.year)
            if (result2 != null) {
                Log.d(TAG, "策略2成功：找到演唱会 '$originalFileName'")
                return createConcertMedia(result2, mediaInfo, file, "tmdb")
            }
        }

        // 策略3：尝试搜索 "刘德华演唱会99" 格式
        if (mediaInfo.year != null) {
            val yearShort = mediaInfo.year.toString().takeLast(2)  // 1999 -> 99
            val searchQuery3 = "刘德华演唱会$yearShort"
            Log.d(TAG, "策略3：搜索 '$searchQuery3'")
            
            val result3 = tmdbScraper.searchMovie(searchQuery3, mediaInfo.year)
            if (result3 != null) {
                Log.d(TAG, "策略3成功：找到演唱会 '$searchQuery3'")
                return createConcertMedia(result3, mediaInfo, file, "tmdb")
            }
        }

        // 策略4：搜索艺人信息作为备选
        Log.d(TAG, "所有演唱会搜索策略失败，尝试搜索艺人信息")
        val artistResult = tmdbScraper.searchPerson("刘德华")
        if (artistResult != null) {
            Log.d(TAG, "找到艺人信息: ${artistResult.name}")
            
            val concertTitle = buildString {
                append(artistResult.name)
                if (mediaInfo.year != null) {
                    append(" ")
                    append(mediaInfo.year)
                }
                append(" 演唱会")
            }

            return ScrapedMedia(
                id = "concert_${file.path.hashCode()}",
                title = concertTitle,
                originalTitle = null,
                overview = "${artistResult.name}的演唱会",
                posterUrl = artistResult.profileUrl,
                backdropUrl = null,
                year = mediaInfo.year,
                rating = null,
                genres = listOf("音乐", "演唱会"),
                type = MediaType.CONCERT,
                seasonNumber = null,
                episodeNumber = null,
                filePath = file.path,
                fileName = file.name,
                source = "tmdb+artist"
            )
        }

        // 所有策略都失败，使用本地信息
        Log.w(TAG, "所有搜索策略都失败: ${mediaInfo.title}")
        return null
    }

    /**
     * 判断搜索结果是否是演唱会
     * 通过检查标题是否包含演唱会相关关键词
     * 注意：放宽检查条件，只要找到结果就认为是演唱会（因为我们已经用演唱会关键词搜索了）
     */
    private fun isConcertResult(result: ScrapeResult): Boolean {
        val title = result.title.lowercase()
        val overview = (result.overview ?: "").lowercase()
        
        val concertKeywords = listOf("演唱会", "音乐会", "concert", "live", "巡演", "巡回", "音乐")
        
        // 首先检查是否包含演唱会关键词
        val hasConcertKeyword = concertKeywords.any { keyword ->
            title.contains(keyword) || overview.contains(keyword)
        }
        
        if (hasConcertKeyword) {
            return true
        }
        
        // 如果没有明显的演唱会关键词，但类型是音乐相关，也认为是演唱会
        val musicGenres = listOf("音乐", "music", "歌舞", "演唱会")
        val hasMusicGenre = result.genres.any { genre ->
            musicGenres.any { musicKeyword ->
                genre.contains(musicKeyword, ignoreCase = true)
            }
        }
        
        // 放宽条件：只要有结果，且年份匹配，就认为是演唱会
        // （因为我们已经用演唱会关键词搜索了）
        return true
    }

    /**
     * 创建演唱会媒体对象
     */
    private fun createConcertMedia(
        result: ScrapeResult,
        mediaInfo: MediaInfo,
        file: WebDavFile,
        source: String
    ): ScrapedMedia {
        return ScrapedMedia(
            id = result.id,
            title = result.title,
            originalTitle = result.originalTitle,
            overview = result.overview,
            posterUrl = result.posterUrl,
            backdropUrl = result.backdropUrl,
            year = result.year,
            rating = result.rating,
            genres = result.genres.ifEmpty { listOf("音乐", "演唱会") },
            type = MediaType.CONCERT,
            seasonNumber = null,
            episodeNumber = null,
            filePath = file.path,
            fileName = file.name,
            source = source,
            tmdbId = mediaInfo.tmdbId
        )
    }

    /**
     * 为单个文件创建完整的ScrapedMedia
     * 使用剧集的主信息，但更新集数等文件特定信息
     */
    private fun createScrapedMedia(
        file: WebDavFile,
        seriesInfo: ScrapedMedia?
    ): ScrapedMedia? {
        val parentFolder = file.path.substringBeforeLast("/", "").substringAfterLast("/", "")
        val mediaInfo = MediaInfoExtractor.extract(file.name, parentFolder, file.path)

        return if (seriesInfo != null) {
            // 使用剧集信息，但更新文件特定的集数；类型以当前路径为准（支持「按类型分目录」）
            seriesInfo.copy(
                id = "${seriesInfo.id}_${file.path.hashCode()}", // 确保每个文件ID唯一
                type = mediaInfo.type,
                seasonNumber = mediaInfo.season,
                episodeNumber = mediaInfo.episode,
                filePath = file.path,
                fileName = file.name,
                tmdbId = seriesInfo.tmdbId ?: mediaInfo.tmdbId
            )
        } else {
            // 刮削失败，使用本地信息（displayTitle保留完整名称）
            ScrapedMedia(
                id = file.path.hashCode().toString(),
                title = mediaInfo.displayTitle,
                originalTitle = null,
                overview = null,
                posterUrl = null,
                backdropUrl = null,
                year = mediaInfo.year,
                rating = null,
                genres = emptyList(),
                type = mediaInfo.type,  // 使用从文件名提取的类型
                seasonNumber = mediaInfo.season,
                episodeNumber = mediaInfo.episode,
                filePath = file.path,
                fileName = file.name,
                source = "local",
                tmdbId = mediaInfo.tmdbId
            )
        }
    }


    /**
     * 解析本地 .nfo 文件
     * 搜索规则：
     * 1. 首先搜索与视频文件同名的 .nfo 文件（如 movie.mp4 -> movie.nfo）
     * 2. 然后搜索文件夹内的 movie.nfo 或 tvshow.nfo
     * 3. 解析 nfo 文件中的标题、年份、简介、海报等信息
     */
    private suspend fun parseNfoFile(
        file: WebDavFile,
        client: RemoteFileClient? = null
    ): ScrapedMedia? {
        if (client == null) {
            // 没有 WebDAV 客户端，无法读取 nfo 文件
            return null
        }
        
        try {
            val fileNameWithoutExt = file.name.substringBeforeLast(".", file.name)
            val parentPath = file.path.substringBeforeLast("/", "")

            // 只请求一次目录列表，避免每个候选 .nfo 都走一遍 HEAD 导致高延迟卡顿
            val dirFiles = client.listFiles(parentPath).getOrNull().orEmpty()
            if (dirFiles.isEmpty()) return null
            val nfoByLowerName = dirFiles
                .asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".nfo", ignoreCase = true) }
                .associateBy { it.name.lowercase() }
            if (nfoByLowerName.isEmpty()) return null

            // 候选文件按优先级匹配
            val candidateNames = listOf(
                "$fileNameWithoutExt.nfo",
                "movie.nfo",
                "tvshow.nfo"
            )

            for (candidate in candidateNames) {
                val matched = nfoByLowerName[candidate.lowercase()] ?: continue
                val nfoPath = matched.path
                try {
                    Log.d(TAG, "找到 .nfo 文件: $nfoPath")
                    val content = client.readTextFile(nfoPath)
                    if (!content.isNullOrBlank()) {
                        val result = parseNfoContent(content, file)
                        if (result != null) {
                            Log.d(TAG, "成功解析 .nfo 文件: ${result.title}")
                            return result
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "读取 .nfo 文件失败: $nfoPath", e)
                }
            }
            
            Log.d(TAG, "未找到有效的 .nfo 文件: ${file.name}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "解析 .nfo 文件失败: ${file.path}", e)
            return null
        }
    }

    /**
     * 从 nfo 文件内容解析媒体信息
     * 支持 Kodi 格式的 nfo 文件
     */
    private fun parseNfoContent(content: String, file: WebDavFile): ScrapedMedia? {
        try {
            // 提取标题
            val titleRegex = "<title>([^<]+)</title>".toRegex(RegexOption.IGNORE_CASE)
            val title = titleRegex.find(content)?.groupValues?.get(1)?.trim()
            
            // 提取年份
            val yearRegex = "<year>(\\d{4})</year>".toRegex(RegexOption.IGNORE_CASE)
            val year = yearRegex.find(content)?.groupValues?.get(1)?.toIntOrNull()
            
            // 提取简介
            val plotRegex = "<plot>([^<]+)</plot>".toRegex(RegexOption.IGNORE_CASE)
            val overview = plotRegex.find(content)?.groupValues?.get(1)?.trim()
            
            // 提取海报路径（本地或网络）
            val posterRegex = "<poster>([^<]+)</poster>".toRegex(RegexOption.IGNORE_CASE)
            val thumbRegex = "<thumb>([^<]+)</thumb>".toRegex(RegexOption.IGNORE_CASE)
            val posterUrl = posterRegex.find(content)?.groupValues?.get(1)?.trim()
                ?: thumbRegex.find(content)?.groupValues?.get(1)?.trim()
            
            // 提取背景图
            val fanartRegex = "<fanart>([^<]+)</fanart>".toRegex(RegexOption.IGNORE_CASE)
            val backdropUrl = fanartRegex.find(content)?.groupValues?.get(1)?.trim()
            
            // 提取评分
            val ratingRegex = "<rating>([\\d.]+)</rating>".toRegex(RegexOption.IGNORE_CASE)
            val rating = ratingRegex.find(content)?.groupValues?.get(1)?.toFloatOrNull()
            
            // 提取类型
            val genreRegex = "<genre>([^<]+)</genre>".toRegex(RegexOption.IGNORE_CASE)
            val genres = genreRegex.findAll(content).map { it.groupValues[1].trim() }.toList()
            
            // 提取 TMDB ID
            val tmdbIdRegex = "<tmdbid>(\\d+)</tmdbid>".toRegex(RegexOption.IGNORE_CASE)
            val tmdbId = tmdbIdRegex.find(content)?.groupValues?.get(1)
            
            // 提取原始标题
            val originalTitleRegex = "<originaltitle>([^<]+)</originaltitle>".toRegex(RegexOption.IGNORE_CASE)
            val originalTitle = originalTitleRegex.find(content)?.groupValues?.get(1)?.trim()
            
            if (title != null) {
                val parentFolder = file.path.substringBeforeLast("/", "").substringAfterLast("/", "")
                val mediaInfo = MediaInfoExtractor.extract(file.name, parentFolder, file.path)
                
                return ScrapedMedia(
                    id = tmdbId ?: file.path.hashCode().toString(),
                    title = title,
                    originalTitle = originalTitle,
                    overview = overview,
                    posterUrl = posterUrl?.let { if (it.startsWith("http")) it else null },
                    backdropUrl = backdropUrl?.let { if (it.startsWith("http")) it else null },
                    year = year,
                    rating = rating?.let { it * 2 }, // Kodi 评分通常是 0-10，转换为 0-20
                    genres = genres,
                    type = resolveFinalScrapedType(file.path, mediaInfo, mediaInfo.type),
                    seasonNumber = mediaInfo.season,
                    episodeNumber = mediaInfo.episode,
                    filePath = file.path,
                    fileName = file.name,
                    source = "nfo",
                    tmdbId = tmdbId
                )
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "解析 nfo 内容失败", e)
            return null
        }
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        cache.clear()
    }
}
