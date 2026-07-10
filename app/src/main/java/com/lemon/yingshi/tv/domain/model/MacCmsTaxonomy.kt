package com.lemon.yingshi.tv.domain.model

/**
 * 与 MacCMS 后台「视频 / 分类」ID 对齐，参考柠檬影视TV1 的 maccmsTaxonomy。
 * 首页四大类通过多个子类 type_id 聚合拉取，不依赖接口返回的 class 字段。
 */
enum class MacCmsHomeNavCategory(val label: String, val sortIndex: Int) {
    TV("电视剧", 0),
    MOVIE("电影", 1),
    VARIETY("综艺", 2),
    ANIME("动漫", 3);

    companion object {
        val ordered = listOf(TV, MOVIE, VARIETY, ANIME)
    }
}

sealed class MacCmsHomeSectionRef {
    abstract val sectionKey: String
    abstract val displayName: String
    abstract val filterTypeId: Int

    data class Main(val category: MacCmsHomeNavCategory) : MacCmsHomeSectionRef() {
        override val sectionKey: String = MacCmsTaxonomy.mainSectionKey(category)
        override val displayName: String = category.label
        override val filterTypeId: Int =
            MacCmsTaxonomy.listQueryTypeIds(category).firstOrNull() ?: 0
    }

    data class Secondary(val typeId: Int, val label: String) : MacCmsHomeSectionRef() {
        override val sectionKey: String = MacCmsTaxonomy.typeSectionKey(typeId)
        override val displayName: String = label
        override val filterTypeId: Int = typeId
    }
}

object MacCmsTaxonomy {
    private const val MAIN_PREFIX = "main:"
    private const val TYPE_PREFIX = "type:"

    private val typeIdsByNavCategory: Map<MacCmsHomeNavCategory, List<Int>> = mapOf(
        MacCmsHomeNavCategory.TV to listOf(13, 14, 15, 16, 23, 41),
        MacCmsHomeNavCategory.MOVIE to listOf(6, 7, 8, 9, 10, 11, 12, 19, 20, 21, 37, 42, 43, 44),
        MacCmsHomeNavCategory.VARIETY to listOf(24, 25, 26, 27),
        MacCmsHomeNavCategory.ANIME to listOf(28, 29, 30, 31)
    )

    /** 部分站点子类 list 为空，需额外请求父类 ID（如综艺 t=3） */
    private val anchorTypeIds: Map<MacCmsHomeNavCategory, List<Int>> = mapOf(
        MacCmsHomeNavCategory.VARIETY to listOf(3)
    )

    private val secondaryTypeLabels: Map<Int, String> = mapOf(
        13 to "国产剧",
        14 to "港台剧",
        15 to "日韩剧",
        16 to "欧美剧",
        23 to "短剧",
        41 to "Netflix自制剧",
        6 to "动作片",
        7 to "喜剧片",
        8 to "爱情片",
        9 to "科幻片",
        10 to "恐怖片",
        11 to "剧情片",
        12 to "战争片",
        19 to "动画片",
        20 to "奇幻片",
        21 to "悬疑片",
        37 to "动漫电影",
        44 to "Netflix电影",
        43 to "邵氏电影",
        42 to "4K电影",
        24 to "大陆综艺",
        25 to "日韩综艺",
        26 to "港台综艺",
        27 to "欧美综艺",
        28 to "国产动漫",
        29 to "日韩动漫",
        30 to "欧美动漫",
        31 to "港台动漫"
    )

    fun mainSectionKey(category: MacCmsHomeNavCategory): String = "$MAIN_PREFIX${category.label}"

    fun typeSectionKey(typeId: Int): String = "$TYPE_PREFIX$typeId"

    fun listQueryTypeIds(category: MacCmsHomeNavCategory): List<Int> {
        val base = typeIdsByNavCategory[category].orEmpty()
        val extra = anchorTypeIds[category].orEmpty()
        return (base + extra).distinct()
    }

    fun allowedTypeIds(category: MacCmsHomeNavCategory): Set<Int> =
        listQueryTypeIds(category).toSet()

    fun allSecondaryTypeIds(): List<Int> = secondaryTypeLabels.keys.sorted()

    fun secondaryLabel(typeId: Int): String =
        secondaryTypeLabels[typeId] ?: "分类$typeId"

    fun defaultMainLabelsText(): String =
        MacCmsHomeNavCategory.ordered.joinToString("、") { it.label }

    fun defaultSectionOrder(): List<String> {
        val mainKeys = MacCmsHomeNavCategory.ordered.map { mainSectionKey(it) }
        val secondaryKeys = allSecondaryTypeIds().map { typeSectionKey(it) }
        return mainKeys + secondaryKeys
    }

    fun defaultVisibleSectionKeys(): Set<String> =
        MacCmsHomeNavCategory.ordered.map { mainSectionKey(it) }.toSet()

    fun parseSectionKey(key: String): MacCmsHomeSectionRef? {
        if (key.startsWith(MAIN_PREFIX)) {
            val label = key.removePrefix(MAIN_PREFIX)
            val category = MacCmsHomeNavCategory.ordered.find { it.label == label } ?: return null
            return MacCmsHomeSectionRef.Main(category)
        }
        if (key.startsWith(TYPE_PREFIX)) {
            val typeId = key.removePrefix(TYPE_PREFIX).toIntOrNull() ?: return null
            return MacCmsHomeSectionRef.Secondary(typeId, secondaryLabel(typeId))
        }
        return null
    }

    fun resolveSectionOrder(savedOrder: List<String>): List<String> {
        val available = defaultSectionOrder()
        val ordered = savedOrder.filter { it in available }
        val missing = available.filterNot { it in ordered }
        return ordered + missing
    }

    fun resolveVisibleSectionKeys(
        savedVisible: Set<String>,
        visibilityConfigured: Boolean
    ): Set<String> = if (visibilityConfigured) savedVisible else defaultVisibleSectionKeys()

    fun buildHomeSectionRefs(
        savedOrder: List<String>,
        savedVisible: Set<String>,
        visibilityConfigured: Boolean
    ): List<MacCmsHomeSectionRef> {
        val visible = resolveVisibleSectionKeys(savedVisible, visibilityConfigured)
        return resolveSectionOrder(savedOrder)
            .mapNotNull { parseSectionKey(it) }
            .filter { it.sectionKey in visible }
    }

    /** 筛选页使用的子分类列表（全部二级类目） */
    fun filterCategories(): List<Pair<Int, String>> =
        allSecondaryTypeIds().map { it to secondaryLabel(it) }

    data class FilterTreeChild(val typeId: Int, val label: String)

    data class FilterTreeCategory(
        val category: MacCmsHomeNavCategory,
        val children: List<FilterTreeChild>
    )

    /** 筛选页左侧树：四大类 + 各自二级子类 */
    fun filterTreeCategories(): List<FilterTreeCategory> =
        MacCmsHomeNavCategory.ordered.map { nav ->
            FilterTreeCategory(
                category = nav,
                children = typeIdsByNavCategory[nav].orEmpty().map { typeId ->
                    FilterTreeChild(typeId = typeId, label = secondaryLabel(typeId))
                }
            )
        }

    /** 当前侧栏选中项对应的查询 type_id 列表；selectedTypeId=0 表示该大类「全部」 */
    fun filterTypeIdsForSelection(
        navCategory: MacCmsHomeNavCategory,
        selectedTypeId: Int
    ): List<Int> = if (selectedTypeId > 0) {
        listOf(selectedTypeId)
    } else {
        listQueryTypeIds(navCategory)
    }

    fun navCategoryForTypeId(typeId: Int): MacCmsHomeNavCategory? {
        MacCmsHomeNavCategory.ordered.forEach { nav ->
            if (typeId in allowedTypeIds(nav)) return nav
        }
        return null
    }
}
