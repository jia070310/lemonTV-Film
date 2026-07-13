package com.lemon.yingshi.tv.domain.service

import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class MediaUrlResolver @Inject constructor(
    @Named("playback") private val okHttpClient: OkHttpClient,
    private val macCmsRepository: MacCmsRepository
) {
    private val thirdPartyResolver = ThirdPartyPlayUrlResolver(okHttpClient)

    suspend fun resolvePlaybackUrl(videoPath: String): Result<MediaPlaybackInfo> =
        withContext(Dispatchers.IO) {
            val parseEndpoints = macCmsRepository.getPlayerParseEndpoints()
            thirdPartyResolver.resolve(videoPath, parseEndpoints)
        }
}

data class MediaPlaybackInfo(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleInfo> = emptyList()
)
