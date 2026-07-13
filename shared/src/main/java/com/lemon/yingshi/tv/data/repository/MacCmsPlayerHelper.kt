package com.lemon.yingshi.tv.data.repository

import com.lemon.yingshi.tv.data.remote.model.MacCmsEpisodeRef
import com.lemon.yingshi.tv.data.remote.model.MacCmsIds
import com.lemon.yingshi.tv.data.remote.model.MacCmsPlaySource
import com.lemon.yingshi.tv.data.remote.parser.MacCmsPlayUrlParser
import javax.inject.Inject
import javax.inject.Singleton

data class MacCmsPlayerEpisode(
    val id: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String,
    val path: String
)

@Singleton
class MacCmsPlayerHelper @Inject constructor(
    private val macCmsRepository: MacCmsRepository
) {
    suspend fun loadEpisodes(mediaId: String, episodeId: String?): List<MacCmsPlayerEpisode> {
        if (!MacCmsIds.isMacCmsId(mediaId)) return emptyList()
        val vodId = MacCmsIds.decode(mediaId) ?: return emptyList()
        val sourceIndex = episodeId?.let { MacCmsEpisodeRef.parse(it)?.sourceIndex } ?: 0
        val vod = macCmsRepository.fetchVodDetail(vodId) ?: return emptyList()
        val playerNames = macCmsRepository.getPlayerShowNames()
        val sources = MacCmsPlayUrlParser.parse(
            vodPlayFrom = vod.vodPlayFrom,
            vodPlayUrl = vod.vodPlayUrl,
            vodPlayNote = vod.vodPlayNote,
            playerShowNames = playerNames
        )
        val source = sources.getOrNull(sourceIndex) ?: return emptyList()
        return source.episodes.mapIndexed { index, episode ->
            MacCmsPlayerEpisode(
                id = MacCmsEpisodeRef.buildEpisodeId(mediaId, sourceIndex, index),
                episodeNumber = episode.episodeNumber,
                seasonNumber = 1,
                title = episode.title,
                path = episode.url
            )
        }
    }

    suspend fun resolveSourceIndex(mediaId: String, episodeId: String?): Int {
        return episodeId?.let { MacCmsEpisodeRef.parse(it)?.sourceIndex } ?: 0
    }

    suspend fun getAdjacentEpisode(
        mediaId: String,
        currentEpisodeId: String?,
        direction: Int
    ): MacCmsPlayerEpisode? {
        val episodes = loadEpisodes(mediaId, currentEpisodeId)
        if (episodes.isEmpty()) return null
        val currentIndex = currentEpisodeId?.let { id ->
            episodes.indexOfFirst { it.id == id }
        } ?: -1
        val targetIndex = when {
            currentIndex < 0 -> if (direction > 0) 0 else episodes.lastIndex
            else -> currentIndex + direction
        }
        return episodes.getOrNull(targetIndex)
    }
}
