package com.lemon.yingshi.tv.domain.service

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageTier {
    COMPACT,
    STANDARD,
    SPACIOUS
}

@Singleton
class DeviceStorageProfile @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun tier(): StorageTier {
        val freeBytes = runCatching {
            StatFs(context.cacheDir.absolutePath).availableBytes
        }.getOrDefault(0L)
        return when {
            freeBytes < 512L * 1024 * 1024 -> StorageTier.COMPACT
            freeBytes < 2L * 1024 * 1024 * 1024 -> StorageTier.STANDARD
            else -> StorageTier.SPACIOUS
        }
    }

    fun playbackCacheMaxBytes(): Long = when (tier()) {
        StorageTier.COMPACT -> 64L * 1024 * 1024
        StorageTier.STANDARD -> 128L * 1024 * 1024
        StorageTier.SPACIOUS -> 192L * 1024 * 1024
    }

    fun prefetchWindowSeconds(): Int = when (tier()) {
        StorageTier.SPACIOUS -> 120
        StorageTier.COMPACT, StorageTier.STANDARD -> 90
    }

    fun prefetchThreadCount(): Int = when (tier()) {
        StorageTier.COMPACT -> 2
        StorageTier.STANDARD, StorageTier.SPACIOUS -> 3
    }

    fun homeFeedCacheMaxBytes(): Long = when (tier()) {
        StorageTier.COMPACT -> 8L * 1024 * 1024
        StorageTier.STANDARD -> 16L * 1024 * 1024
        StorageTier.SPACIOUS -> 24L * 1024 * 1024
    }

    /** 观看历史封面目录上限（Coil 海报缓存另计，见 [COIL_DISK_MAX_BYTES]） */
    fun watchHistoryCoverMaxBytes(): Long = when (tier()) {
        StorageTier.COMPACT -> 12L * 1024 * 1024
        StorageTier.STANDARD -> 24L * 1024 * 1024
        StorageTier.SPACIOUS -> 32L * 1024 * 1024
    }

    fun coverCacheMaxBytes(): Long = COIL_DISK_MAX_BYTES + watchHistoryCoverMaxBytes()

    companion object {
        const val COIL_DISK_MAX_BYTES = 48L * 1024 * 1024
        const val COIL_IMAGE_CACHE_DIR = "image_cache"
    }
}
