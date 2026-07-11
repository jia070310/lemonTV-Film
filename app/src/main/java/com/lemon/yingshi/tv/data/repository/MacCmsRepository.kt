package com.lemon.yingshi.tv.data.repository

import com.lemon.yingshi.tv.data.preferences.MacCmsPreferences
import com.lemon.yingshi.tv.data.remote.api.MacCmsApi
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.remote.model.MacCmsFilterParams
import com.lemon.yingshi.tv.data.remote.model.MacCmsListResponse
import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsTypeItem
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.data.remote.parser.MacCmsAssetUrl
import com.lemon.yingshi.tv.data.remote.parser.MacCmsPlayerConfigParser
import com.lemon.yingshi.tv.data.remote.parser.MacCmsVersionDetector
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport
import com.lemon.yingshi.tv.domain.model.MacCmsNavCategory
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class MacCmsCategoryFetchResult(
    val items: List<MacCmsVodItem>,
    val total: Int,
    val page: Int = 1,
    val pageCount: Int = 1,
    val error: String? = null
)

@Singleton
class MacCmsRepository @Inject constructor(
    private val macCmsApi: MacCmsApi,
    private val macCmsPreferences: MacCmsPreferences,
    private val okHttpClient: OkHttpClient
) {
    val serverUrl: Flow<String> = macCmsPreferences.serverUrl
    val isConfigured: Flow<Boolean> = macCmsPreferences.isConfigured

    @Volatile
    private var cachedPlayerShowNames: Map<String, String> = emptyMap()

    @Volatile
    private var cachedPlayerConfigBaseUrl: String? = null

    @Volatile
    private var cachedTaxonomy: MacCmsTaxonomy? = null

    @Volatile
    private var cachedTaxonomyUrl: String? = null

    @Volatile
    private var cachedVodDetails: MutableMap<Int, MacCmsVodItem> = linkedMapOf()

    fun invalidateTaxonomyCache() {
        cachedTaxonomy = null
        cachedTaxonomyUrl = null
    }

    fun invalidateVodDetailCache() {
        cachedVodDetails = linkedMapOf()
    }

    fun getCachedVodDetail(vodId: Int): MacCmsVodItem? = cachedVodDetails[vodId]

    /** 点击卡片进入详情前写入，便于详情页秒开基本信息 */
    fun putCachedVodSnapshot(vod: MacCmsVodItem) {
        if (vod.vodId <= 0) return
        cacheVodDetail(vod)
    }

    private fun cacheVodDetail(item: MacCmsVodItem) {
        if (item.vodId <= 0) return
        val existing = cachedVodDetails[item.vodId]
        val merged = if (existing != null) mergeFullVodDetail(existing, item) else item
        if (cachedVodDetails.size >= VOD_DETAIL_CACHE_MAX && !cachedVodDetails.containsKey(item.vodId)) {
            val oldestKey = cachedVodDetails.keys.firstOrNull() ?: return
            cachedVodDetails.remove(oldestKey)
        }
        cachedVodDetails[item.vodId] = merged
    }

    private fun hasPlayInfo(vod: MacCmsVodItem): Boolean =
        !vod.vodPlayFrom.isNullOrBlank() && !vod.vodPlayUrl.isNullOrBlank()

    /**
     * 拉取分类树：优先 REST `/type/get_list/`，不可用时回退到 provide/vod 的 class 字段。
     */
    suspend fun fetchTaxonomy(forceRefresh: Boolean = false, baseUrlOverride: String? = null): MacCmsTaxonomy {
        val baseUrl = macCmsPreferences.normalizeBaseUrl(
            baseUrlOverride ?: getServerUrl()
        )
        if (baseUrl.isBlank()) {
            throw IllegalStateException("未配置 MacCMS 服务器")
        }

        if (!forceRefresh && cachedTaxonomy != null && cachedTaxonomyUrl == baseUrl) {
            return cachedTaxonomy!!
        }

        val taxonomy = fetchTaxonomyFromRest(baseUrl)
            ?: fetchTaxonomyFromProvide(baseUrl)
            ?: throw IllegalStateException("无法获取服务器分类，请确认已开启视频 API")

        if (taxonomy.topCategories.isEmpty()) {
            throw IllegalStateException("服务器分类数据为空")
        }

        cachedTaxonomy = taxonomy
        cachedTaxonomyUrl = baseUrl
        return taxonomy
    }

    /** REST 分类树（需后台开启「公共 API」） */
    private suspend fun fetchTaxonomyFromRest(baseUrl: String): MacCmsTaxonomy? = runCatching {
        val url = "$baseUrl/api.php/type/get_list/"
        val response = macCmsApi.fetchTypeList(url)
        if (!response.isSuccessful) return@runCatching null
        val body = response.body() ?: return@runCatching null
        if (body.code != 1) return@runCatching null
        val rows = body.info?.rows.orEmpty()
        if (rows.isEmpty()) return@runCatching null
        val taxonomy = MacCmsTaxonomy.fromServerTypes(rows)
        taxonomy.takeIf { it.topCategories.isNotEmpty() }
    }.getOrNull()

    /** 采集接口 class 字段（兼容大多数 MacCMS 站点） */
    private suspend fun fetchTaxonomyFromProvide(baseUrl: String): MacCmsTaxonomy? = runCatching {
        val response = macCmsApi.fetchVodList(
            buildListUrl(baseUrl, MacCmsFilterParams(page = 1, pageSize = 1))
        )
        if (!response.isSuccessful) return@runCatching null
        val body = response.body() ?: return@runCatching null
        if (body.code != 1) return@runCatching null
        val classes = body.categories
        if (classes.isEmpty()) return@runCatching null
        val taxonomy = MacCmsTaxonomy.fromFlatTypeItems(classes)
        taxonomy.takeIf { it.topCategories.isNotEmpty() }
    }.getOrNull()

    suspend fun getServerUrl(): String = macCmsPreferences.serverUrl.first()

    suspend fun saveServerUrl(url: String) {
        val oldUrl = getServerUrl()
        val normalizedNew = macCmsPreferences.normalizeBaseUrl(url)
        macCmsPreferences.saveServerUrl(url)
        if (oldUrl != normalizedNew) {
            invalidateTaxonomyCache()
            invalidateRecommendedCache()
            invalidateVodDetailCache()
            cachedPlayerShowNames = emptyMap()
            cachedPlayerConfigBaseUrl = null
        }
    }

    suspend fun testConnection(url: String? = null): MacCmsConnectionResult {
        val baseUrl = macCmsPreferences.normalizeBaseUrl(url ?: getServerUrl())
        if (baseUrl.isBlank()) {
            return MacCmsConnectionResult(
                success = false,
                message = "请先填写服务器地址"
            )
        }

        return try {
            val response = macCmsApi.fetchVodList(
                buildListUrl(baseUrl, MacCmsFilterParams(page = 1, pageSize = 1))
            )
            if (!response.isSuccessful) {
                val msg = MacCmsErrorMessages.httpFailure(response.code())
                macCmsPreferences.saveConnectionTestResult(msg)
                return MacCmsConnectionResult(success = false, message = msg)
            }

            val body = response.body()
            if (body == null || body.code != 1) {
                val msg = body?.msg?.takeIf { it.isNotBlank() } ?: "服务器返回异常数据"
                macCmsPreferences.saveConnectionTestResult(msg)
                return MacCmsConnectionResult(success = false, message = msg)
            }

            refreshPlayerShowNames(baseUrl)
            val taxonomy = runCatching {
                fetchTaxonomy(forceRefresh = true, baseUrlOverride = baseUrl)
            }.getOrNull()
            val versionProbe = probeMacCmsVersion(
                baseUrl = baseUrl,
                restTypeApiAvailable = taxonomy?.sourceLabel == MacCmsTaxonomy.SOURCE_REST
            )
            val maccmsVersionLabel = MacCmsVersionDetector.formatVersionLabel(versionProbe)
            val categoryCount = taxonomy?.categoryCount ?: 0
            val apiSource = taxonomy?.sourceLabel
            macCmsPreferences.saveConnectionTestResult(
                status = "已连接",
                siteName = baseUrl,
                maccmsVersion = maccmsVersionLabel,
                categoryCount = categoryCount,
                apiSourceLabel = apiSource
            )
            MacCmsConnectionResult(
                success = true,
                message = MacCmsErrorMessages.connectionSuccess(),
                categoryCount = categoryCount,
                siteName = baseUrl,
                apiSourceLabel = apiSource,
                maccmsVersionLabel = maccmsVersionLabel
            )
        } catch (e: Exception) {
            val msg = MacCmsErrorMessages.fromThrowable(e, "网络异常")
            macCmsPreferences.saveConnectionTestResult(msg)
            MacCmsConnectionResult(success = false, message = msg)
        }
    }

    /** 筛选页分类：使用当前服务器 taxonomy */
    suspend fun getFilterCategories(): List<MacCmsTypeItem> =
        fetchTaxonomy().filterCategories().map { (typeId, label) ->
            MacCmsTypeItem(typeId = typeId, typeName = label)
        }

    suspend fun fetchLatestForNavCategory(
        category: MacCmsNavCategory,
        taxonomy: MacCmsTaxonomy,
        limit: Int = 10,
        enrichDetail: Boolean = true
    ): MacCmsCategoryFetchResult {
        val typeIds = taxonomy.listQueryTypeIds(category)
        if (typeIds.isEmpty()) return MacCmsCategoryFetchResult(emptyList(), 0)

        val allow = taxonomy.allowedTypeIds(category)
        val seen = mutableSetOf<Int>()
        val pool = mutableListOf<MacCmsVodItem>()
        var lastError: String? = null
        var totalCount = 0

        for (typeId in typeIds) {
            val response = fetchVodList(
                MacCmsFilterParams(
                    typeId = typeId,
                    sort = MacCmsSortOption.LATEST,
                    page = 1,
                    pageSize = limit + 8
                )
            )
            if (response.code != 1) {
                lastError = response.msg ?: lastError
                continue
            }
            totalCount += response.total
            for (row in response.list) {
                if (row.typeId !in allow) continue
                if (!seen.add(row.vodId)) continue
                pool.add(row)
            }
        }

        val sorted = pool
            .sortedByDescending { vodTimeMs(it.vodTime) }
            .take(limit)
        if (sorted.isEmpty()) {
            return MacCmsCategoryFetchResult(emptyList(), 0, error = lastError)
        }
        val displayItems = if (enrichDetail) {
            enrichWithDetail(baseUrl = getServerUrl(), rows = sorted)
        } else {
            prepareListForDisplay(baseUrl = getServerUrl(), rows = sorted)
        }
        return MacCmsCategoryFetchResult(
            items = displayItems,
            total = totalCount.coerceAtLeast(displayItems.size)
        )
    }

    /** 拉取指定推荐等级的视频（首页「最新推荐」使用 level=9） */
    suspend fun fetchRecommendedVods(
        level: Int = 9,
        page: Int = 1,
        pageSize: Int = 30,
        homePreviewLimit: Int? = null,
        fullScan: Boolean = homePreviewLimit == null
    ): MacCmsCategoryFetchResult {
        val all = fetchAllRecommendedLevel9(level = level, fullScan = fullScan)
        if (homePreviewLimit != null) {
            return MacCmsCategoryFetchResult(
                items = all.take(homePreviewLimit),
                total = all.size
            )
        }
        val safePage = page.coerceAtLeast(1)
        val start = (safePage - 1) * pageSize
        val pageItems = all.drop(start).take(pageSize)
        val pageCount = if (all.isEmpty()) 1 else ((all.size + pageSize - 1) / pageSize)
        return MacCmsCategoryFetchResult(
            items = pageItems,
            total = all.size,
            page = safePage.coerceAtMost(pageCount),
            pageCount = pageCount
        )
    }

    @Volatile
    private var cachedRecommendedItems: List<MacCmsVodItem>? = null

    @Volatile
    private var cachedRecommendedLevel: Int? = null

    @Volatile
    private var cachedRecommendedUrl: String? = null

    @Volatile
    private var cachedRecommendedFullScanComplete: Boolean = false

    fun invalidateRecommendedCache() {
        cachedRecommendedItems = null
        cachedRecommendedLevel = null
        cachedRecommendedUrl = null
        cachedRecommendedFullScanComplete = false
    }

    /** 拉取并缓存全部推荐9视频；优先走网页筛选页（与模板 level="9" 一致） */
    private suspend fun fetchAllRecommendedLevel9(level: Int, fullScan: Boolean): List<MacCmsVodItem> {
        val baseUrl = getServerUrl()
        if (
            cachedRecommendedItems != null &&
            cachedRecommendedLevel == level &&
            cachedRecommendedUrl == baseUrl &&
            (!fullScan || cachedRecommendedFullScanComplete)
        ) {
            return cachedRecommendedItems!!
        }

        fetchRecommendedFromShowPage(baseUrl, level)?.let { fromShowPage ->
            val sorted = fromShowPage.sortedWith(recommendedSortComparator)
            cachedRecommendedItems = sorted
            cachedRecommendedLevel = level
            cachedRecommendedUrl = baseUrl
            cachedRecommendedFullScanComplete = true
            return sorted
        }

        val firstResponse = fetchVodList(
            MacCmsFilterParams(
                level = level,
                sort = MacCmsSortOption.LATEST,
                page = 1,
                pageSize = RECOMMENDED_FETCH_PAGE_SIZE
            )
        )
        if (firstResponse.code != 1) {
            return cachedRecommendedItems.orEmpty()
        }

        val apiTotal = firstResponse.total
        val levelFilterBroken = isRecommendedLevelFilterBroken(apiTotal)
        val sorted = when {
            levelFilterBroken -> fetchRecommendedLevelBrokenScan(
                baseUrl = baseUrl,
                level = level,
                firstPage = firstResponse.list,
                fullScan = fullScan,
                existing = if (fullScan && cachedRecommendedUrl == baseUrl) cachedRecommendedItems else null
            )
            apiTotal in 1..RECOMMENDED_SERVER_FILTER_MAX_TOTAL ->
                fetchRecommendedServerFiltered(baseUrl, level, apiTotal, firstResponse.list)
            else -> fetchRecommendedLevelBrokenScan(
                baseUrl = baseUrl,
                level = level,
                firstPage = firstResponse.list,
                fullScan = fullScan,
                existing = if (fullScan && cachedRecommendedUrl == baseUrl) cachedRecommendedItems else null
            )
        }

        cachedRecommendedItems = sorted
        cachedRecommendedLevel = level
        cachedRecommendedUrl = baseUrl
        cachedRecommendedFullScanComplete = fullScan || cachedRecommendedFullScanComplete
        return sorted
    }

    /** 采集接口未按 level 筛选时（total≈全库），通过详情接口在客户端识别推荐等级 */
    private fun isRecommendedLevelFilterBroken(apiTotal: Int): Boolean =
        apiTotal > RECOMMENDED_SERVER_FILTER_MAX_TOTAL

    /**
     * 拉取网页筛选页 `/vod/show/level/N.html`，与模板 `{maccms:vod level="N"}` 同源。
     * 解析 vod_id 后批量走 provide detail 补全封面与字段。
     */
    private suspend fun fetchRecommendedFromShowPage(
        baseUrl: String,
        level: Int
    ): List<MacCmsVodItem>? {
        val normalizedBase = macCmsPreferences.normalizeBaseUrl(baseUrl).trimEnd('/')
        if (normalizedBase.isBlank()) return null

        val showUrl = "$normalizedBase/index.php/vod/show/level/$level.html"
        val html = fetchHttpText(showUrl)?.take(SHOW_PAGE_HTML_MAX_CHARS).orEmpty()
        if (html.isBlank()) return null

        val vodIds = linkedSetOf<Int>()
        for (match in SHOW_PAGE_VOD_ID_PATTERN.findAll(html)) {
            val id = match.groupValues[1].toIntOrNull() ?: continue
            if (id > 0) vodIds.add(id)
        }
        if (vodIds.isEmpty()) return null

        val detailMap = fetchVodRowsByDetailIds(
            baseUrl = normalizedBase,
            ids = vodIds.toList(),
            chunkSize = RECOMMENDED_DETAIL_CHUNK_SIZE
        )
        val items = vodIds.mapNotNull { id ->
            val detail = detailMap[id] ?: return@mapNotNull null
            if (detail.vodLevel != level) return@mapNotNull null
            resolveItemAssets(normalizedBase, detail)
        }
        return items.takeIf { it.isNotEmpty() }
    }

    /**
     * 服务端 level 参数未生效时的扫描：
     * - 快速模式（首页）：仅降序扫前几页，凑够预览条数即停
     * - 完整模式（推荐页）：降序 + 升序深扫，补全全部推荐9
     */
    private suspend fun fetchRecommendedLevelBrokenScan(
        baseUrl: String,
        level: Int,
        firstPage: List<MacCmsVodItem>,
        fullScan: Boolean,
        existing: List<MacCmsVodItem>? = null
    ): List<MacCmsVodItem> {
        val collected = linkedMapOf<Int, MacCmsVodItem>()
        existing.orEmpty().forEach { item -> collected[item.vodId] = item }

        if (!fullScan || collected.isEmpty()) {
            scanProvidePagesForLevel(
                baseUrl = baseUrl,
                level = level,
                sortAscending = false,
                collected = collected,
                firstPage = if (collected.isEmpty()) firstPage else null,
                maxPages = if (fullScan) RECOMMENDED_DESC_SCAN_MAX_PAGES else RECOMMENDED_FAST_SCAN_MAX_PAGES,
                idlePageLimit = if (fullScan) RECOMMENDED_DESC_IDLE_PAGE_LIMIT else RECOMMENDED_FAST_IDLE_PAGE_LIMIT,
                stopWhenCount = if (fullScan) null else RECOMMENDED_FAST_STOP_COUNT
            )
        }

        if (fullScan) {
            scanAscSupplementPageRanges(baseUrl, level, collected)
        }

        if (fullScan) {
            return finalizeRecommendedList(baseUrl, level, collected)
        }
        return collected.values.sortedWith(recommendedSortComparator)
    }

    /** 升序补扫关键页码区间（早期条目 + 后部页码如 47045 所在第 172 页） */
    private suspend fun scanAscSupplementPageRanges(
        baseUrl: String,
        level: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ) {
        for (range in RECOMMENDED_ASC_SUPPLEMENT_PAGE_RANGES) {
            for (page in range) {
                val response = fetchVodList(
                    MacCmsFilterParams(
                        level = level,
                        sort = MacCmsSortOption.LATEST,
                        sortAscending = true,
                        page = page,
                        pageSize = RECOMMENDED_FETCH_PAGE_SIZE
                    )
                )
                if (response.code != 1 || response.list.isEmpty()) continue
                absorbLevelMatchedRows(baseUrl, level, response.list, collected)
            }
        }
    }

    private suspend fun scanProvidePagesForLevel(
        baseUrl: String,
        level: Int,
        sortAscending: Boolean,
        collected: LinkedHashMap<Int, MacCmsVodItem>,
        firstPage: List<MacCmsVodItem>?,
        maxPages: Int,
        idlePageLimit: Int?,
        stopWhenCount: Int? = null
    ) {
        var page = 1
        var idlePages = 0
        while (page <= maxPages) {
            if (stopWhenCount != null && collected.size >= stopWhenCount) break
            if (idlePageLimit != null && idlePages >= idlePageLimit) break
            val rows = if (page == 1 && firstPage != null) {
                firstPage
            } else {
                val response = fetchVodList(
                    MacCmsFilterParams(
                        level = level,
                        sort = MacCmsSortOption.LATEST,
                        sortAscending = sortAscending,
                        page = page,
                        pageSize = RECOMMENDED_FETCH_PAGE_SIZE
                    )
                )
                if (response.code != 1 || response.list.isEmpty()) break
                response.list
            }
            val beforeSize = collected.size
            absorbLevelMatchedRows(baseUrl, level, rows, collected)
            idlePages = if (collected.size == beforeSize) idlePages + 1 else 0
            if (rows.size < RECOMMENDED_FETCH_PAGE_SIZE) break
            page++
        }
    }

    private suspend fun absorbLevelMatchedRows(
        baseUrl: String,
        level: Int,
        rows: List<MacCmsVodItem>,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ) {
        if (rows.isEmpty()) return
        val detailMap = fetchVodRowsByDetailIds(
            baseUrl = baseUrl,
            ids = rows.map { it.vodId },
            chunkSize = RECOMMENDED_DETAIL_CHUNK_SIZE
        )
        for (row in rows) {
            val detail = detailMap[row.vodId] ?: continue
            if (detail.vodLevel != level) continue
            collected[detail.vodId] = resolveItemAssets(
                baseUrl,
                mergeListRowWithDetail(row, detail)
            )
        }
    }

    /** 服务端 level 筛选生效：直接信任列表，不拉详情，分页直到凑齐 total */
    private suspend fun fetchRecommendedServerFiltered(
        baseUrl: String,
        level: Int,
        apiTotal: Int,
        firstPage: List<MacCmsVodItem>
    ): List<MacCmsVodItem> {
        val collected = linkedMapOf<Int, MacCmsVodItem>()
        absorbRecommendedListRows(baseUrl, firstPage, collected)

        if (collected.size < apiTotal) {
            val exactSize = apiTotal.coerceIn(1, RECOMMENDED_FETCH_PAGE_SIZE)
            val exactResponse = fetchVodList(
                MacCmsFilterParams(
                    level = level,
                    sort = MacCmsSortOption.LATEST,
                    page = 1,
                    pageSize = exactSize
                )
            )
            if (exactResponse.code == 1) {
                absorbRecommendedListRows(baseUrl, exactResponse.list, collected)
            }
        }

        var page = 2
        while (collected.size < apiTotal && page <= RECOMMENDED_MAX_PAGES) {
            val response = fetchVodList(
                MacCmsFilterParams(
                    level = level,
                    sort = MacCmsSortOption.LATEST,
                    page = page,
                    pageSize = RECOMMENDED_FETCH_PAGE_SIZE
                )
            )
            if (response.code != 1 || response.list.isEmpty()) break
            absorbRecommendedListRows(baseUrl, response.list, collected)
            if (collected.size >= apiTotal) break
            page++
        }

        supplementRecommendedGaps(baseUrl, level, apiTotal, collected)

        return finalizeRecommendedList(baseUrl, level, collected)
    }

    /** 合并 REST 按推荐等级排序的结果，补回采集接口因播放器过滤遗漏的条目 */
    private suspend fun finalizeRecommendedList(
        baseUrl: String,
        level: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ): List<MacCmsVodItem> {
        mergeRecommendedFromRestLevelOrder(baseUrl, level, collected)
        return collected.values.sortedWith(recommendedSortComparator)
    }

    /**
     * REST get_list 按 vod_level 降序分页，再用 get_detail 校验等级。
     * 采集接口 total 会受播放器过滤影响（后台 24 条可能只返回 22），此路径可补全。
     */
    private suspend fun mergeRecommendedFromRestLevelOrder(
        baseUrl: String,
        level: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ) {
        var offset = 0
        val limit = REST_RECOMMENDED_LEVEL_PAGE_SIZE
        var pagesWithoutTargetLevel = 0
        while (pagesWithoutTargetLevel < 2 && offset < REST_RECOMMENDED_LEVEL_SCAN_MAX) {
            val rows = fetchRestVodListRows(baseUrl, offset, limit, orderby = "level")
            if (rows.isEmpty()) break

            var foundTargetLevelOnPage = false
            for (row in rows) {
                if (collected.containsKey(row.vodId)) {
                    foundTargetLevelOnPage = true
                    continue
                }
                val item = fetchVodDetailFromRest(baseUrl, row.vodId) ?: continue
                if (item.vodLevel == level) {
                    collected[item.vodId] = resolveItemAssets(baseUrl, item)
                    foundTargetLevelOnPage = true
                }
            }
            if (!foundTargetLevelOnPage) {
                pagesWithoutTargetLevel++
            } else {
                pagesWithoutTargetLevel = 0
            }
            offset += limit
        }
    }

    /** 补齐列表接口遗漏的推荐条目（如 47045 因播放器过滤未出现在采集列表中） */
    private suspend fun supplementRecommendedGaps(
        baseUrl: String,
        level: Int,
        apiTotal: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ) {
        if (collected.size >= apiTotal) return

        paginateRecommendedInto(
            baseUrl = baseUrl,
            level = level,
            collected = collected,
            sortAscending = true,
            stopWhenComplete = true,
            apiTotal = apiTotal
        )
        if (collected.size >= apiTotal) return

        fetchRecommendedSingleItemPages(baseUrl, level, apiTotal, collected, sortAscending = false)
        if (collected.size >= apiTotal) return

        fetchRecommendedSingleItemPages(baseUrl, level, apiTotal, collected, sortAscending = true)
    }

    private suspend fun fetchRecommendedSingleItemPages(
        baseUrl: String,
        level: Int,
        apiTotal: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>,
        sortAscending: Boolean
    ) {
        val maxPages = (apiTotal + 5).coerceIn(1, 500)
        for (page in 1..maxPages) {
            if (collected.size >= apiTotal) break
            val response = fetchVodList(
                MacCmsFilterParams(
                    level = level,
                    sort = MacCmsSortOption.LATEST,
                    sortAscending = sortAscending,
                    page = page,
                    pageSize = 1
                )
            )
            if (response.code != 1) continue
            if (response.list.isNotEmpty()) {
                absorbRecommendedListRows(baseUrl, response.list, collected)
            }
        }
    }

    private suspend fun paginateRecommendedInto(
        baseUrl: String,
        level: Int,
        collected: LinkedHashMap<Int, MacCmsVodItem>,
        sortAscending: Boolean,
        stopWhenComplete: Boolean,
        apiTotal: Int
    ) {
        var page = 1
        while (page <= RECOMMENDED_MAX_PAGES) {
            if (stopWhenComplete && collected.size >= apiTotal) break
            val response = fetchVodList(
                MacCmsFilterParams(
                    level = level,
                    sort = MacCmsSortOption.LATEST,
                    sortAscending = sortAscending,
                    page = page,
                    pageSize = RECOMMENDED_FETCH_PAGE_SIZE
                )
            )
            if (response.code != 1 || response.list.isEmpty()) break
            absorbRecommendedListRows(baseUrl, response.list, collected)
            if (stopWhenComplete && collected.size >= apiTotal) break
            if (response.list.size < RECOMMENDED_FETCH_PAGE_SIZE && !stopWhenComplete) break
            page++
        }
    }

    /** REST 详情不走采集接口的播放器过滤 */
    private suspend fun fetchRestVodListRows(
        baseUrl: String,
        offset: Int,
        limit: Int,
        orderby: String = "time"
    ): List<MacCmsVodItem> {
        return runCatching {
            val url = "$baseUrl/api.php/vod/get_list/?offset=$offset&limit=$limit&orderby=$orderby"
            val response = macCmsApi.fetchRestVodList(url)
            if (!response.isSuccessful) return@runCatching emptyList()
            val body = response.body() ?: return@runCatching emptyList()
            if (body.code != 1) return@runCatching emptyList()
            body.info?.rows.orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchVodDetailFromRest(baseUrl: String, vodId: Int): MacCmsVodItem? {
        return runCatching {
            val url = "$baseUrl/api.php/vod/get_detail/?vod_id=$vodId"
            val response = macCmsApi.fetchRestVodDetail(url)
            if (!response.isSuccessful) return@runCatching null
            val body = response.body() ?: return@runCatching null
            if (body.code != 1) return@runCatching null
            body.info?.firstOrNull()
        }.getOrNull()
    }

    private fun prepareListForDisplay(baseUrl: String, rows: List<MacCmsVodItem>): List<MacCmsVodItem> =
        rows.map { resolveItemAssets(baseUrl, it) }

    private fun absorbRecommendedListRows(
        baseUrl: String,
        rows: List<MacCmsVodItem>,
        collected: LinkedHashMap<Int, MacCmsVodItem>
    ) {
        rows.forEach { row ->
            collected[row.vodId] = resolveItemAssets(baseUrl, row)
        }
    }

    private val recommendedSortComparator =
        compareByDescending<MacCmsVodItem> { vodTimeMs(it.vodTime) }
            .thenByDescending { it.vodId }

    companion object {
        /** 与 MacCMS 模板页 `/vod/show/level/N.html` 中详情链接一致 */
        private val SHOW_PAGE_VOD_ID_PATTERN = Regex("""vod/detail/id/(\d+)""")
        private const val SHOW_PAGE_HTML_MAX_CHARS = 512_000
        /** 服务端 level 筛选生效时，total 通常较小 */
        private const val RECOMMENDED_SERVER_FILTER_MAX_TOTAL = 200
        private const val RECOMMENDED_FETCH_PAGE_SIZE = 100
        private const val RECOMMENDED_MAX_PAGES = 20
        private const val RECOMMENDED_DESC_SCAN_MAX_PAGES = 50
        private const val RECOMMENDED_DESC_IDLE_PAGE_LIMIT = 15
        /** 首页快速预览：只扫前几页，凑够条数即停 */
        private const val RECOMMENDED_FAST_SCAN_MAX_PAGES = 15
        private const val RECOMMENDED_FAST_IDLE_PAGE_LIMIT = 6
        private const val RECOMMENDED_FAST_STOP_COUNT = 12
        /** 升序补扫区间：前部早期推荐 + 后部页码（实测 47045 在第 172 页） */
        private val RECOMMENDED_ASC_SUPPLEMENT_PAGE_RANGES = listOf(1..8, 155..195)
        private const val RECOMMENDED_DETAIL_CHUNK_SIZE = 20
        private const val REST_RECOMMENDED_LEVEL_PAGE_SIZE = 50
        private const val REST_RECOMMENDED_LEVEL_SCAN_MAX = 1000
        private const val VOD_DETAIL_CACHE_MAX = 300
    }

    suspend fun fetchLatestForType(
        typeId: Int,
        limit: Int = 10,
        enrichDetail: Boolean = true
    ): MacCmsCategoryFetchResult {
        val response = fetchVodList(
            MacCmsFilterParams(
                typeId = typeId,
                sort = MacCmsSortOption.LATEST,
                page = 1,
                pageSize = limit
            )
        )
        if (response.code != 1) {
            return MacCmsCategoryFetchResult(
                items = emptyList(),
                total = 0,
                error = response.msg
            )
        }
        val sorted = response.list
            .sortedByDescending { vodTimeMs(it.vodTime) }
            .take(limit)
        val displayItems = if (enrichDetail) {
            enrichWithDetail(baseUrl = getServerUrl(), rows = sorted)
        } else {
            prepareListForDisplay(baseUrl = getServerUrl(), rows = sorted)
        }
        return MacCmsCategoryFetchResult(items = displayItems, total = response.total)
    }

    suspend fun fetchFilterResults(
        typeIds: List<Int>,
        params: MacCmsFilterParams,
        allowedTypeIds: Set<Int>? = null
    ): MacCmsListResponse {
        val filterState = MacCmsFilterSupport.FilterState(
            typeIds = typeIds,
            plot = params.vodClass,
            area = params.area,
            lang = params.lang,
            year = params.year,
            sort = params.sort
        )
        val result = advanceFilterCatalog(
            filter = filterState,
            continuation = null,
            targetSortedCount = params.page * params.pageSize,
            allowedTypeIds = allowedTypeIds
        )
        val start = (params.page - 1) * params.pageSize
        val pageItems = result.sorted.drop(start).take(params.pageSize)
        val total = if (MacCmsFilterSupport.filterListTotalUsesClientCount(
                filterState.plot, filterState.area, filterState.lang
            )
        ) {
            result.sorted.size
        } else {
            result.apiTotalSum.coerceAtLeast(result.sorted.size)
        }
        val pageCount = if (params.pageSize > 0) {
            ((total + params.pageSize - 1) / params.pageSize).coerceAtLeast(1)
        } else 1
        return MacCmsListResponse(
            code = 1,
            list = pageItems,
            total = total,
            page = params.page,
            pagecount = pageCount
        )
    }

    /**
     * 按波次合并 ac=list，本地过滤排序；达到 targetSortedCount 后可暂停续拉。
     * 与参考项目 advanceFilterCatalog 对齐。
     */
    suspend fun advanceFilterCatalog(
        filter: MacCmsFilterSupport.FilterState,
        continuation: FilterCatalogContinuation?,
        targetSortedCount: Int,
        allowedTypeIds: Set<Int>? = null
    ): FilterCatalogResult {
        val allow = allowedTypeIds ?: filter.typeIds.toSet()
        if (allow.isEmpty()) {
            val empty = createEmptyFilterContinuation(filter.typeIds)
            return FilterCatalogResult(empty, emptyList(), 0, true)
        }

        val state = continuation ?: createEmptyFilterContinuation(filter.typeIds)
        val pagesize = MacCmsFilterSupport.FILTER_FETCH_PAGE_SIZE
        val maxPg = MacCmsFilterSupport.FILTER_MAX_PAGES_PER_TYPE
        val pool = state.pool
        val seen = state.seen
        val exhausted = state.exhausted

        fun mergeBatch(batch: List<MacCmsVodItem>) {
            for (row in batch) {
                if (row.typeId !in allow) continue
                if (row.vodId in seen) continue
                if (!MacCmsFilterSupport.rowMatchesFiltersListMerge(row, filter)) continue
                seen.add(row.vodId)
                pool.add(row)
            }
        }

        fun totalForUi(sortedLen: Int): Int =
            if (MacCmsFilterSupport.filterListTotalUsesClientCount(filter.plot, filter.area, filter.lang)) {
                sortedLen
            } else if (state.apiTotalSum > 0) {
                state.apiTotalSum
            } else {
                sortedLen
            }

        suspend fun enrichPoolThenStrictPrune() {
            val need = mutableSetOf<Int>()
            for (r in pool) {
                if (filter.plot.isNotBlank() && r.vodClass.isNullOrBlank()) need.add(r.vodId)
                if (filter.area.isNotBlank() && r.vodArea.isNullOrBlank()) need.add(r.vodId)
                if (filter.lang.isNotBlank() && r.vodLang.isNullOrBlank()) need.add(r.vodId)
                if (filter.year.isNotBlank() && r.vodYear.isNullOrBlank()) need.add(r.vodId)
            }
            if (need.isEmpty()) return
            val baseUrl = getServerUrl()
            val detailMap = fetchVodRowsByDetailIds(baseUrl, need.toList())
            for (i in pool.indices) {
                val detail = detailMap[pool[i].vodId] ?: continue
                pool[i] = mergeListRowWithDetail(pool[i], resolveItemAssets(baseUrl, detail))
            }
            val kept = pool.filter { MacCmsFilterSupport.rowMatchesFiltersStrict(it, filter) }
            pool.clear()
            pool.addAll(kept)
            seen.clear()
            kept.forEach { seen.add(it.vodId) }
        }

        fun finish(exhaustedAll: Boolean): FilterCatalogResult {
            val sorted = MacCmsFilterSupport.sortFiltered(pool, filter.sort)
            return FilterCatalogResult(
                continuation = state,
                sorted = sorted,
                apiTotalSum = totalForUi(sorted.size),
                exhaustedAll = exhaustedAll
            )
        }

        for (pg in state.nextPg..maxPg) {
            val activeTypes = filter.typeIds.filter { exhausted[it] != true }
            if (activeTypes.isEmpty()) {
                state.nextPg = pg
                if (MacCmsFilterSupport.needsFilterDetailEnrichment(
                        filter.plot, filter.area, filter.lang, filter.year
                    )
                ) {
                    enrichPoolThenStrictPrune()
                }
                return finish(true)
            }

            for (typeId in activeTypes) {
                val response = fetchVodList(
                    MacCmsFilterParams(
                        typeId = typeId,
                        area = filter.area,
                        lang = filter.lang,
                        year = filter.year,
                        vodClass = filter.plot,
                        sort = MacCmsSortOption.LATEST,
                        page = pg,
                        pageSize = pagesize
                    )
                )
                if (response.code != 1) {
                    exhausted[typeId] = true
                    continue
                }
                if (!state.totalsCaptured &&
                    !MacCmsFilterSupport.filterListTotalUsesClientCount(
                        filter.plot, filter.area, filter.lang
                    )
                ) {
                    if (response.total > 0) state.apiTotalSum += response.total
                }
                val batch = response.list
                mergeBatch(batch)
                if (typeId !in state.typePageCount) {
                    state.typePageCount[typeId] = MacCmsFilterSupport.resolveFilterTypePageCount(
                        pagecount = response.pagecount,
                        total = response.total,
                        batchSize = batch.size,
                        requestedPageSize = pagesize
                    )
                }
                val typePages = state.typePageCount[typeId] ?: 1
                if (MacCmsFilterSupport.isFilterTypePageExhausted(pg, batch.size, typePages)) {
                    exhausted[typeId] = true
                }
            }
            state.totalsCaptured = true

            if (MacCmsFilterSupport.needsFilterDetailEnrichment(
                    filter.plot, filter.area, filter.lang, filter.year
                )
            ) {
                enrichPoolThenStrictPrune()
            }

            state.nextPg = pg + 1
            val sorted = MacCmsFilterSupport.sortFiltered(pool, filter.sort)
            if (sorted.size >= targetSortedCount) {
                return finish(filter.typeIds.all { exhausted[it] == true })
            }
            if (filter.typeIds.all { exhausted[it] == true }) {
                return finish(true)
            }
        }

        return finish(filter.typeIds.all { exhausted[it] == true })
    }

    suspend fun fetchVodList(params: MacCmsFilterParams): MacCmsListResponse {
        val baseUrl = getServerUrl()
        if (baseUrl.isBlank()) {
            return MacCmsListResponse(code = 0, msg = "未配置 MacCMS 服务器")
        }

        return try {
            val response = macCmsApi.fetchVodList(buildListUrl(baseUrl, params))
            if (!response.isSuccessful) {
                return MacCmsListResponse(
                    code = 0,
                    msg = MacCmsErrorMessages.httpFailure(response.code())
                )
            }
            val body = response.body() ?: MacCmsListResponse(code = 0, msg = "空响应")
            body.copy(list = body.list.map { resolveItemAssets(baseUrl, it) })
        } catch (e: Exception) {
            MacCmsListResponse(code = 0, msg = MacCmsErrorMessages.fromThrowable(e, "加载失败"))
        }
    }

    suspend fun getPlayerShowNames(forceRefresh: Boolean = false): Map<String, String> {
        val baseUrl = macCmsPreferences.normalizeBaseUrl(getServerUrl())
        if (baseUrl.isBlank()) return emptyMap()
        if (!forceRefresh &&
            cachedPlayerShowNames.isNotEmpty() &&
            cachedPlayerConfigBaseUrl == baseUrl
        ) {
            return cachedPlayerShowNames
        }
        return refreshPlayerShowNames(baseUrl)
    }

    private suspend fun refreshPlayerShowNames(baseUrl: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            val normalizedBase = macCmsPreferences.normalizeBaseUrl(baseUrl)
            if (normalizedBase.isBlank()) return@withContext emptyMap()

            val candidateUrls = listOf(
                "${normalizedBase.trimEnd('/')}/static/js/playerconfig.js",
                "${normalizedBase.trimEnd('/')}/static_new/js/playerconfig.js"
            )

            for (url in candidateUrls) {
                val names = fetchPlayerShowNamesFromUrl(url)
                if (names.isNotEmpty()) {
                    cachedPlayerShowNames = names
                    cachedPlayerConfigBaseUrl = normalizedBase
                    return@withContext names
                }
            }
            emptyMap()
        }
    }

    private fun fetchPlayerShowNamesFromUrl(url: String): Map<String, String> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap()
                MacCmsPlayerConfigParser.parsePlayerShowNames(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun fetchVodDetail(vodId: Int, forceRefresh: Boolean = false): MacCmsVodItem? {
        val baseUrl = getServerUrl()
        if (baseUrl.isBlank()) return null

        if (!forceRefresh) {
            getCachedVodDetail(vodId)?.takeIf { hasPlayInfo(it) }?.let { cached ->
                return resolveItemAssets(baseUrl, cached)
            }
        }

        return try {
            val url = "$baseUrl/api.php/provide/vod/?ac=detail&ids=$vodId"
            val response = macCmsApi.fetchVodList(url)
            if (!response.isSuccessful) return null
            val row = response.body()?.list?.firstOrNull() ?: return null
            cacheVodDetail(row)
            resolveItemAssets(baseUrl, row)
        } catch (e: Exception) {
            throw MacCmsErrorMessages.wrapIOException(e)
        }
    }

    suspend fun searchVod(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30
    ): MacCmsListResponse {
        return fetchVodList(
            MacCmsFilterParams(
                keyword = keyword.trim(),
                page = page,
                pageSize = pageSize
            )
        )
    }

    private fun buildListUrl(baseUrl: String, params: MacCmsFilterParams): String {
        val query = buildString {
            append("$baseUrl/api.php/provide/vod/?ac=list")
            append("&pg=${params.page}")
            append("&pagesize=${params.pageSize}")
            if (params.typeId > 0) append("&t=${params.typeId}")
            if (params.keyword.isNotBlank()) append("&wd=${encode(params.keyword)}")
            if (params.area.isNotBlank()) append("&area=${encode(params.area)}")
            if (params.lang.isNotBlank()) append("&lang=${encode(params.lang)}")
            if (params.year.isNotBlank()) append("&year=${encode(params.year)}")
            if (params.vodClass.isNotBlank()) append("&class=${encode(params.vodClass)}")
            if (params.level != null) append("&level=${params.level}")
            when (params.sort) {
                MacCmsSortOption.LATEST -> {
                    append("&sort_direction=${if (params.sortAscending) "asc" else "desc"}")
                }
                else -> {
                    append("&by=${params.sort.by}")
                    append("&order=${params.sort.order}")
                }
            }
        }
        return query
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /** 用 ac=detail 补全列表项的封面、年份、地区等卡片信息 */
    suspend fun enrichVodItemsForDisplay(rows: List<MacCmsVodItem>): List<MacCmsVodItem> =
        enrichWithDetail(getServerUrl(), rows)

    private fun resolveItemAssets(baseUrl: String, item: MacCmsVodItem): MacCmsVodItem {
        val normalizedBase = macCmsPreferences.normalizeBaseUrl(baseUrl)
        val posterRaw = MacCmsAssetUrl.pickPosterRaw(item.vodPic, item.vodPicThumb, item.vodPicSlide)
        return item.copy(
            vodPic = MacCmsAssetUrl.resolve(normalizedBase, posterRaw).takeIf { it.isNotBlank() },
            vodPicThumb = item.vodPicThumb?.let { MacCmsAssetUrl.resolve(normalizedBase, it) },
            vodPicSlide = item.vodPicSlide?.let { MacCmsAssetUrl.resolve(normalizedBase, it) }
        )
    }

    private suspend fun enrichWithDetail(
        baseUrl: String,
        rows: List<MacCmsVodItem>
    ): List<MacCmsVodItem> {
        if (rows.isEmpty()) return emptyList()
        val normalizedBase = macCmsPreferences.normalizeBaseUrl(baseUrl)
        if (normalizedBase.isBlank()) return rows.map { resolveItemAssets(normalizedBase, it) }

        val detailMap = fetchVodRowsByDetailIds(normalizedBase, rows.map { it.vodId })
        return rows.map { row ->
            val detail = detailMap[row.vodId]
            val merged = if (detail != null) mergeListRowWithDetail(row, detail) else row
            resolveItemAssets(normalizedBase, merged)
        }
    }

    private suspend fun fetchVodRowsByDetailIds(
        baseUrl: String,
        ids: List<Int>,
        chunkSize: Int = 8
    ): Map<Int, MacCmsVodItem> {
        val map = linkedMapOf<Int, MacCmsVodItem>()
        for (chunk in ids.chunked(chunkSize)) {
            if (chunk.isEmpty()) continue
            val url = "$baseUrl/api.php/provide/vod/?ac=detail&ids=${chunk.joinToString(",")}"
            val response = macCmsApi.fetchVodList(url)
            if (!response.isSuccessful) continue
            response.body()?.list.orEmpty().forEach { row ->
                map[row.vodId] = row
                cacheVodDetail(row)
            }
        }
        return map
    }

    private fun vodTimeMs(time: String?): Long {
        if (time.isNullOrBlank()) return 0L
        time.toLongOrNull()?.let { n ->
            return if (n < 20_000_000_000L) n * 1000 else n
        }
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .parse(time.replace('-', '/'))?.time ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun probeMacCmsVersion(
        baseUrl: String,
        restTypeApiAvailable: Boolean
    ): MacCmsVersionDetector.ProbeResult = withContext(Dispatchers.IO) {
        val xmlUrls = listOf(
            "$baseUrl/api.php/provide/vod/?ac=list&at=xml&pagesize=1",
            "$baseUrl/api.php/provide/vod/at/xml/?ac=list&pagesize=1"
        )
        var protocolVersion: String? = null
        for (url in xmlUrls) {
            val xml = fetchHttpText(url)?.take(4096).orEmpty()
            protocolVersion = MacCmsVersionDetector.parseProvideXmlProtocolVersion(xml)
            if (protocolVersion != null) break
        }

        val homepageVersion = fetchHttpText(baseUrl)
            ?.take(65536)
            ?.let { MacCmsVersionDetector.parseHomepageVersionCode(it) }

        MacCmsVersionDetector.ProbeResult(
            provideProtocolVersion = protocolVersion,
            homepageVersionCode = homepageVersion,
            restTypeApiAvailable = restTypeApiAvailable
        )
    }

    private suspend fun fetchHttpText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
        }.getOrNull()
    }
}
