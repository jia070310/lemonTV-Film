package com.lemon.yingshi.tv.domain.model

import com.lemon.yingshi.tv.data.remote.model.MacCmsTypeItem
import com.lemon.yingshi.tv.data.remote.model.MacCmsTypeTreeItem

/** 服务器顶级分类（含子类） */
data class MacCmsNavCategory(
    val typeId: Int,
    val label: String,
    val children: List<MacCmsTypeChild> = emptyList()
)

data class MacCmsTypeChild(
    val typeId: Int,
    val label: String
)

sealed class MacCmsHomeSectionRef {
    abstract val sectionKey: String
    abstract val displayName: String
    abstract val filterTypeId: Int
    abstract val navTypeId: Int

    data class Main(
        val category: MacCmsNavCategory,
        override val sectionKey: String
    ) : MacCmsHomeSectionRef() {
        override val displayName: String = category.label
        override val filterTypeId: Int = 0
        override val navTypeId: Int = category.typeId
    }

    data class Secondary(
        val typeId: Int,
        val label: String,
        override val navTypeId: Int,
        override val sectionKey: String
    ) : MacCmsHomeSectionRef() {
        override val displayName: String = label
        override val filterTypeId: Int = typeId
    }
}

/**
 * 由 MacCMS `/api.php/type/get_list/` 构建的分类快照。
 * 每次刷新首页或切换服务器时应重新拉取，不使用固定 ID 映射。
 */
class MacCmsTaxonomy private constructor(
    val topCategories: List<MacCmsNavCategory>,
    /** 分类数据来源，用于设置页展示 */
    val sourceLabel: String = SOURCE_PROVIDE
) {
    private val childByTypeId: Map<Int, MacCmsTypeChild>
    private val parentByChildTypeId: Map<Int, MacCmsNavCategory>

    init {
        val childMap = linkedMapOf<Int, MacCmsTypeChild>()
        val parentMap = linkedMapOf<Int, MacCmsNavCategory>()
        topCategories.forEach { nav ->
            nav.children.forEach { child ->
                childMap[child.typeId] = child
                parentMap[child.typeId] = nav
            }
        }
        childByTypeId = childMap
        parentByChildTypeId = parentMap
    }

    val categoryCount: Int
        get() = topCategories.size + allSecondaryTypeIds().size

    fun listQueryTypeIds(category: MacCmsNavCategory): List<Int> {
        val childIds = category.children.map { it.typeId }
        return if (childIds.isEmpty()) {
            listOf(category.typeId)
        } else {
            (listOf(category.typeId) + childIds).distinct()
        }
    }

    fun allowedTypeIds(category: MacCmsNavCategory): Set<Int> =
        listQueryTypeIds(category).toSet()

    fun allSecondaryTypeIds(): List<Int> {
        val ordered = linkedSetOf<Int>()
        topCategories.forEach { nav ->
            nav.children.forEach { child ->
                ordered.add(child.typeId)
            }
        }
        return ordered.toList()
    }

    fun secondaryLabel(typeId: Int): String =
        childByTypeId[typeId]?.label
            ?: topCategories.find { it.typeId == typeId }?.label
            ?: "分类$typeId"

    fun defaultMainLabelsText(): String =
        topCategories.joinToString("、") { it.label }

    fun defaultSectionOrder(): List<String> {
        val mainKeys = topCategories.map { mainSectionKey(it.typeId) }
        val secondaryKeys = allSecondaryTypeIds().map { typeSectionKey(it) }
        return mainKeys + secondaryKeys
    }

    fun defaultVisibleSectionKeys(): Set<String> =
        topCategories.map { mainSectionKey(it.typeId) }.toSet()

    fun parseSectionKey(key: String): MacCmsHomeSectionRef? {
        if (key.startsWith(MAIN_PREFIX)) {
            val suffix = key.removePrefix(MAIN_PREFIX)
            suffix.toIntOrNull()?.let { typeId ->
                topCategories.find { it.typeId == typeId }?.let { nav ->
                    return MacCmsHomeSectionRef.Main(nav, mainSectionKey(nav.typeId))
                }
            }
            topCategories.find { it.label == suffix }?.let { nav ->
                return MacCmsHomeSectionRef.Main(nav, mainSectionKey(nav.typeId))
            }
            return null
        }
        if (key.startsWith(TYPE_PREFIX)) {
            val typeId = key.removePrefix(TYPE_PREFIX).toIntOrNull() ?: return null
            childByTypeId[typeId]?.let { child ->
                val parentId = parentByChildTypeId[typeId]?.typeId ?: typeId
                return MacCmsHomeSectionRef.Secondary(
                    typeId = typeId,
                    label = child.label,
                    navTypeId = parentId,
                    sectionKey = typeSectionKey(typeId)
                )
            }
            topCategories.find { it.typeId == typeId }?.let { nav ->
                return MacCmsHomeSectionRef.Main(nav, mainSectionKey(nav.typeId))
            }
            return MacCmsHomeSectionRef.Secondary(
                typeId = typeId,
                label = secondaryLabel(typeId),
                navTypeId = parentByChildTypeId[typeId]?.typeId ?: typeId,
                sectionKey = typeSectionKey(typeId)
            )
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

    fun filterCategories(): List<Pair<Int, String>> =
        allSecondaryTypeIds().map { it to secondaryLabel(it) }

    data class FilterTreeChild(val typeId: Int, val label: String)

    data class FilterTreeCategory(
        val category: MacCmsNavCategory,
        val children: List<FilterTreeChild>
    )

    fun filterTreeCategories(): List<FilterTreeCategory> =
        topCategories.map { nav ->
            FilterTreeCategory(
                category = nav,
                children = nav.children.map { child ->
                    FilterTreeChild(typeId = child.typeId, label = child.label)
                }
            )
        }

    fun filterTypeIdsForSelection(
        navCategory: MacCmsNavCategory,
        selectedTypeId: Int
    ): List<Int> = if (selectedTypeId > 0) {
        listOf(selectedTypeId)
    } else {
        listQueryTypeIds(navCategory)
    }

    fun navCategoryForTypeId(typeId: Int): MacCmsNavCategory? =
        parentByChildTypeId[typeId] ?: topCategories.find { it.typeId == typeId }

    fun navCategoryByTypeId(typeId: Int): MacCmsNavCategory? =
        topCategories.find { it.typeId == typeId }

    companion object {
        private const val MAIN_PREFIX = "main:"
        private const val TYPE_PREFIX = "type:"
        const val SOURCE_REST = "REST 分类接口"
        const val SOURCE_PROVIDE = "视频采集接口"

        fun mainSectionKey(typeId: Int): String = "$MAIN_PREFIX$typeId"

        fun typeSectionKey(typeId: Int): String = "$TYPE_PREFIX$typeId"

        fun fromServerTypes(rows: List<MacCmsTypeTreeItem>): MacCmsTaxonomy {
            val topCategories = rows.withIndex()
                .filter { (_, row) -> row.typePid == 0 && row.typeId > 0 }
                .sortedWith(
                    compareBy<IndexedValue<MacCmsTypeTreeItem>> { it.value.typeSort }
                        .thenBy { it.index }
                )
                .map { (_, row) ->
                    MacCmsNavCategory(
                        typeId = row.typeId,
                        label = row.typeName,
                        children = (row.children ?: emptyList()).withIndex()
                            .filter { (_, child) -> child.typeId > 0 }
                            .sortedWith(
                                compareBy<IndexedValue<MacCmsTypeTreeItem>> { it.value.typeSort }
                                    .thenBy { it.index }
                            )
                            .map { (_, child) ->
                                MacCmsTypeChild(typeId = child.typeId, label = child.typeName)
                            }
                    )
                }
            return MacCmsTaxonomy(topCategories, SOURCE_REST)
        }

        /**
         * 从 provide/vod ac=list 返回的扁平 class 列表构建分类树（兼容未开启 REST API 的站点）。
         */
        fun fromFlatTypeItems(items: List<MacCmsTypeItem>): MacCmsTaxonomy {
            val valid = items.filter { it.typeId > 0 && it.typeName.isNotBlank() }
            if (valid.isEmpty()) return MacCmsTaxonomy(emptyList())

            val indexById = items.withIndex().associate { (index, item) -> item.typeId to index }
            fun sortTypes(list: List<MacCmsTypeItem>): List<MacCmsTypeItem> =
                list.sortedWith(
                    compareBy<MacCmsTypeItem> { it.typeSort }
                        .thenBy { indexById[it.typeId] ?: Int.MAX_VALUE }
                )

            val byId = valid.associateBy { it.typeId }
            val roots = sortTypes(
                valid
                    .filter { item -> valid.any { it.typePid == item.typeId } }
                    .distinctBy { it.typeId }
                    .ifEmpty {
                        valid.filter { it.typePid == 0 || it.typePid !in byId }.distinctBy { it.typeId }
                    }
            )

            val topCategories = roots.map { root ->
                MacCmsNavCategory(
                    typeId = root.typeId,
                    label = root.typeName,
                    children = sortTypes(valid.filter { it.typePid == root.typeId })
                        .map { child -> MacCmsTypeChild(typeId = child.typeId, label = child.typeName) }
                )
            }
            return MacCmsTaxonomy(topCategories, SOURCE_PROVIDE)
        }
    }
}
