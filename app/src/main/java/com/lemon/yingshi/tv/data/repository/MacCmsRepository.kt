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
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport
import com.lemon.yingshi.tv.domain.model.MacCmsHomeNavCategory
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class MacCmsCategoryFetchResult(
    val items: List<MacCmsVodItem>,
    val total: Int,
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

    suspend fun getServerUrl(): String = macCmsPreferences.serverUrl.first()

    suspend fun saveServerUrl(url: String) {
        macCmsPreferences.saveServerUrl(url)
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

            macCmsPreferences.saveConnectionTestResult("已连接", baseUrl)
            refreshPlayerShowNames(baseUrl)
            MacCmsConnectionResult(
                success = true,
                message = MacCmsErrorMessages.connectionSuccess(),
                categoryCount = MacCmsTaxonomy.allSecondaryTypeIds().size + 4,
                siteName = baseUrl
            )
        } catch (e: Exception) {
            val msg = MacCmsErrorMessages.fromThrowable(e, "网络异常")
            macCmsPreferences.saveConnectionTestResult(msg)
            MacCmsConnectionResult(success = false, message = msg)
        }
    }

    /** 筛选页分类：使用本地 taxonomy，与参考项目一致 */
    fun getFilterCategories(): List<MacCmsTypeItem> =
        MacCmsTaxonomy.filterCategories().map { (typeId, label) ->
            MacCmsTypeItem(typeId = typeId, typeName = label)
        }

    suspend fun fetchLatestForNavCategory(
        category: MacCmsHomeNavCategory,
        limit: Int = 10
    ): MacCmsCategoryFetchResult {
        val typeIds = MacCmsTaxonomy.listQueryTypeIds(category)
        if (typeIds.isEmpty()) return MacCmsCategoryFetchResult(emptyList(), 0)

        val allow = MacCmsTaxonomy.allowedTypeIds(category)
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
            return MacCmsCategoryFetchResult(emptyList(), 0, lastError)
        }
        val enriched = enrichWithDetail(baseUrl = getServerUrl(), rows = sorted)
        return MacCmsCategoryFetchResult(
            items = enriched,
            total = totalCount.coerceAtLeast(enriched.size)
        )
    }

    suspend fun fetchLatestForType(typeId: Int, limit: Int = 10): MacCmsCategoryFetchResult {
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
        val enriched = enrichWithDetail(baseUrl = getServerUrl(), rows = sorted)
        return MacCmsCategoryFetchResult(items = enriched, total = response.total)
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
                if (batch.size < pagesize) exhausted[typeId] = true
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

    suspend fun getPlayerShowNames(): Map<String, String> {
        val baseUrl = getServerUrl()
        if (baseUrl.isBlank()) return emptyMap()
        if (cachedPlayerShowNames.isNotEmpty() && cachedPlayerConfigBaseUrl == baseUrl) {
            return cachedPlayerShowNames
        }
        return refreshPlayerShowNames(baseUrl)
    }

    private suspend fun refreshPlayerShowNames(baseUrl: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            val normalizedBase = macCmsPreferences.normalizeBaseUrl(baseUrl)
            if (normalizedBase.isBlank()) return@withContext emptyMap()

            runCatching {
                val url = "${normalizedBase.trimEnd('/')}/static/js/playerconfig.js"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val body = response.body?.string().orEmpty()
                    val names = MacCmsPlayerConfigParser.parsePlayerShowNames(body)
                    cachedPlayerShowNames = names
                    cachedPlayerConfigBaseUrl = normalizedBase
                    names
                }
            }.getOrElse { emptyMap() }
        }
    }

    suspend fun fetchVodDetail(vodId: Int): MacCmsVodItem? {
        val baseUrl = getServerUrl()
        if (baseUrl.isBlank()) return null

        return try {
            val url = "$baseUrl/api.php/provide/vod/?ac=detail&ids=$vodId"
            val response = macCmsApi.fetchVodList(url)
            if (!response.isSuccessful) return null
            val row = response.body()?.list?.firstOrNull() ?: return null
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
            when (params.sort) {
                MacCmsSortOption.LATEST -> append("&sort_direction=desc")
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
}
