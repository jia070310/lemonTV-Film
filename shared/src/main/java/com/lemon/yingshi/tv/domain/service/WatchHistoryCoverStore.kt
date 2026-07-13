package com.lemon.yingshi.tv.domain.service

import android.content.Context
import android.graphics.Bitmap
import com.lemon.yingshi.tv.util.DiskCacheTrimHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class WatchHistoryCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceStorageProfile: DeviceStorageProfile
) {
    private val coverDir: File
        get() = File(context.filesDir, "watch_history_covers").also { it.mkdirs() }

    fun saveCover(mediaId: String, episodeId: String?, source: Bitmap): String? {
        if (source.width <= 0 || source.height <= 0) return null

        val compressed = compressBitmap(source)
        val file = File(coverDir, buildFileName(mediaId, episodeId))
        return try {
            val maxBytes = deviceStorageProfile.watchHistoryCoverMaxBytes()
            DiskCacheTrimHelper.trimToMaxBytes(
                dir = coverDir,
                maxBytes = maxBytes,
                reservedBytes = estimateJpegBytes(compressed),
                exclude = file.takeIf { it.exists() }
            )
            FileOutputStream(file).use { output ->
                compressed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            file.setLastModified(System.currentTimeMillis())
            DiskCacheTrimHelper.trimToMaxBytes(coverDir, maxBytes)
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save watch history cover", e)
            file.delete()
            null
        } finally {
            if (compressed !== source && !compressed.isRecycled) {
                compressed.recycle()
            }
        }
    }

    fun deleteCover(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun clearAll() {
        coverDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun getCacheSizeBytes(): Long = DiskCacheTrimHelper.directorySizeBytes(coverDir)

    fun maxCacheBytes(): Long = deviceStorageProfile.watchHistoryCoverMaxBytes()

    private fun estimateJpegBytes(bitmap: Bitmap): Long {
        val pixels = bitmap.width.toLong() * bitmap.height
        return (pixels / 4).coerceAtLeast(8L * 1024)
    }

    private fun compressBitmap(source: Bitmap): Bitmap {
        val scale = min(
            TARGET_WIDTH.toFloat() / source.width,
            TARGET_HEIGHT.toFloat() / source.height
        ).coerceAtMost(1f)
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        if (targetWidth == source.width && targetHeight == source.height) {
            return source
        }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun buildFileName(mediaId: String, episodeId: String?): String {
        val safeMediaId = mediaId.replace(INVALID_FILE_CHARS, "_").take(64)
        val safeEpisodeId = episodeId?.replace(INVALID_FILE_CHARS, "_")?.take(32) ?: "movie"
        return "${safeMediaId}_${safeEpisodeId}.jpg"
    }

    companion object {
        private const val TAG = "WatchHistoryCoverStore"
        private const val TARGET_WIDTH = 320
        private const val TARGET_HEIGHT = 180
        private const val JPEG_QUALITY = 72
        private val INVALID_FILE_CHARS = Regex("[^a-zA-Z0-9._-]")
    }
}
