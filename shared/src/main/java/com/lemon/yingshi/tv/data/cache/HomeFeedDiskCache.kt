package com.lemon.yingshi.tv.data.cache

import android.content.Context
import android.os.StatFs
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeSection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 首页栏目磁盘缓存：下次打开先读缓存秒显，后台刷新后覆盖。
 * 容量随设备剩余空间动态调整，满额按最旧文件淘汰。
 */
@Singleton
class HomeFeedDiskCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir: File
        get() = File(context.cacheDir, "home_feed").also { it.mkdirs() }

    fun read(serverUrl: String): CachedHomeFeed? {
        val file = cacheFile(serverUrl)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CachedHomeFeed>(file.readText())
        }.getOrNull()
    }

    fun write(
        serverUrl: String,
        recommended: List<MacCmsVodItem>,
        sections: List<MacCmsHomeSection>
    ) {
        val payload = CachedHomeFeed(
            serverUrl = serverUrl,
            savedAt = System.currentTimeMillis(),
            recommended = recommended.map { it.toSnapshot() },
            sections = sections
                .filter { it.isLoaded && it.items.isNotEmpty() }
                .map { section ->
                    CachedHomeSection(
                        sectionKey = section.sectionKey,
                        typeName = section.typeName,
                        typeId = section.typeId,
                        navTypeId = section.navTypeId,
                        total = section.total,
                        items = section.items.map { it.toSnapshot() }
                    )
                }
        )
        val bytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        if (bytes.size > maxCacheBytes()) return
        trimCacheIfNeeded(bytes.size.toLong())
        runCatching {
            cacheFile(serverUrl).writeBytes(bytes)
        }
    }

    fun toRecommendedItems(snapshots: List<CachedVodSnapshot>): List<MacCmsVodItem> =
        snapshots.map { it.toVodItem() }

    fun toHomeSection(cached: CachedHomeSection): MacCmsHomeSection =
        MacCmsHomeSection(
            sectionKey = cached.sectionKey,
            typeName = cached.typeName,
            typeId = cached.typeId,
            navTypeId = cached.navTypeId,
            items = cached.items.map { it.toVodItem() },
            total = cached.total,
            isLoaded = true,
            isLoading = false
        )

    private fun cacheFile(serverUrl: String): File {
        val hash = sha256(serverUrl.trim().lowercase())
        return File(cacheDir, "feed_$hash.json")
    }

    private fun maxCacheBytes(): Long {
        val stat = runCatching { StatFs(context.cacheDir.absolutePath) }.getOrNull()
        val freeBytes = stat?.availableBytes ?: (32L * 1024 * 1024)
        val dynamic = (freeBytes * 0.02).toLong()
        return dynamic.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }

    private fun trimCacheIfNeeded(incomingBytes: Long) {
        val maxBytes = maxCacheBytes()
        val files = cacheDir.listFiles()?.filter { it.isFile && it.extension == "json" }.orEmpty()
        var total = files.sumOf { it.length() } + incomingBytes
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            val len = file.length()
            if (file.delete()) total -= len
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MIN_CACHE_BYTES = 5L * 1024 * 1024
        private const val MAX_CACHE_BYTES = 32L * 1024 * 1024
    }
}

@Serializable
data class CachedHomeFeed(
    val serverUrl: String,
    val savedAt: Long,
    val recommended: List<CachedVodSnapshot> = emptyList(),
    val sections: List<CachedHomeSection> = emptyList()
)

@Serializable
data class CachedHomeSection(
    val sectionKey: String,
    val typeName: String,
    val typeId: Int,
    val navTypeId: Int? = null,
    val total: Int = 0,
    val items: List<CachedVodSnapshot> = emptyList()
)

@Serializable
data class CachedVodSnapshot(
    val vodId: Int,
    val vodName: String,
    val vodPic: String? = null,
    val vodPicThumb: String? = null,
    val vodScore: String? = null,
    val vodClass: String? = null,
    val vodYear: String? = null,
    val vodRemarks: String? = null,
    val typeId: Int = 0,
    val typeName: String? = null
)

private fun MacCmsVodItem.toSnapshot(): CachedVodSnapshot =
    CachedVodSnapshot(
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        vodPicThumb = vodPicThumb,
        vodScore = vodScore,
        vodClass = vodClass,
        vodYear = vodYear,
        vodRemarks = vodRemarks,
        typeId = typeId,
        typeName = typeName
    )

private fun CachedVodSnapshot.toVodItem(): MacCmsVodItem =
    MacCmsVodItem(
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        vodPicThumb = vodPicThumb,
        vodScore = vodScore,
        vodClass = vodClass,
        vodYear = vodYear,
        vodRemarks = vodRemarks,
        typeId = typeId,
        typeName = typeName
    )
