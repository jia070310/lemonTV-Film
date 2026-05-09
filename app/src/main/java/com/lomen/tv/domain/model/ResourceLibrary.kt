package com.lomen.tv.domain.model

data class ResourceLibrary(
    val id: String,
    val name: String,
    val type: LibraryType,
    val protocol: String = "",
    val host: String = "",
    val port: Int = 0,
    val path: String = "/",
    val username: String = "",
    val password: String = "",
    /** AList/OpenList 基础地址，例如：http://192.168.1.2:5244 */
    val apiBaseUrl: String = "",
    /** AList/OpenList token（通常为 "Bearer xxx" 或直接 token，按服务端要求） */
    val apiToken: String = "",
    /** OAuth refresh token（用于 access_token 过期后续期） */
    val apiRefreshToken: String = "",
    /** access_token 过期时间戳（毫秒）；0 表示未知 */
    val apiTokenExpireAt: Long = 0L,
    /** 光鸭登录方式；历史数据可能为空 */
    val guangyaLoginMode: GuangyaLoginMode? = null,
    /** 光鸭设备标识（用于部分风控/短信/鉴权接口）；历史数据可能为空 */
    val guangyaDeviceId: String? = null,
    /** 扫描范围：可多选目录；为空表示从根目录扫描 */
    val selectedPaths: List<String> = emptyList(),
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class LibraryType {
        WEBDAV, QUARK, GUANGYA
    }

    enum class GuangyaLoginMode {
        QR, SMS
    }

    fun getDisplayUrl(): String {
        return when (type) {
            LibraryType.WEBDAV -> "$protocol://$host:$port$path"
            LibraryType.QUARK -> "网盘"
            LibraryType.GUANGYA -> "光鸭云盘"
        }
    }

    fun getTypeIcon(): String {
        return when (type) {
            LibraryType.WEBDAV -> "🌐"
            LibraryType.QUARK -> "☁️"
            LibraryType.GUANGYA -> "🦆"
        }
    }
}
