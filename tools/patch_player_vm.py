import re
from pathlib import Path

p = Path(r"D:/xiangmu/lemon影视TV/LomenTV0/app/src/main/java/com/lomen/tv/ui/player/PlayerViewModel.kt")
text = p.read_text(encoding="utf-8")

start = text.find("    /**\n     * 解析当前播放上下文的 WebDAV")
end = text.find("    fun setPlaybackSpeed(speed: Float)")
if start == -1 or end == -1:
    raise SystemExit(f"markers not found start={start} end={end}")

replacement = r"""    private suspend fun navigateToAdjacentEpisode(direction: Int) {
        val mediaId = currentMediaId ?: return
        if (!MacCmsIds.isMacCmsId(mediaId)) return
        val episode = macCmsPlayerHelper.getAdjacentEpisode(mediaId, currentEpisodeId, direction)
            ?: return
        val start = watchHistoryService.getLastWatchPosition(mediaId, episode.id)?.progress ?: 0L
        val episodeTitle = if (episode.title.isNotBlank()) {
            "第${episode.episodeNumber}集 ${episode.title}"
        } else {
            "第${episode.episodeNumber}集"
        }
        onNavigateToEpisode?.invoke(
            episode.path,
            currentTitle ?: "",
            episodeTitle,
            mediaId,
            episode.id,
            start
        )
    }

    private suspend fun getNextEpisode(mediaId: String, currentEpisodeId: String?): EpisodeInfo? {
        if (!MacCmsIds.isMacCmsId(mediaId)) return null
        val episode = macCmsPlayerHelper.getAdjacentEpisode(mediaId, currentEpisodeId, 1) ?: return null
        return EpisodeInfo(mediaId, episode.id, episode.seasonNumber, episode.episodeNumber)
    }

    private suspend fun getPreviousEpisode(mediaId: String, currentEpisodeId: String?): EpisodeInfo? {
        if (!MacCmsIds.isMacCmsId(mediaId)) return null
        val episode = macCmsPlayerHelper.getAdjacentEpisode(mediaId, currentEpisodeId, -1) ?: return null
        return EpisodeInfo(mediaId, episode.id, episode.seasonNumber, episode.episodeNumber)
    }

    private data class EpisodeInfo(
        val mediaId: String,
        val episodeId: String?,
        val seasonNumber: Int,
        val episodeNumber: Int
    )

"""

text = text[:start] + replacement + text[end:]

text = re.sub(
    r"    suspend fun getEpisodeList\(\): List<EpisodeListItem> \{.*?\n    \}\n    \n    /\*\*\n     \* 获取当前集数的所有清晰度选项",
    """    suspend fun getEpisodeList(): List<EpisodeListItem> {
        val mediaId = currentMediaId ?: return emptyList()
        if (!MacCmsIds.isMacCmsId(mediaId)) return emptyList()
        return try {
            macCmsPlayerHelper.loadEpisodes(mediaId, currentEpisodeId).map { episode ->
                EpisodeListItem(
                    id = episode.id,
                    episodeNumber = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    title = episode.title,
                    stillUrl = null,
                    path = episode.path
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error getting episode list", e)
            emptyList()
        }
    }

    /**
     * 获取当前集数的所有清晰度选项""",
    text,
    count=1,
    flags=re.DOTALL,
)

text = re.sub(
    r"    suspend fun getQualityOptions\(\): List<QualityOption> \{.*?\n    \}\n    \n    suspend fun switchQuality",
    """    suspend fun getQualityOptions(): List<QualityOption> = emptyList()

    suspend fun switchQuality""",
    text,
    count=1,
    flags=re.DOTALL,
)

text = re.sub(
    r"    fun loadSkipConfigForSeries\(mediaId: String, seasonNumber: Int = 0\) \{.*?\n    \}\n    \n    /\*\*\n     \* 跳过片头",
    """    fun loadSkipConfigForSeries(mediaId: String, seasonNumber: Int = 0) {
        currentSeriesMediaId = mediaId
        loadSkipConfig(mediaId, seasonNumber)
    }

    /**
     * 跳过片头""",
    text,
    count=1,
    flags=re.DOTALL,
)

idx = text.find("\n/** 与详情页一致：WebDAV")
if idx != -1:
    text = text[:idx] + "\n"

p.write_text(text, encoding="utf-8")
print("done", len(text))
