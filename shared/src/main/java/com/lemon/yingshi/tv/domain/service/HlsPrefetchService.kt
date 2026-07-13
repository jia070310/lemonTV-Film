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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.min

data class HlsPrefetchState(
    val active: Boolean = false,
    /** 窗口内已缓存分片数 / 窗口内总分片数 */
    val progress: Float = 0f,
    val completedSegments: Int = 0,
    val totalSegments: Int = 0
)

/**
 * 滚动窗口 HLS 预缓存：仅预取播放头前方 90~120 秒分片，播放中定时补充。
 */
@Singleton
class HlsPrefetchService @Inject constructor(
    private val playerDataSourceFactory: PlayerDataSourceFactory,
    private val deviceStorageProfile: DeviceStorageProfile,
    @Named("playback") private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private val playheadMs = AtomicLong(0L)
    private val replenishMutex = Mutex()
    private val prefetchedKeys = ConcurrentHashMap.newKeySet<String>()

    private var segments: List<HlsSegment> = emptyList()
    private var requestHeaders: Map<String, String> = emptyMap()
    private var lastReplenishMs = 0L
    private var lastReplenishPlayheadMs = 0L

    private val _state = MutableStateFlow(HlsPrefetchState())
    val state: StateFlow<HlsPrefetchState> = _state.asStateFlow()

    fun start(
        m3u8Url: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L
    ) {
        if (!isHlsUrl(m3u8Url) || isLocalPlaybackUrl(m3u8Url)) return
        stop()
        playheadMs.set(startPositionMs.coerceAtLeast(0L))
        requestHeaders = headers
        prefetchJob = scope.launch {
            runRollingPrefetch(m3u8Url, headers)
        }
    }

    fun updatePlayhead(positionMs: Long) {
        val normalized = positionMs.coerceAtLeast(0L)
        val previous = playheadMs.getAndSet(normalized)
        if (normalized - previous >= PLAYHEAD_ADVANCE_TRIGGER_MS) {
            scope.launch { replenishWindow(force = false) }
        }
    }

    fun stop() {
        prefetchJob?.cancel()
        prefetchJob = null
        segments = emptyList()
        requestHeaders = emptyMap()
        prefetchedKeys.clear()
        lastReplenishMs = 0L
        lastReplenishPlayheadMs = 0L
        _state.value = HlsPrefetchState()
    }

    private suspend fun runRollingPrefetch(m3u8Url: String, headers: Map<String, String>) {
        _state.value = HlsPrefetchState(active = true)
        try {
            val parsed = HlsPlaylistParser.resolveSegmentUrls(m3u8Url, headers, okHttpClient)
            if (parsed == null || parsed.segments.isEmpty()) {
                Log.w(TAG, "HLS prefetch: no segments parsed from $m3u8Url")
                _state.value = HlsPrefetchState()
                return
            }

            segments = parsed.segments
            val windowSeconds = deviceStorageProfile.prefetchWindowSeconds()
            val threadCount = deviceStorageProfile.prefetchThreadCount()
            Log.i(
                TAG,
                "HLS rolling prefetch start: ${segments.size} segments, window=${windowSeconds}s, threads=$threadCount"
            )

            replenishWindow(force = true)

            while (coroutineContext.isActive) {
                delay(REPLENISH_INTERVAL_MS)
                replenishWindow(force = false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "HLS prefetch error: ${error.message}", error)
            _state.value = HlsPrefetchState()
        }
    }

    private suspend fun replenishWindow(force: Boolean) {
        if (segments.isEmpty()) return
        replenishMutex.withLock {
            val now = System.currentTimeMillis()
            val currentPlayhead = playheadMs.get()
            val shouldReplenish = force ||
                now - lastReplenishMs >= REPLENISH_INTERVAL_MS ||
                currentPlayhead - lastReplenishPlayheadMs >= PLAYHEAD_ADVANCE_TRIGGER_MS
            if (!shouldReplenish) return

            lastReplenishMs = now
            lastReplenishPlayheadMs = currentPlayhead

            val windowStartSec = currentPlayhead / 1000.0
            val windowEndSec = windowStartSec + deviceStorageProfile.prefetchWindowSeconds()
            val windowSegments = segments.withIndex().filter { (_, segment) ->
                segment.startTimeSec < windowEndSec &&
                    segment.startTimeSec + segment.durationSec > windowStartSec
            }
            if (windowSegments.isEmpty()) {
                _state.value = HlsPrefetchState(active = true, progress = 0f, completedSegments = 0, totalSegments = 0)
                return
            }

            val threadCount = min(deviceStorageProfile.prefetchThreadCount(), windowSegments.size).coerceAtLeast(1)
            val semaphore = Semaphore(threadCount)
            val cache = playerDataSourceFactory.getCache()
            val dataSourceFactory = playerDataSourceFactory.createCacheWriterDataSourceFactory(requestHeaders)
            var completedInWindow = 0

            coroutineScope {
                windowSegments.map { (index, segment) ->
                    async {
                        semaphore.withPermit {
                            if (!coroutineContext.isActive) return@withPermit
                            val dataSpec = DataSpec.Builder().setUri(segment.url).build()
                            val cacheKey = dataSpec.key ?: segment.url
                            val cachedLength = cache.getCachedLength(cacheKey, 0, C.LENGTH_UNSET.toLong())
                            if (cachedLength > 0 || prefetchedKeys.contains(cacheKey)) {
                                completedInWindow++
                                updateWindowProgress(completedInWindow, windowSegments.size)
                                return@withPermit
                            }
                            runCatching {
                                prefetchSegment(dataSourceFactory, dataSpec)
                                prefetchedKeys.add(cacheKey)
                            }.onFailure { error ->
                                if (error !is CancellationException) {
                                    Log.w(TAG, "Prefetch segment failed [$index]: ${error.message}")
                                }
                            }
                            completedInWindow++
                            updateWindowProgress(completedInWindow, windowSegments.size)
                        }
                    }
                }.awaitAll()
            }
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

    private fun updateWindowProgress(completed: Int, total: Int) {
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
        const val CACHE_BUFFER_SIZE = 128 * 1024
        const val REPLENISH_INTERVAL_MS = 5_000L
        const val PLAYHEAD_ADVANCE_TRIGGER_MS = 10_000L
    }
}
