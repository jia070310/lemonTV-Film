package com.lemon.yingshi.tv.data.remote.model

import com.google.gson.annotations.SerializedName

data class MacCmsListResponse(
    val code: Int = 0,
    val msg: String? = null,
    val page: Int = 1,
    val pagecount: Int = 1,
    val limit: String? = null,
    val total: Int = 0,
    val list: List<MacCmsVodItem> = emptyList(),
    @SerializedName("class")
    val categories: List<MacCmsTypeItem> = emptyList()
)

data class MacCmsTypeItem(
    @SerializedName("type_id")
    val typeId: Int = 0,
    @SerializedName("type_name")
    val typeName: String = "",
    @SerializedName("type_pid")
    val typePid: Int = 0
)

/** MacCMS /api.php/type/get_list/ 返回的分类树节点 */
data class MacCmsTypeTreeItem(
    @SerializedName("type_id")
    val typeId: Int = 0,
    @SerializedName("type_name")
    val typeName: String = "",
    @SerializedName("type_pid")
    val typePid: Int = 0,
    @SerializedName("type_sort")
    val typeSort: Int = 0,
    @SerializedName("child")
    val children: List<MacCmsTypeTreeItem>? = null
)

data class MacCmsTypeListInfo(
    val total: Int = 0,
    val rows: List<MacCmsTypeTreeItem> = emptyList()
)

data class MacCmsTypeListResponse(
    val code: Int = 0,
    val msg: String? = null,
    val info: MacCmsTypeListInfo? = null
)

data class MacCmsVodItem(
    @SerializedName("vod_id")
    val vodId: Int = 0,
    @SerializedName("vod_name")
    val vodName: String = "",
    @SerializedName("vod_pic")
    val vodPic: String? = null,
    @SerializedName("vod_pic_thumb")
    val vodPicThumb: String? = null,
    @SerializedName("vod_pic_slide")
    val vodPicSlide: String? = null,
    @SerializedName("vod_remarks")
    val vodRemarks: String? = null,
    @SerializedName("vod_score")
    val vodScore: String? = null,
    @SerializedName("vod_year")
    val vodYear: String? = null,
    @SerializedName("vod_area")
    val vodArea: String? = null,
    @SerializedName("vod_lang")
    val vodLang: String? = null,
    @SerializedName("vod_class")
    val vodClass: String? = null,
    @SerializedName("vod_hits")
    val vodHits: Int? = null,
    @SerializedName("type_id")
    val typeId: Int = 0,
    @SerializedName("type_name")
    val typeName: String? = null,
    @SerializedName("vod_blurb")
    val vodBlurb: String? = null,
    @SerializedName("vod_content")
    val vodContent: String? = null,
    @SerializedName("vod_play_from")
    val vodPlayFrom: String? = null,
    @SerializedName("vod_play_note")
    val vodPlayNote: String? = null,
    @SerializedName("vod_play_url")
    val vodPlayUrl: String? = null,
    @SerializedName("vod_actor")
    val vodActor: String? = null,
    @SerializedName("vod_director")
    val vodDirector: String? = null,
    @SerializedName("vod_time")
    val vodTime: String? = null
)

data class MacCmsConnectionResult(
    val success: Boolean,
    val message: String,
    val categoryCount: Int = 0,
    val siteName: String? = null,
    /** 分类接口来源，如 REST 或视频采集接口 */
    val apiSourceLabel: String? = null,
    /** MacCMS 版本描述 */
    val maccmsVersionLabel: String? = null
)

enum class MacCmsSortOption(val label: String, val by: String, val order: String) {
    LATEST("时间排序", "time", "desc"),
    HITS("人气排序", "hits", "desc"),
    SCORE("评分排序", "score", "desc"),
    RANDOM("随机推荐", "rnd", "desc")
}

data class MacCmsFilterParams(
    val typeId: Int = 0,
    val keyword: String = "",
    val area: String = "",
    val lang: String = "",
    val year: String = "",
    val vodClass: String = "",
    val sort: MacCmsSortOption = MacCmsSortOption.LATEST,
    val page: Int = 1,
    val pageSize: Int = 30
)
