package com.lemon.yingshi.tv.util

import java.io.File

/**
 * 磁盘缓存 LRU 淘汰：超出上限时按最旧文件删除，为新数据腾出空间。
 */
object DiskCacheTrimHelper {

    fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * @param reservedBytes 即将写入的新文件大小，计入总量后再淘汰
     * @param exclude 不参与淘汰的文件（通常是即将覆盖的同名文件）
     */
    fun trimToMaxBytes(
        dir: File,
        maxBytes: Long,
        reservedBytes: Long = 0L,
        exclude: File? = null
    ) {
        if (!dir.exists()) return
        val files = dir.listFiles()
            ?.filter { it.isFile && it.absolutePath != exclude?.absolutePath }
            .orEmpty()
        var total = files.sumOf { it.length() } + reservedBytes
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            val length = file.length()
            if (file.delete()) {
                total -= length
            }
        }
    }
}
