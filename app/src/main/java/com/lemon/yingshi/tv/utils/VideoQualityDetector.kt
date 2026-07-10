package com.lemon.yingshi.tv.utils

/**
 * 从文件路径与文件名推断清晰度（用于同集多版本合并与播放器选项排序）。
 * 支持目录名中含 4K / 1080 / 2160p 等常见命名。
 */
object VideoQualityDetector {

    fun label(filePath: String, fileName: String): String {
        val combined = combinePath(filePath, fileName)
        val lower = combined.lowercase()
        return when {
            matches(lower, listOf("2160", "2160p", "3840", "uhd")) ||
                matches(lower, listOf("4k")) ||
                combined.contains("超高清") -> "4K"
            matches(lower, listOf("1080", "1080p", "fhd")) ||
                combined.contains("全高清") -> "1080p"
            matches(lower, listOf("720", "720p")) -> "720p"
            matches(lower, listOf("480", "480p", "576", "576p")) -> "480p"
            matches(lower, listOf("bluray", "bdremux")) ||
                combined.contains("蓝光") -> "蓝光"
            matches(lower, listOf("dolby vision")) ||
                matches(lower, listOf("hdr")) -> "HDR"
            else -> "高清"
        }
    }

    /** 数值越大表示清晰度越高，用于默认选最高画质 */
    fun rank(filePath: String, fileName: String): Int {
        val tier = when (label(filePath, fileName)) {
            "4K" -> 100
            "HDR" -> 92
            "蓝光" -> 88
            "1080p" -> 80
            "高清" -> 45
            "720p" -> 40
            "480p" -> 20
            else -> 35
        }
        return tier * 100000 + (fileName.hashCode() and 0xffff)
    }

    private fun combinePath(filePath: String, fileName: String): String {
        val p = filePath.trim().trimEnd('/')
        val n = fileName.trim()
        return if (p.isEmpty()) n else "$p/$n"
    }

    private fun matches(lowerCombined: String, tokens: List<String>): Boolean =
        tokens.any { lowerCombined.contains(it.lowercase()) }
}
