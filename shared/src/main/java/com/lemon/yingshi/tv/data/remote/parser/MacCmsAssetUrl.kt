package com.lemon.yingshi.tv.data.remote.parser

object MacCmsAssetUrl {
    fun resolve(baseUrl: String, href: String?): String {
        val s = href?.trim().orEmpty()
        if (s.isEmpty()) return ""
        if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) {
            return s
        }
        if (s.startsWith("//")) return "http:$s"
        if (s.startsWith("mac:", ignoreCase = true)) {
            return s.replace(Regex("^mac:", RegexOption.IGNORE_CASE), "$baseUrl/")
        }
        return if (s.startsWith("/")) "$baseUrl$s" else "$baseUrl/$s"
    }

    fun pickPosterRaw(
        vodPic: String?,
        vodPicThumb: String? = null,
        vodPicSlide: String? = null
    ): String {
        return vodPic?.trim()?.takeIf { it.isNotBlank() }
            ?: vodPicThumb?.trim()?.takeIf { it.isNotBlank() }
            ?: vodPicSlide?.trim()?.takeIf { it.isNotBlank() }
            ?: ""
    }
}
