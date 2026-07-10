package com.lemon.yingshi.tv.domain.model

import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem

/**
 * 筛选页选项与匹配逻辑，与参考项目 maccmsTaxonomy + maccmsApi 对齐。
 * 苹果 CMS `ac=list` 在 SQL 里只认 year（及 t/wd 等），不认 class/area/lang；
 * 剧情/地区/语言筛选需本地合并 + detail 补全后严格过滤。
 */
object MacCmsFilterSupport {

    const val FILTER_FETCH_PAGE_SIZE = 100
    const val FILTER_MAX_PAGES_PER_TYPE = 200
    const val FILTER_UI_PAGE_SIZE = 20
    const val FILTER_INITIAL_TARGET = FILTER_UI_PAGE_SIZE * 5

    data class FilterOptionRow(
        val plot: List<String>,
        val area: List<String>,
        val lang: List<String>,
        val year: List<String>,
        val sort: List<MacCmsSortOption>
    )

    private enum class FilterProfile {
        MOVIE, TV, VARIETY, ANIME, DEFAULT
    }

    private val filterOptionsByProfile: Map<FilterProfile, FilterOptionRow> = mapOf(
        FilterProfile.MOVIE to FilterOptionRow(
            plot = listOf(
                "喜剧", "爱情", "恐怖", "动作", "科幻", "剧情", "战争", "警匪", "犯罪",
                "动画", "奇幻", "武侠", "冒险", "枪战", "悬疑", "惊悚", "经典", "青春",
                "文艺", "微电影", "古装", "历史", "运动", "农村", "儿童", "网络电影"
            ),
            area = listOf(
                "大陆", "香港", "台湾", "美国", "法国", "英国", "日本", "韩国",
                "德国", "泰国", "印度", "意大利", "西班牙", "加拿大", "其他"
            ),
            lang = listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "法语", "德语", "其它"),
            year = yearOptions(),
            sort = listOf(MacCmsSortOption.LATEST, MacCmsSortOption.HITS, MacCmsSortOption.SCORE)
        ),
        FilterProfile.TV to FilterOptionRow(
            plot = listOf(
                "喜剧", "爱情", "恐怖", "动作", "科幻", "剧情", "战争", "警匪", "犯罪",
                "动画", "奇幻", "武侠", "冒险", "枪战", "悬疑", "惊悚", "经典", "青春",
                "文艺", "微电影", "古装", "历史", "运动", "农村", "儿童", "网络电影"
            ),
            area = listOf(
                "大陆", "香港", "台湾", "美国", "法国", "英国", "日本", "韩国",
                "德国", "泰国", "印度", "意大利", "西班牙", "加拿大", "其他"
            ),
            lang = listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "其它"),
            year = yearOptions(),
            sort = listOf(MacCmsSortOption.LATEST, MacCmsSortOption.HITS, MacCmsSortOption.SCORE)
        ),
        FilterProfile.VARIETY to FilterOptionRow(
            plot = listOf(
                "选秀", "情感", "访谈", "播报", "旅游", "音乐", "美食", "纪实",
                "曲艺", "生活", "游戏互动", "财经", "求职"
            ),
            area = listOf("内地", "港台", "日韩", "欧美"),
            lang = listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "其它"),
            year = yearOptions(),
            sort = listOf(MacCmsSortOption.LATEST, MacCmsSortOption.HITS, MacCmsSortOption.SCORE)
        ),
        FilterProfile.ANIME to FilterOptionRow(
            plot = listOf(
                "情感", "科幻", "热血", "推理", "搞笑", "冒险", "萝莉", "校园", "动作",
                "机战", "运动", "战争", "少年", "少女", "社会", "原创", "亲子", "益智", "励志", "其他"
            ),
            area = listOf("国产", "日本", "欧美", "其他"),
            lang = listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "其它"),
            year = yearOptions(),
            sort = listOf(MacCmsSortOption.LATEST, MacCmsSortOption.HITS, MacCmsSortOption.SCORE)
        ),
        FilterProfile.DEFAULT to FilterOptionRow(
            plot = emptyList(),
            area = listOf(
                "大陆", "香港", "台湾", "美国", "法国", "英国", "日本", "韩国",
                "德国", "泰国", "印度", "意大利", "西班牙", "加拿大", "其他"
            ),
            lang = listOf("国语", "英语", "粤语", "闽南语", "韩语", "日语", "其它"),
            year = yearOptions(),
            sort = listOf(MacCmsSortOption.LATEST, MacCmsSortOption.HITS, MacCmsSortOption.SCORE)
        )
    )

    private fun profileForCategoryLabel(label: String): FilterProfile = when {
        label.contains("电影") -> FilterProfile.MOVIE
        label.contains("综艺") -> FilterProfile.VARIETY
        label.contains("动漫") || label.contains("动画") -> FilterProfile.ANIME
        label.contains("剧") || label.contains("电视") -> FilterProfile.TV
        else -> FilterProfile.DEFAULT
    }

    fun filterOptionsFor(categoryLabel: String): FilterOptionRow {
        val profile = profileForCategoryLabel(categoryLabel)
        return filterOptionsByProfile[profile] ?: filterOptionsByProfile[FilterProfile.DEFAULT]!!
    }

    private fun yearOptions(): List<String> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return (currentYear downTo currentYear - 12).map { it.toString() }
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

    fun needsFilterDetailEnrichment(plot: String, area: String, lang: String, year: String): Boolean =
        plot.isNotBlank() || area.isNotBlank() || lang.isNotBlank() || year.isNotBlank()

    private val areaAliases: Map<String, List<String>> = mapOf(
        "大陆" to listOf("大陆", "内地", "国产"),
        "内地" to listOf("内地", "大陆"),
        "国产" to listOf("国产", "大陆", "内地"),
        "香港" to listOf("香港", "港台"),
        "台湾" to listOf("台湾", "港台"),
        "日韩" to listOf("日韩", "日本", "韩国"),
        "欧美" to listOf("欧美", "美国", "英国", "法国")
    )

    fun areaMatchesFilter(vodArea: String, filterArea: String): Boolean {
        if (filterArea.isBlank()) return true
        val a = vodArea.trim()
        if (a.isEmpty()) return false
        val aliases = areaAliases[filterArea]
        if (aliases != null) {
            return aliases.any { x -> a.contains(x) || x.contains(a) }
        }
        return a.contains(filterArea) || filterArea.contains(a)
    }

    fun langMatchesFilter(vodLang: String, filterLang: String): Boolean {
        if (filterLang.isBlank()) return true
        val s = vodLang.trim()
        if (s.isEmpty()) return false
        if (s.contains(filterLang)) return true
        if (filterLang == "其它" || filterLang == "其他") {
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

    /** 列表合并阶段：缺字段时不误杀 */
    fun rowMatchesFiltersListMerge(row: MacCmsVodItem, f: FilterState): Boolean {
        if (needsFilterDetailEnrichment(f.plot, f.area, f.lang, f.year)) {
            return f.typeIds.contains(row.typeId)
        }
        return rowMatchesFilters(row, f)
    }

    /** 详情补全后严格匹配 */
    fun rowMatchesFiltersStrict(row: MacCmsVodItem, f: FilterState): Boolean {
        if (!f.typeIds.contains(row.typeId)) return false
        if (f.year.isNotBlank() && row.vodYear.orEmpty().trim() != f.year) return false
        if (!areaMatchesFilter(row.vodArea.orEmpty(), f.area)) return false
        if (!langMatchesFilter(row.vodLang.orEmpty(), f.lang)) return false
        if (!classMatchesPlot(row.vodClass.orEmpty(), f.plot)) return false
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
