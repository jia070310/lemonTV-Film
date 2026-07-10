package com.lemon.yingshi.tv.data.remote.parser

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MacCmsPlayerConfigParser {

    private val gson = Gson()
    private val playerListPattern = Regex(
        """MacPlayerConfig\.player_list\s*=\s*(\{.*?\})\s*,\s*MacPlayerConfig\.downer_list""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parsePlayerShowNames(jsContent: String): Map<String, String> {
        if (jsContent.isBlank()) return emptyMap()

        val json = extractPlayerListJson(jsContent) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            val raw: Map<String, Map<String, String>> = gson.fromJson(json, type)
            raw.mapNotNull { (code, info) ->
                info["show"]?.trim()?.takeIf { it.isNotBlank() }?.let { code to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun extractPlayerListJson(jsContent: String): String? {
        playerListPattern.find(jsContent)?.groupValues?.getOrNull(1)?.let { return it }

        val marker = "MacPlayerConfig.player_list"
        val markerIndex = jsContent.indexOf(marker)
        if (markerIndex < 0) return null

        val eqIndex = jsContent.indexOf('=', markerIndex + marker.length)
        if (eqIndex < 0) return null

        var index = eqIndex + 1
        while (index < jsContent.length && jsContent[index].isWhitespace()) {
            index++
        }
        if (index >= jsContent.length || jsContent[index] != '{') return null

        var depth = 0
        val start = index
        while (index < jsContent.length) {
            when (jsContent[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return jsContent.substring(start, index + 1)
                    }
                }
            }
            index++
        }
        return null
    }
}
