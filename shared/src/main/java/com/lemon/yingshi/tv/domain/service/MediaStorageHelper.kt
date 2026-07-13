package com.lemon.yingshi.tv.domain.service

import android.content.Context
import com.lemon.yingshi.tv.data.cache.HomeFeedDiskCache
import com.lemon.yingshi.tv.util.DiskCacheTrimHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerDataSourceFactory: PlayerDataSourceFactory,
    private val coverStore: WatchHistoryCoverStore,
    private val homeFeedDiskCache: HomeFeedDiskCache,
    private val deviceStorageProfile: DeviceStorageProfile
) {

    fun getPlaybackCacheSizeBytes(): Long = playerDataSourceFactory.getCacheDirectorySizeBytes()

    fun getCoverCacheSizeBytes(): Long =
        coverStore.getCacheSizeBytes() + directorySize(coilImageCacheDir())

    fun getCoverCacheMaxBytes(): Long = deviceStorageProfile.coverCacheMaxBytes()

    fun getHomeFeedCacheMaxBytes(): Long = homeFeedDiskCache.maxCacheBytes()

    fun getHomeFeedCacheSizeBytes(): Long = homeFeedDiskCache.getCacheSizeBytes()

    fun getTotalCacheSizeBytes(): Long =
        getPlaybackCacheSizeBytes() + getCoverCacheSizeBytes() + getHomeFeedCacheSizeBytes()

    fun clearPlaybackCache() {
        playerDataSourceFactory.clearMediaCache()
    }

    /**
     * 冷启动维护：清理过期播放缓存，并将首页/封面缓存修剪到上限以内。
     */
    fun onApplicationColdStart() {
        clearStalePlaybackCacheOnColdStart()
        trimPersistentCachesToLimits()
    }

    /**
     * 冷启动时清理上次未正常退出留下的播放预缓存（突然关机、崩溃、系统杀进程等）。
     * 播放缓存仅在当前进程的单次播放会话内有意义，进程重启后一律视为过期。
     */
    fun clearStalePlaybackCacheOnColdStart() {
        clearPlaybackCache()
    }

    private fun trimPersistentCachesToLimits() {
        DiskCacheTrimHelper.trimToMaxBytes(
            dir = File(context.cacheDir, "home_feed"),
            maxBytes = homeFeedDiskCache.maxCacheBytes()
        )
        DiskCacheTrimHelper.trimToMaxBytes(
            dir = watchHistoryCoverDir(),
            maxBytes = coverStore.maxCacheBytes()
        )
    }

    fun clearCoverCache() {
        coverStore.clearAll()
        clearCoilCaches()
        runCatching { coilImageCacheDir().deleteRecursively() }
    }

    fun clearHomeFeedCache() {
        homeFeedDiskCache.clearAll()
    }

    fun clearAllCaches() {
        clearPlaybackCache()
        clearCoverCache()
        clearHomeFeedCache()
    }

    private fun clearCoilCaches() {
        runCatching {
            val loaderClass = Class.forName("coil.Coil")
            val imageLoaderMethod = loaderClass.getMethod("imageLoader", Context::class.java)
            val loader = imageLoaderMethod.invoke(null, context) ?: return
            loader.javaClass.getMethod("getDiskCache").invoke(loader)?.let { diskCache ->
                diskCache.javaClass.getMethod("clear").invoke(diskCache)
            }
            loader.javaClass.getMethod("getMemoryCache").invoke(loader)?.let { memoryCache ->
                memoryCache.javaClass.getMethod("clear").invoke(memoryCache)
            }
        }
    }

    private fun watchHistoryCoverDir(): File = File(context.filesDir, "watch_history_covers")

    private fun coilImageCacheDir(): File =
        File(context.cacheDir, DeviceStorageProfile.COIL_IMAGE_CACHE_DIR)

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
