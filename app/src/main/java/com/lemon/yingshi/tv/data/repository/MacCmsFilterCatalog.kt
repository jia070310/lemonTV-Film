package com.lemon.yingshi.tv.data.repository

import com.lemon.yingshi.tv.data.remote.model.MacCmsFilterParams
import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport

/** 筛选目录续传状态，对应参考项目 FilterCatalogContinuation */
data class FilterCatalogContinuation(
    val pool: MutableList<MacCmsVodItem> = mutableListOf(),
    val seen: MutableSet<Int> = mutableSetOf(),
    val exhausted: MutableMap<Int, Boolean> = mutableMapOf(),
    var nextPg: Int = 1,
    var apiTotalSum: Int = 0,
    var totalsCaptured: Boolean = false
)

data class FilterCatalogResult(
    val continuation: FilterCatalogContinuation,
    val sorted: List<MacCmsVodItem>,
    val apiTotalSum: Int,
    val exhaustedAll: Boolean
)

fun createEmptyFilterContinuation(typeIds: List<Int>): FilterCatalogContinuation {
    val exhausted = mutableMapOf<Int, Boolean>()
    typeIds.forEach { exhausted[it] = false }
    return FilterCatalogContinuation(exhausted = exhausted)
}

internal fun mergeListRowWithDetail(base: MacCmsVodItem, detail: MacCmsVodItem): MacCmsVodItem =
    base.copy(
        vodClass = detail.vodClass ?: base.vodClass,
        vodArea = detail.vodArea ?: base.vodArea,
        vodLang = detail.vodLang ?: base.vodLang,
        vodYear = detail.vodYear ?: base.vodYear,
        vodHits = detail.vodHits ?: base.vodHits,
        vodScore = detail.vodScore ?: base.vodScore,
        vodRemarks = detail.vodRemarks ?: base.vodRemarks,
        vodPic = detail.vodPic ?: base.vodPic,
        vodPicThumb = detail.vodPicThumb ?: base.vodPicThumb,
        vodPicSlide = detail.vodPicSlide ?: base.vodPicSlide,
        vodLevel = detail.vodLevel ?: base.vodLevel
    )
