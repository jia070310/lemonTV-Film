package com.lomen.tv.data.remote

import com.lomen.tv.data.webdav.WebDavFile

/**
 * 远端文件访问抽象层。
 *
 * 说明：
 * - 现有刮削/扫描流程基于 WebDAV；为了支持基于 AList/OpenList 的网盘挂载（如光鸭云盘），
 *   需要提供同一套“列目录/递归扫描/取封面/取直链/读文本”等能力。
 * - 这里沿用 [WebDavFile] 作为通用文件模型，避免大范围重构。
 */
interface RemoteFileClient {
    suspend fun listFiles(relativePath: String = ""): Result<List<WebDavFile>>

    suspend fun listAllVideoFiles(onProgress: ((Int) -> Unit)? = null): Result<List<WebDavFile>>

    fun getFileUrl(relativePath: String): String

    suspend fun getCoverImage(directoryPath: String): String?

    suspend fun readTextFile(relativePath: String): String?

    suspend fun fileExists(relativePath: String): Boolean
}

