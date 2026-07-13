package com.lemon.yingshi.tv.data.remote.parser

/**
 * 根据服务器探测结果组装 MacCMS 版本描述。
 */
object MacCmsVersionDetector {

    data class ProbeResult(
        val provideProtocolVersion: String? = null,
        val homepageVersionCode: String? = null,
        val restTypeApiAvailable: Boolean = false
    )

    fun formatVersionLabel(probe: ProbeResult): String {
        val segments = mutableListOf<String>()

        val core = probe.homepageVersionCode?.let { "MacCMS $it" } ?: "MacCMS v10"
        segments.add(core)

        val details = mutableListOf<String>()
        probe.provideProtocolVersion?.let { details.add("采集协议 v$it") }
        if (probe.restTypeApiAvailable) {
            details.add("REST 模块")
        } else if (probe.provideProtocolVersion == null && probe.homepageVersionCode == null) {
            details.add("视频采集 API")
        }

        return if (details.isEmpty()) {
            core
        } else {
            "$core（${details.joinToString(" · ")}）"
        }
    }

    /** 从 provide/vod XML 响应解析 rss version */
    fun parseProvideXmlProtocolVersion(xml: String): String? {
        val match = RSS_VERSION_REGEX.find(xml) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    /** 从首页 HTML 尝试提取 MacCMS 程序版本号（如 2025.1000.4052） */
    fun parseHomepageVersionCode(html: String): String? {
        MACCMS_VERSION_CODE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
        VERSION_CODE_REGEX.findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.count { c -> c == '.' } >= 2 }
            ?.let { return it }
        return null
    }

    private val RSS_VERSION_REGEX =
        Regex("""<rss\s+version=["']([\d.]+)["']""", RegexOption.IGNORE_CASE)

    private val MACCMS_VERSION_CODE_REGEX =
        Regex("""(?i)(?:MacCMS|苹果CMS|maccms)[^\d]{0,24}(\d{4}\.\d+(?:\.\d+)+)""")

    private val VERSION_CODE_REGEX =
        Regex("""(\d{4}\.\d+(?:\.\d+)+)""")
}
