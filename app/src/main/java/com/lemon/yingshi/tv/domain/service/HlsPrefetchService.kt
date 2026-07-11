package com.lemon.yingshi.tv.domain.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min

data class HlsPrefetchState(
    val active: Boolean = false,
    /** 已缓存分片数 / 总分片数 */
    val progress: Float = 0f,
    val completedSegments: Int = 0,
    val totalSegments: Int = 0
)

/**
 * 多线程 HLS 分片预缓存：参考 M3U8 下载工具的 ThreadPool 模型，
 * 并行将 TS 分片写入 ExoPlayer SimpleCache，播放时直接命中本地缓存。
 */
@Singleton
class HlsPrefetchService @Inject constructor(
    private val playerDataSourceFactory: PlayerDataSourceFactory,
    @Named("playback") private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    private val _state = MutableStateFlow(HlsPrefetchState())
    val state: StateFlow<HlsPrefetchState> = _state.asStateFlow()

    fun start(m3u8Url: String, headers: Map<String, String> = emptyMap()) {
        if (!isHlsUrl(m3u8Url)) return
        stop()
        prefetchJob = scope.launch {
            runPrefetch(m3u8Url, headers)
        }
    }

    fun stop() {
        prefetchJob?.cancel()
        prefetchJob = null
        _state.value = HlsPrefetchState()
    }

    @OptIn(UnstableApi::class)
    private suspend fun runPrefetch(m3u8Url: String, headers: Map<String, String>) {
        _state.value = HlsPrefetchState(active = true)
        try {
            val parsed = HlsPlaylistParser.resolveSegmentUrls(m3u8Url, headers, okHttpClient)
            if (parsed == null || parsed.segmentUrls.isEmpty()) {
                Log.w(TAG, "HLS prefetch: no segments parsed from $m3u8Url")
                _state.value = HlsPrefetchState()
                return
            }

            val segments = parsed.segmentUrls
            val total = segments.size
            val threadCount = min(DEFAULT_THREADS, total).coerceAtLeast(2)
            val semaphore = Semaphore(threadCount)
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val dataSourceFactory = playerDataSourceFactory.createCacheWriterDataSourceFactory(headers)
            val cache = playerDataSourceFactory.getCache()

            Log.i(TAG, "HLS prefetch start: $total segments, threads=$threadCount")

            _state.value = HlsPrefetchState(
                active = true,
                progress = 0f,
                completedSegments = 0,
                totalSegments = total
            )

            coroutineScope {
                segments.mapIndexed { index, segmentUrl ->
                    async {
                        semaphore.withPermit {
                            if (!coroutineContext.isActive) return@withPermit
                            val dataSpec = DataSpec.Builder().setUri(segmentUrl).build()
                            val cacheKey = dataSpec.key ?: segmentUrl
                            val cachedLength = cache.getCachedLength(
                                cacheKey,
                                0,
                                C.LENGTH_UNSET.toLong()
                            )
                            if (cachedLength > 0) {
                                val done = completed.incrementAndGet()
                                updateProgress(done, total)
                                return@withPermit
                            }
                            runCatching {
                                prefetchSegment(dataSourceFactory, dataSpec)
                            }.onFailure { error ->
                                if (error !is CancellationException) {
                                    Log.w(TAG, "Prefetch segment failed [$index]: ${error.message}")
                                }
                            }
                            val done = completed.incrementAndGet()
                            updateProgress(done, total)
                        }
                    }
                }.awaitAll()
            }

            Log.i(TAG, "HLS prefetch done: ${completed.get()}/$total")
            _state.value = HlsPrefetchState(
                active = false,
                progress = 1f,
                completedSegments = completed.get(),
                totalSegments = total
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "HLS prefetch error: ${error.message}", error)
            _state.value = HlsPrefetchState()
        }
    }

    @OptIn(UnstableApi::class)
    private fun prefetchSegment(
        dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
        dataSpec: DataSpec
    ) {
        val dataSource = dataSourceFactory.createDataSource()
        check(dataSource is CacheDataSource) { "Prefetch requires CacheDataSource" }
        val buffer = ByteArray(CACHE_BUFFER_SIZE)
        CacheWriter(dataSource, dataSpec, buffer, null).cache()
    }

    private fun updateProgress(completed: Int, total: Int) {
        _state.value = HlsPrefetchState(
            active = true,
            progress = completed.toFloat() / total.coerceAtLeast(1),
            completedSegments = completed,
            totalSegments = total
        )
    }

    private fun isHlsUrl(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) ||
            url.contains("format=m3u8", ignoreCase = true)

    private companion object {
        const val TAG = "HlsPrefetchService"
        const val DEFAULT_THREADS = 6
        const val CACHE_BUFFER_SIZE = 128 * 1024
    }
}
