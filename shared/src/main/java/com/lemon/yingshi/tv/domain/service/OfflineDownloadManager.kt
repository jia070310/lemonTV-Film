package com.lemon.yingshi.tv.domain.service

import android.net.Uri
import android.content.Context
import android.util.Log
import com.lemon.yingshi.tv.data.local.database.dao.OfflineDownloadDao
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadEntity
import com.lemon.yingshi.tv.data.local.database.entity.OfflineDownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("offlineDownload") private val okHttpClient: OkHttpClient,
    private val offlineDownloadDao: OfflineDownloadDao,
    private val mediaUrlResolver: MediaUrlResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val scheduleMutex = Mutex()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()

    fun enqueue(@Suppress("UNUSED_PARAMETER") entity: OfflineDownloadEntity) {
        scheduleDownloads()
    }

    /** 应用启动时恢复因进程退出而中断的下载任务 */
    fun resumeInterruptedDownloads() {
        scope.launch {
            offlineDownloadDao.resetStaleDownloading(
                pendingStatus = OfflineDownloadStatus.PENDING,
                downloadingStatus = OfflineDownloadStatus.DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
            repairLegacyOfflinePlaylists()
            scheduleDownloads()
        }
    }

    private suspend fun repairLegacyOfflinePlaylists() {
        val completed = offlineDownloadDao.getAllByStatus(OfflineDownloadStatus.COMPLETED)
        completed.filter { isHlsUrl(it.videoUrl) }.forEach { entity ->
            runCatching { repairOfflinePlaylistIfNeeded(entity) }
                .onFailure { error ->
                    Log.w(TAG, "Repair offline playlist failed for ${entity.id}: ${error.message}")
                }
        }
    }

    private suspend fun repairOfflinePlaylistIfNeeded(entity: OfflineDownloadEntity) {
        val dir = resolveDownloadDir(entity.id)
        if (!dir.exists()) return
        val indexFile = File(dir, "index.m3u8")
        val keyFile = File(dir, HlsPlaylistParser.LOCAL_KEY_FILE)
        val playlistText = if (indexFile.exists()) indexFile.readText() else ""
        if (playlistText.contains("#EXT-X-KEY") && keyFile.exists() && keyFile.length() > 0L) {
            return
        }

        val parsed = HlsPlaylistParser.resolvePlaylistDetails(
            entity.videoUrl,
            buildDownloadHeaders(entity.videoUrl),
            okHttpClient
        ) ?: return

        val existingSegments = dir.listFiles()
            ?.count { it.isFile && it.name.matches(Regex("""seg_\d{5}\.ts""")) }
            ?: 0
        if (existingSegments <= 0) return

        val keyUrl = HlsPlaylistParser.extractEncryptionKeyUrl(
            parsed.mediaPlaylistContent,
            parsed.mediaPlaylistUrl
        )
        if (keyUrl != null && (!keyFile.exists() || keyFile.length() == 0L)) {
            downloadUrlToFileWithRetry(keyUrl, keyFile, buildDownloadHeaders(keyUrl))
        }

        val truncatedPlaylist = buildTruncatedPlaylist(
            parsed.mediaPlaylistContent,
            existingSegments
        )
        val repaired = HlsPlaylistParser.buildLocalOfflinePlaylist(truncatedPlaylist)
        indexFile.writeText(repaired)
        val localUrl = Uri.fromFile(indexFile).toString()
        offlineDownloadDao.updateDownload(
            entity.copy(
                localPlaybackUrl = localUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.i(TAG, "Repaired legacy offline playlist for ${entity.id}")
    }

    private fun buildTruncatedPlaylist(content: String, maxSegments: Int): String {
        val output = mutableListOf<String>()
        var segmentCount = 0
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val isSegment = trimmed.isNotEmpty() && !trimmed.startsWith("#") &&
                (trimmed.contains(".ts", ignoreCase = true) || trimmed.contains(".m4s", ignoreCase = true))
            if (isSegment) {
                if (segmentCount >= maxSegments) return@forEach
                segmentCount++
            }
            output.add(line)
        }
        if (output.none { it.trim().equals("#EXT-X-ENDLIST", ignoreCase = true) }) {
            output.add("#EXT-X-ENDLIST")
        }
        return output.joinToString("\n")
    }

    fun cancel(id: String) {
        pausedIds.remove(id)
        activeJobs.remove(id)?.cancel()
    }

    fun cancelAll() {
        pausedIds.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun pause(id: String) {
        scope.launch {
            pausedIds.add(id)
            activeJobs.remove(id)?.cancel()
            val entity = offlineDownloadDao.getDownloadById(id) ?: return@launch
            if (entity.status == OfflineDownloadStatus.PENDING ||
                entity.status == OfflineDownloadStatus.DOWNLOADING
            ) {
                offlineDownloadDao.updateDownload(
                    entity.copy(
                        status = OfflineDownloadStatus.PAUSED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun resume(id: String) {
        scope.launch {
            pausedIds.remove(id)
            val entity = offlineDownloadDao.getDownloadById(id) ?: return@launch
            if (entity.status == OfflineDownloadStatus.PAUSED) {
                offlineDownloadDao.updateDownload(
                    entity.copy(
                        status = OfflineDownloadStatus.PENDING,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                scheduleDownloads()
            }
        }
    }

    suspend fun pauseAll(serverUrl: String) {
        val active = offlineDownloadDao.getDownloadsSnapshot(serverUrl).filter {
            it.status == OfflineDownloadStatus.PENDING ||
                it.status == OfflineDownloadStatus.DOWNLOADING
        }
        active.forEach { pausedIds.add(it.id) }
        active.forEach { activeJobs.remove(it.id)?.cancel() }
        if (active.isNotEmpty()) {
            offlineDownloadDao.pauseActiveDownloads(
                serverUrl = serverUrl,
                pausedStatus = OfflineDownloadStatus.PAUSED,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun resumeAll(serverUrl: String) {
        offlineDownloadDao.resumePausedDownloads(
            serverUrl = serverUrl,
            pendingStatus = OfflineDownloadStatus.PENDING,
            pausedStatus = OfflineDownloadStatus.PAUSED,
            updatedAt = System.currentTimeMillis()
        )
        pausedIds.clear()
        scheduleDownloads()
    }

    fun deleteFiles(id: String) {
        runCatching {
            resolveDownloadDir(id).deleteRecursively()
            // 兼容旧版含 ':' 的目录名
            File(context.filesDir, "$OFFLINE_DIR/$id").takeIf { it.exists() }?.deleteRecursively()
        }
    }

    fun getOfflineStorageSizeBytes(): Long {
        val root = File(context.filesDir, OFFLINE_DIR)
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getDownloadSizeBytes(id: String): Long {
        val dir = resolveDownloadDir(id)
        if (dir.exists()) {
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        val legacyDir = File(context.filesDir, "$OFFLINE_DIR/$id")
        if (legacyDir.exists()) {
            return legacyDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return 0L
    }

    /** 并行调度多个离线缓存任务（手机端）。 */
    private fun scheduleDownloads() {
        scope.launch {
            val toStart = scheduleMutex.withLock {
                val pending = offlineDownloadDao
                    .getAllByStatus(OfflineDownloadStatus.PENDING)
                    .sortedBy { it.createdAt }
                val batch = mutableListOf<OfflineDownloadEntity>()
                for (item in pending) {
                    if (activeJobs.size + batch.size >= MAX_CONCURRENT_DOWNLOADS) break
                    if (activeJobs.containsKey(item.id)) continue
                    batch.add(item)
                }
                batch
            }
            toStart.forEach { entity ->
                if (activeJobs.containsKey(entity.id)) return@forEach
                val job = scope.launch {
                    try {
                        runDownload(entity)
                    } finally {
                        activeJobs.remove(entity.id)
                        scheduleDownloads()
                    }
                }
                activeJobs[entity.id] = job
            }
        }
    }

    private suspend fun runDownload(entity: OfflineDownloadEntity) {
        val started = entity.copy(
            status = OfflineDownloadStatus.DOWNLOADING,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        offlineDownloadDao.updateDownload(started)

        try {
            val resolved = mediaUrlResolver.resolvePlaybackUrl(entity.videoUrl).getOrElse { throw it }
            val downloadEntity = started.copy(videoUrl = resolved.url)
            val dir = prepareDownloadDir(entity.id)
            val localUrl = if (isHlsUrl(resolved.url)) {
                downloadHls(downloadEntity, dir, resolved.headers)
            } else {
                downloadDirectFile(downloadEntity, dir, resolved.headers)
            }
            offlineDownloadDao.updateDownload(
                started.copy(
                    status = OfflineDownloadStatus.COMPLETED,
                    progress = 100,
                    localPlaybackUrl = localUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (error: CancellationException) {
            if (!pausedIds.contains(entity.id)) {
                offlineDownloadDao.updateDownload(
                    started.copy(
                        status = OfflineDownloadStatus.PENDING,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Offline download failed: ${error.message}", error)
            offlineDownloadDao.updateDownload(
                started.copy(
                    status = OfflineDownloadStatus.FAILED,
                    errorMessage = error.message,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun prepareDownloadDir(id: String): File {
        val dir = File(context.filesDir, "$OFFLINE_DIR/${toStorageDirName(id)}")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("无法创建离线缓存目录: ${dir.absolutePath}")
        }
        return dir
    }

    private fun resolveDownloadDir(id: String): File =
        File(context.filesDir, "$OFFLINE_DIR/${toStorageDirName(id)}")

    /** MacCMS episodeId 含 ':'，不能直接作为 Android 文件目录名。 */
    private fun toStorageDirName(downloadId: String): String =
        downloadId.replace(':', '_').replace('/', '_').replace('\\', '_')

    private suspend fun downloadHls(
        entity: OfflineDownloadEntity,
        dir: File,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val downloadHeaders = buildDownloadHeaders(entity.videoUrl, extraHeaders)
        val parsed = HlsPlaylistParser.resolvePlaylistDetails(
            entity.videoUrl,
            downloadHeaders,
            okHttpClient
        ) ?: throw IllegalStateException("无法解析 HLS 播放列表")

        val segments = parsed.segmentUrls
        if (segments.isEmpty()) throw IllegalStateException("未找到可下载的分片")

        val keyUrl = HlsPlaylistParser.extractEncryptionKeyUrl(
            parsed.mediaPlaylistContent,
            parsed.mediaPlaylistUrl
        )
        val keyFile = File(dir, HlsPlaylistParser.LOCAL_KEY_FILE)
        if (keyUrl != null && (!keyFile.exists() || keyFile.length() == 0L)) {
            downloadUrlToFileWithRetry(keyUrl, keyFile, buildDownloadHeaders(keyUrl, extraHeaders))
        }

        val total = segments.size
        val completed = AtomicInteger(countCompletedSegments(dir, total))
        val threadCount = min(hlsThreadCount(), total).coerceAtLeast(2)
        val semaphore = Semaphore(threadCount)

        if (completed.get() > 0) {
            Log.i(TAG, "HLS resume ${entity.id}: ${completed.get()}/$total segments already cached")
            publishHlsProgress(entity, completed.get(), total, calculateDownloadedBytes(dir, total))
        }

        coroutineScope {
            segments.mapIndexed { index, segmentUrl ->
                async {
                    semaphore.withPermit {
                        val target = File(dir, HlsPlaylistParser.segmentFileName(index))
                        if (!target.exists() || target.length() == 0L) {
                            downloadUrlToFileWithRetry(
                                segmentUrl,
                                target,
                                buildDownloadHeaders(segmentUrl, extraHeaders)
                            )
                        }
                        val done = completed.incrementAndGet()
                        if (done % PROGRESS_UPDATE_INTERVAL == 0 || done == total) {
                            publishHlsProgress(
                                entity,
                                done,
                                total,
                                calculateDownloadedBytes(dir, total)
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        publishHlsProgress(entity, total, total, calculateDownloadedBytes(dir, total))

        val playlist = HlsPlaylistParser.buildLocalOfflinePlaylist(parsed.mediaPlaylistContent)
        val indexFile = File(dir, "index.m3u8")
        indexFile.writeText(playlist)
        return Uri.fromFile(indexFile).toString()
    }

    private suspend fun downloadDirectFile(
        entity: OfflineDownloadEntity,
        dir: File,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val extension = entity.videoUrl.substringAfterLast('.', "mp4")
            .substringBefore('?')
            .takeIf { it.length in 2..5 } ?: "mp4"
        val target = File(dir, "video.$extension")
        if (target.exists() && target.length() > 0) {
            Log.i(TAG, "Direct file resume ${entity.id}: ${target.length()} bytes already cached")
        }
        downloadUrlToFileWithRetry(
            entity.videoUrl,
            target,
            buildDownloadHeaders(entity.videoUrl, extraHeaders)
        ) { downloaded, total ->
            val progress = if (total > 0) {
                ((downloaded * 100) / total).toInt().coerceIn(0, 99)
            } else {
                0
            }
            scope.launch {
                offlineDownloadDao.updateDownload(
                    entity.copy(
                        progress = progress,
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return Uri.fromFile(target).toString()
    }

    private suspend fun publishHlsProgress(
        entity: OfflineDownloadEntity,
        completed: Int,
        total: Int,
        bytesDownloaded: Long
    ) {
        offlineDownloadDao.updateDownload(
            entity.copy(
                progress = ((completed * 100) / max(total, 1)).coerceIn(0, 99),
                bytesDownloaded = bytesDownloaded,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun downloadUrlToFileWithRetry(
        url: String,
        target: File,
        headers: Map<String, String> = buildDownloadHeaders(url),
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ) {
        var lastError: Exception? = null
        repeat(SEGMENT_MAX_RETRIES) { attempt ->
            try {
                downloadUrlToFile(url, target, headers, onProgress)
                return
            } catch (error: Exception) {
                lastError = error
                if (attempt < SEGMENT_MAX_RETRIES - 1) {
                    Log.w(TAG, "Download retry ${attempt + 1}/$SEGMENT_MAX_RETRIES for $url: ${error.message}")
                    delay(500L * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("下载失败: $url")
    }

    private suspend fun downloadUrlToFile(
        url: String,
        target: File,
        headers: Map<String, String> = buildDownloadHeaders(url),
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ) {
        val existingBytes = if (target.exists()) target.length() else 0L

        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            when {
                existingBytes > 0 && response.code == 416 -> {
                    onProgress?.invoke(existingBytes, existingBytes)
                    return
                }
                existingBytes > 0 && response.code == 200 -> {
                    target.delete()
                    downloadUrlToFile(url, target, headers, onProgress)
                    return
                }
                !response.isSuccessful -> {
                    throw IllegalStateException("下载失败: HTTP ${response.code}")
                }
            }

            val body = response.body ?: throw IllegalStateException("下载内容为空")
            val append = existingBytes > 0 && response.code == 206
            val total = resolveTotalBytes(response.header("Content-Range"), existingBytes, body.contentLength())
            var downloaded = existingBytes
            var lastReported = existingBytes

            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (onProgress != null &&
                            (downloaded - lastReported >= PROGRESS_BYTES_STEP || downloaded >= total)
                        ) {
                            onProgress.invoke(downloaded, total)
                            lastReported = downloaded
                        }
                    }
                }
            }
            onProgress?.invoke(downloaded, total)
        }
    }

    private fun resolveTotalBytes(
        contentRange: String?,
        existingBytes: Long,
        contentLength: Long
    ): Long {
        val rangeTotal = contentRange
            ?.substringAfterLast('/')
            ?.toLongOrNull()
        return when {
            rangeTotal != null && rangeTotal > 0 -> rangeTotal
            contentLength > 0 -> existingBytes + contentLength
            existingBytes > 0 -> existingBytes
            else -> 0L
        }
    }

    private fun countCompletedSegments(dir: File, total: Int): Int {
        return (0 until total).count { index ->
            val file = File(dir, HlsPlaylistParser.segmentFileName(index))
            file.exists() && file.length() > 0L
        }
    }

    private fun calculateDownloadedBytes(dir: File, total: Int): Long {
        return (0 until total).sumOf { index ->
            val file = File(dir, HlsPlaylistParser.segmentFileName(index))
            if (file.exists()) file.length() else 0L
        }
    }

    private fun hlsThreadCount(): Int {
        val activeCount = activeJobs.size.coerceAtLeast(1)
        return max(2, HLS_THREADS / activeCount)
    }

    private fun isHlsUrl(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) ||
            url.contains("format=m3u8", ignoreCase = true)

    private fun buildDownloadHeaders(
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return DEFAULT_DOWNLOAD_HEADERS + extraHeaders
        val host = parsed.host.orEmpty()
        val referer = when {
            host.contains("gsuus", ignoreCase = true) ||
                host.contains("gszyi", ignoreCase = true) -> "https://v.gsuus.com/"
            else -> "${parsed.protocol}://${parsed.host}/"
        }
        return DEFAULT_DOWNLOAD_HEADERS + ("Referer" to referer) + extraHeaders
    }

    private companion object {
        const val TAG = "OfflineDownloadManager"
        const val OFFLINE_DIR = "offline_downloads"
        const val MAX_CONCURRENT_DOWNLOADS = 3
        const val HLS_THREADS = 12
        const val SEGMENT_MAX_RETRIES = 3
        const val DOWNLOAD_BUFFER_SIZE = 256 * 1024
        const val PROGRESS_UPDATE_INTERVAL = 5
        const val PROGRESS_BYTES_STEP = 256 * 1024L

        val DEFAULT_DOWNLOAD_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )
    }
}
