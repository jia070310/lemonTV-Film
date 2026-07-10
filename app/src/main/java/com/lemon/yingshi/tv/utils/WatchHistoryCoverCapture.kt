package com.lemon.yingshi.tv.utils

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.MainThread
import androidx.media3.ui.PlayerView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object WatchHistoryCoverCapture {

    @MainThread
    fun captureFrame(playerView: PlayerView): Bitmap? {
        val surfaceView = playerView.videoSurfaceView ?: return null
        return when (surfaceView) {
            is TextureView -> captureTextureView(surfaceView)
            is SurfaceView -> captureSurfaceView(surfaceView)
            else -> null
        }
    }

    private fun captureTextureView(textureView: TextureView): Bitmap? {
        if (!textureView.isAvailable) return null
        val width = textureView.width.coerceAtLeast(1)
        val height = textureView.height.coerceAtLeast(1)
        val direct = textureView.bitmap?.takeIf { !it.isRecycled && !isLikelyBlank(it) }
        if (direct != null) return direct
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return runCatching {
                textureView.getBitmap(width, height)?.takeIf { !it.isRecycled && !isLikelyBlank(it) }
            }.getOrNull()
        }
        return null
    }

    /** 视频画面截帧失败时，从视频地址按时间点取帧（备用方案） */
    fun captureFrameFromVideo(videoUrl: String, positionMs: Long): Bitmap? {
        if (videoUrl.isBlank()) return null
        if (videoUrl.contains(".m3u8", ignoreCase = true)) return null
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoUrl, emptyMap())
            val timeUs = positionMs.coerceAtLeast(0L) * 1_000L
            retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.takeIf { !it.isRecycled && !isLikelyBlank(it) }
        } catch (e: Exception) {
            android.util.Log.w("WatchHistoryCoverCapture", "MediaMetadataRetriever failed: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun captureSurfaceView(surfaceView: SurfaceView): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        if (surfaceView.width <= 0 || surfaceView.height <= 0) return null

        val bitmap = Bitmap.createBitmap(
            surfaceView.width,
            surfaceView.height,
            Bitmap.Config.RGB_565
        )
        val latch = CountDownLatch(1)
        var success = false
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                success = result == PixelCopy.SUCCESS
                latch.countDown()
            },
            Handler(Looper.getMainLooper())
        )
        latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return if (success) bitmap else {
            bitmap.recycle()
            null
        }
    }

    /** 判断截帧是否接近全黑（无效封面） */
    fun isLikelyBlank(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return true
        val sampleSize = 8
        val stepX = (bitmap.width / sampleSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleSize).coerceAtLeast(1)
        var darkPixels = 0
        var total = 0
        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 16 && g < 16 && b < 16) darkPixels++
                total++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && darkPixels.toFloat() / total > 0.92f
    }

    private const val CAPTURE_TIMEOUT_MS = 800L
}
