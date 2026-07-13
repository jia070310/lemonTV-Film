package com.lemon.yingshi.mobile.util

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.lemon.yingshi.tv.domain.service.PlayerDataSourceFactory
import com.lemon.yingshi.tv.domain.service.WatchHistoryCoverStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerDataSourceFactory: PlayerDataSourceFactory,
    private val coverStore: WatchHistoryCoverStore
) {

    fun getPlaybackCacheSizeBytes(): Long = playerDataSourceFactory.getCacheDirectorySizeBytes()

    fun getCoverCacheSizeBytes(): Long =
        directorySize(watchHistoryCoverDir()) + directorySize(coilImageCacheDir())

    fun getTotalCacheSizeBytes(): Long = getPlaybackCacheSizeBytes() + getCoverCacheSizeBytes()

    fun clearPlaybackCache() {
        playerDataSourceFactory.clearMediaCache()
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCoverCache() {
        coverStore.clearAll()
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
        runCatching { coilImageCacheDir().deleteRecursively() }
    }

    fun clearAllCaches() {
        clearPlaybackCache()
        clearCoverCache()
    }

    private fun watchHistoryCoverDir(): File = File(context.filesDir, "watch_history_covers")

    private fun coilImageCacheDir(): File = File(context.cacheDir, COIL_IMAGE_CACHE_DIR)

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val COIL_IMAGE_CACHE_DIR = "image_cache"
    }
}
