package com.lemon.yingshi.tv.data.remote.model

object MacCmsIds {
    const val PREFIX = "cms:"

    fun encode(vodId: Int): String = "$PREFIX$vodId"

    fun decode(mediaId: String): Int? {
        if (!mediaId.startsWith(PREFIX)) return null
        return mediaId.removePrefix(PREFIX).toIntOrNull()
    }

    fun isMacCmsId(mediaId: String): Boolean = mediaId.startsWith(PREFIX)
}

data class MacCmsEpisodeRef(
    val mediaId: String,
    val vodId: Int,
    val sourceIndex: Int,
    val episodeIndex: Int
) {
    val episodeId: String = buildEpisodeId(mediaId, sourceIndex, episodeIndex)

    companion object {
        fun buildEpisodeId(mediaId: String, sourceIndex: Int, episodeIndex: Int): String =
            "$mediaId:$sourceIndex:$episodeIndex"

        fun parse(episodeId: String): MacCmsEpisodeRef? {
            if (!episodeId.startsWith(MacCmsIds.PREFIX)) return null
            val rest = episodeId.removePrefix(MacCmsIds.PREFIX)
            val parts = rest.split(':')
            if (parts.size < 3) return null
            val vodId = parts[0].toIntOrNull() ?: return null
            val sourceIndex = parts[parts.size - 2].toIntOrNull() ?: return null
            val episodeIndex = parts[parts.size - 1].toIntOrNull() ?: return null
            return MacCmsEpisodeRef(
                mediaId = MacCmsIds.encode(vodId),
                vodId = vodId,
                sourceIndex = sourceIndex,
                episodeIndex = episodeIndex
            )
        }
    }
}

data class MacCmsPlaySource(
    val name: String,
    val episodes: List<MacCmsEpisodeUrl>
)

data class MacCmsEpisodeUrl(
    val title: String,
    val url: String,
    val episodeNumber: Int
)
