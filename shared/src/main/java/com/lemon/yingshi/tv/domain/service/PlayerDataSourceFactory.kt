package com.lemon.yingshi.tv.domain.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 播放专用数据源：OkHttp 连接池 + 磁盘缓存。
 * 配合 [HlsPrefetchService] 多线程预写分片，播放时优先读本地缓存。
 */
@Singleton
class PlayerDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("playback") private val okHttpClient: OkHttpClient,
    private val deviceStorageProfile: DeviceStorageProfile
) {
    private val databaseProvider = StandaloneDatabaseProvider(context)

    private val simpleCache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, "exoplayer_cache").apply { mkdirs() }
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(deviceStorageProfile.playbackCacheMaxBytes()),
            databaseProvider
        )
    }

    private val defaultUpstreamFactory: OkHttpDataSource.Factory by lazy {
        createOkHttpFactory(emptyMap())
    }

    private val defaultDataSourceFactory: DefaultDataSource.Factory by lazy {
        DefaultDataSource.Factory(context, defaultUpstreamFactory)
    }

    /** 播放：边下边播，不阻塞等待整段缓存完成 */
    private val playbackCacheFlags =
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR

    /** 预缓存：写入磁盘，允许阻塞直到该分片完整落盘 */
    private val prefetchCacheFlags =
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_BLOCK_ON_CACHE

    @OptIn(UnstableApi::class)
    fun getCache(): SimpleCache = simpleCache

    @OptIn(UnstableApi::class)
    fun createDataSourceFactory(
        headers: Map<String, String> = emptyMap(),
        url: String? = null
    ): DataSource.Factory {
        val normalizedUrl = url?.let(::normalizeLocalPlaybackUrl)
        val upstream = createUpstreamFactory(headers, normalizedUrl)
        if (isLocalPlaybackUrl(normalizedUrl)) {
            return upstream
        }
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(playbackCacheFlags)
    }

    @OptIn(UnstableApi::class)
    fun createCacheWriterDataSourceFactory(headers: Map<String, String> = emptyMap()): DataSource.Factory {
        val upstream = if (headers.isEmpty()) defaultUpstreamFactory else createOkHttpFactory(headers)
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(context, upstream)
            )
            .setFlags(prefetchCacheFlags)
    }

    @OptIn(UnstableApi::class)
    fun createMediaSourceFactory(
        headers: Map<String, String> = emptyMap(),
        url: String? = null
    ): DefaultMediaSourceFactory {
        val normalizedUrl = url?.let(::normalizeLocalPlaybackUrl)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(createDataSourceFactory(headers, normalizedUrl))
    }

    private fun createUpstreamFactory(
        headers: Map<String, String>,
        url: String?
    ): DefaultDataSource.Factory {
        if (isLocalPlaybackUrl(url)) {
            return DefaultDataSource.Factory(context)
        }
        return if (headers.isEmpty()) {
            defaultDataSourceFactory
        } else {
            DefaultDataSource.Factory(context, createOkHttpFactory(headers))
        }
    }

    private fun createOkHttpFactory(headers: Map<String, String>): OkHttpDataSource.Factory {
        val factory = OkHttpDataSource.Factory(okHttpClient)
        if (headers.isNotEmpty()) {
            factory.setDefaultRequestProperties(headers)
        }
        return factory
    }

    @OptIn(UnstableApi::class)
    fun getCacheDirectorySizeBytes(): Long {
        val dir = File(context.cacheDir, "exoplayer_cache")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    @OptIn(UnstableApi::class)
    fun clearMediaCache() {
        runCatching {
            val cache = simpleCache
            cache.keys.toList().forEach { key ->
                cache.removeResource(key)
            }
        }
    }

}

internal fun isLocalPlaybackUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.trim().lowercase()
    return lower.startsWith("file:") || lower.startsWith("content:")
}

internal fun normalizeLocalPlaybackUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("file:/", ignoreCase = true) &&
        !trimmed.startsWith("file://", ignoreCase = true)
    ) {
        return "file://" + trimmed.substringAfter("file:")
    }
    return trimmed
}
