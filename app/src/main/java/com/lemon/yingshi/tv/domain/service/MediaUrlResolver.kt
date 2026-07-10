package com.lemon.yingshi.tv.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaUrlResolver @Inject constructor() {

    suspend fun resolvePlaybackUrl(videoPath: String): Result<MediaPlaybackInfo> =
        withContext(Dispatchers.IO) {
            val url = videoPath.trim()
            if (url.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("播放地址为空"))
            }
            Result.success(
                MediaPlaybackInfo(
                    url = url,
                    headers = emptyMap(),
                    subtitles = emptyList()
                )
            )
        }
}

data class MediaPlaybackInfo(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleInfo> = emptyList()
)
