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

        val json = playerListPattern.find(jsContent)?.groupValues?.getOrNull(1) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            val raw: Map<String, Map<String, String>> = gson.fromJson(json, type)
            raw.mapNotNull { (code, info) ->
                info["show"]?.trim()?.takeIf { it.isNotBlank() }?.let { code to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
