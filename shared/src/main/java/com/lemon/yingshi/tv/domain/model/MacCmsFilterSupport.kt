package com.lemon.yingshi.tv.domain.model

import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsTypeExtend
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem

/**
 * 筛选页选项与匹配逻辑，与参考项目 maccmsTaxonomy + maccmsApi 对齐。
 * 优先使用 MacCMS 分类 type_extend（地区/年代/类型/语言）；无扩展时回退本地默认。
 * 苹果 CMS `ac=list` 在 SQL 里只认 year（及 t/wd 等），不认 class/area/lang；
 * 剧情/地区/语言筛选需本地合并 + detail 补全后严格过滤。
 */
object MacCmsFilterSupport {

    const val FILTER_FETCH_PAGE_SIZE = 100
    const val FILTER_MAX_PAGES_PER_TYPE = 200
    const val FILTER_UI_PAGE_SIZE = 20
    /** 首屏快速展示：只拉一页数据量 */
    const val FILTER_QUICK_INITIAL_TARGET = FILTER_UI_PAGE_SIZE
    /** 后台预取池上限，供「加载更多」使用 */
    const val FILTER_INITIAL_TARGET = FILTER_UI_PAGE_SIZE * 5
    /** 内存中保留的已排序条目上限，避免大类目首屏合并 OOM */
    const val FILTER_CATALOG_POOL_CAP = FILTER_INITIAL_TARGET * 2
    /** 筛选网格首屏可见条数（约 2 行 × 5 列），优先补封面 */
    const val FILTER_VISIBLE_ENRICH_COUNT = 10

    data class FilterOptionRow(
        val plot: List<String>,
        val area: List<String>,
        val lang: List<String>,
        val year: List<String>,
        val sort: List<MacCmsSortOption>
    )

    private enum class FilterProfile {
        MOVIE, TV, VARIETY, ANIME, DOCUMENTARY, SHORT, SPORTS, DEFAULT
    }

    /** 与 bang.php / 采集资源常见取值对齐的本地兜底（服务器无 type_extend 时使用） */
    private val sortOptionsDefault = listOf(
        MacCmsSortOption.LATEST,
        MacCmsSortOption.HITS,
        MacCmsSortOption.SCORE
    )

    /** 对齐 bang.php $areas_common */
    private val areasCommon = listOf(
        "大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "新加坡",
        "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"
    )

    /** 对齐 bang.php $langs_common */
    private val langsCommon = listOf(
        "国语", "英语", "粤语", "闽南语", "韩语", "日语", "法语", "德语", "其它"
    )

    /** 对齐 bang.php $years_common */
    private val yearsCommon = listOf(
        "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017",
        "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2009", "2008", "2007",
        "2006", "2005", "2004", "2003", "2002", "2001", "2000"
    )

    private val filterOptionsByProfile: Map<FilterProfile, FilterOptionRow> = mapOf(
        FilterProfile.MOVIE to FilterOptionRow(
            plot = listOf(
                "动作", "喜剧", "爱情", "科幻", "犯罪", "冒险", "恐怖", "动画", "战争",
                "悬疑", "灾难", "青春", "怪兽", "武侠", "奇幻", "警匪", "惊悚", "剧情",
                "历史", "纪录片", "传记", "歌舞", "短片", "运动", "其他"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.TV to FilterOptionRow(
            plot = listOf(
                "爱情", "都市", "青春", "奇幻", "武侠", "古装", "科幻", "喜剧", "悬疑",
                "权谋", "军旅", "家庭", "刑侦", "民国", "现实", "传奇", "逆袭", "猎奇",
                "竞技", "革命", "IP改编", "偶像", "谍战", "冒险", "穿越", "仙侠", "罪案",
                "历史", "年代", "农村", "战争"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.VARIETY to FilterOptionRow(
            plot = listOf(
                "游戏", "脱口秀", "音乐", "情感", "生活", "职场", "喜剧", "美食", "潮流运动",
                "竞技", "影视", "电竞", "推理", "访谈", "亲子", "文化", "互动", "晚会",
                "资讯", "偶像", "舞蹈", "相声", "婚恋", "时尚", "明星访谈", "旅游", "益智"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.ANIME to FilterOptionRow(
            plot = listOf(
                "玄幻", "科幻", "奇幻", "武侠", "仙侠", "都市", "恋爱", "搞笑", "冒险",
                "悬疑", "竞技", "日常", "真人", "治愈", "游戏", "异能", "历史", "古风",
                "智斗", "恐怖", "美食", "音乐", "热血", "励志", "校园", "运动", "魔法",
                "机战", "推理", "动态漫", "小说改", "游戏改", "漫画改", "特摄", "布袋戏", "其他"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.DOCUMENTARY to FilterOptionRow(
            plot = listOf(
                "自然", "美食", "社会", "人文", "历史", "军事", "科技", "财经", "探险",
                "罪案", "竞技", "旅游", "人物", "宇宙", "刑侦", "知识"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.SHORT to FilterOptionRow(
            plot = listOf(
                "男频", "女频", "总裁", "大女主", "战神", "萌娃", "神医", "落难千金", "赘婿",
                "神豪", "大男主", "女帝", "穿越", "重生", "逆袭", "家庭伦理", "虐心",
                "甜宠爱情", "闪婚", "系统流", "追妻", "虐渣复仇", "娱乐圈", "脑洞"
            ),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.SPORTS to FilterOptionRow(
            plot = listOf("篮球", "足球", "网球", "竞技", "NBA", "赛事回放"),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        ),
        FilterProfile.DEFAULT to FilterOptionRow(
            plot = emptyList(),
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = sortOptionsDefault
        )
    )

    private fun profileForCategoryLabel(label: String): FilterProfile = when {
        label.contains("短剧") -> FilterProfile.SHORT
        label.contains("纪录") || label.contains("记录") -> FilterProfile.DOCUMENTARY
        label.contains("体育") || label.contains("足球") || label.contains("篮球") ||
            label.contains("网球") -> FilterProfile.SPORTS
        label.contains("电影") -> FilterProfile.MOVIE
        label.contains("综艺") -> FilterProfile.VARIETY
        label.contains("动漫") || label.contains("动画") || label.contains("漫剧") -> FilterProfile.ANIME
        label.contains("剧") || label.contains("电视") -> FilterProfile.TV
        else -> FilterProfile.DEFAULT
    }

    fun filterOptionsFor(categoryLabel: String): FilterOptionRow {
        val profile = profileForCategoryLabel(categoryLabel)
        return filterOptionsByProfile[profile] ?: filterOptionsByProfile[FilterProfile.DEFAULT]!!
    }

    /**
     * 用后台 type_extend 生成筛选项。
     * 类型(class)可跟服务器；地区/语言/年代固定用采集源可筛通的公共列表
     * （避免服务器仍残留「中国香港/内地/2026」等导致筛不出结果）。
     */
    fun filterOptionsFor(
        categoryLabel: String,
        extend: MacCmsTypeExtend?,
        childExtend: MacCmsTypeExtend? = null
    ): FilterOptionRow {
        val fallback = filterOptionsFor(categoryLabel)
        val source = when {
            childExtend?.hasFilterOptions() == true -> childExtend
            extend?.hasFilterOptions() == true -> extend
            else -> null
        }
        return FilterOptionRow(
            plot = source?.splitPlot()?.ifEmpty { null } ?: fallback.plot,
            area = areasCommon,
            lang = langsCommon,
            year = yearsCommon,
            sort = fallback.sort
        )
    }

    data class FilterState(
        val typeIds: List<Int>,
        val plot: String = "",
        val area: String = "",
        val lang: String = "",
        val year: String = "",
        val sort: MacCmsSortOption = MacCmsSortOption.LATEST
    )

    fun filterListTotalUsesClientCount(plot: String, area: String, lang: String): Boolean =
        plot.isNotBlank() || area.isNotBlank() || lang.isNotBlank()

    /**
     * 估算某分类在 MacCMS `ac=list` 下的总页数。
     * 第三方源常忽略 `pagesize=100` 只返回约 20 条，不能再用「本批 < 请求条数」判定结束。
     */
    fun resolveFilterTypePageCount(
        pagecount: Int,
        total: Int,
        batchSize: Int,
        requestedPageSize: Int = FILTER_FETCH_PAGE_SIZE
    ): Int {
        if (pagecount > 1) return pagecount
        if (total > 0 && batchSize > 0) {
            return ((total + batchSize - 1) / batchSize).coerceAtLeast(1)
        }
        return if (batchSize < requestedPageSize) 1 else FILTER_MAX_PAGES_PER_TYPE
    }

    fun isFilterTypePageExhausted(currentPage: Int, batchSize: Int, typePageCount: Int): Boolean =
        batchSize == 0 || currentPage >= typePageCount

    fun needsFilterDetailEnrichment(plot: String, area: String, lang: String, year: String): Boolean =
        plot.isNotBlank() || area.isNotBlank() || lang.isNotBlank() || year.isNotBlank()

    private val areaAliases: Map<String, List<String>> = mapOf(
        "大陆" to listOf("大陆", "内地", "国产", "中国大陆"),
        "内地" to listOf("内地", "大陆", "国产", "中国大陆"),
        "国产" to listOf("国产", "大陆", "内地", "中国大陆"),
        "香港" to listOf("香港", "中国香港", "港台"),
        "台湾" to listOf("台湾", "中国台湾", "港台"),
        "港台" to listOf("港台", "香港", "台湾", "中国香港", "中国台湾"),
        "日韩" to listOf("日韩", "日本", "韩国"),
        "欧美" to listOf("欧美", "美国", "英国", "法国", "加拿大", "西班牙", "俄罗斯"),
        "其它" to listOf("其它", "其他"),
        "其他" to listOf("其他", "其它")
    )

    fun areaMatchesFilter(vodArea: String, filterArea: String): Boolean {
        if (filterArea.isBlank()) return true
        val a = vodArea.trim()
        if (a.isEmpty()) return false
        val aliases = areaAliases[filterArea]
        if (aliases != null) {
            return aliases.any { x -> a.contains(x) || x.contains(a) }
        }
        // 双向兜底：筛选项含「中国香港」而片子写「香港」也能命中
        areaAliases.entries
            .firstOrNull { (key, _) -> key.contains(filterArea) || filterArea.contains(key) }
            ?.value
            ?.let { related ->
                if (related.any { x -> a.contains(x) || x.contains(a) }) return true
            }
        return a.contains(filterArea) || filterArea.contains(a)
    }

    fun langMatchesFilter(vodLang: String, filterLang: String): Boolean {
        if (filterLang.isBlank()) return true
        val s = vodLang.trim()
        if (s.isEmpty()) return false
        if (s.contains(filterLang)) return true
        if (filterLang == "其它" || filterLang == "其他") {
            if (s.contains("其它") || s.contains("其他")) return true
            return !listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "法语", "德语")
                .any { k -> s.contains(k) }
        }
        return false
    }

    fun classMatchesPlot(vodClass: String, plot: String): Boolean {
        if (plot.isBlank()) return true
        val c = vodClass.replace("\\s".toRegex(), "")
        if (c.isEmpty()) return false
        val parts = c.split(Regex("[,，|]"))
        return parts.any { p -> p.contains(plot) || plot.contains(p) }
    }

    /** 列表合并：有字段则按字段过滤；缺字段先保留，等详情补全后再严格剔除 */
    fun rowMatchesFiltersListMerge(row: MacCmsVodItem, f: FilterState): Boolean =
        rowMatchesFilters(row, f)

    /** 详情补全后严格匹配（缺地区/语言/剧情/年份则不算命中） */
    fun rowMatchesFiltersStrict(row: MacCmsVodItem, f: FilterState): Boolean {
        if (!f.typeIds.contains(row.typeId)) return false
        if (f.year.isNotBlank() && row.vodYear.orEmpty().trim() != f.year) return false
        if (f.area.isNotBlank() && !areaMatchesFilter(row.vodArea.orEmpty(), f.area)) return false
        if (f.lang.isNotBlank() && !langMatchesFilter(row.vodLang.orEmpty(), f.lang)) return false
        if (f.plot.isNotBlank() && !classMatchesPlot(row.vodClass.orEmpty(), f.plot)) return false
        return true
    }

    fun rowMatchesFilters(row: MacCmsVodItem, f: FilterState): Boolean {
        if (!f.typeIds.contains(row.typeId)) return false
        if (f.year.isNotBlank()) {
            val y = row.vodYear.orEmpty().trim()
            if (y.isNotEmpty() && y != f.year) return false
        }
        if (f.area.isNotBlank()) {
            val a = row.vodArea.orEmpty().trim()
            if (a.isNotEmpty() && !areaMatchesFilter(a, f.area)) return false
        }
        if (f.lang.isNotBlank()) {
            val lang = row.vodLang.orEmpty().trim()
            if (lang.isNotEmpty() && !langMatchesFilter(lang, f.lang)) return false
        }
        if (f.plot.isNotBlank()) {
            val cls = row.vodClass.orEmpty().replace("\\s".toRegex(), "")
            if (cls.isNotEmpty() && !classMatchesPlot(cls, f.plot)) return false
        }
        return true
    }

    fun sortFiltered(rows: List<MacCmsVodItem>, sort: MacCmsSortOption): List<MacCmsVodItem> =
        when (sort) {
            MacCmsSortOption.HITS -> rows.sortedByDescending { it.vodHits ?: 0 }
            MacCmsSortOption.SCORE -> rows.sortedByDescending { it.vodScore?.toFloatOrNull() ?: 0f }
            else -> rows.sortedByDescending { vodTimeMs(it.vodTime) }
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
