package com.lemon.yingshi.tv.data.remote.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/** MacCMS 数字字段经常是 `"9"` 字符串，也有真正的 number。 */
class FlexibleIntAdapter : JsonDeserializer<Int>, JsonSerializer<Int> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Int = parseFlexibleInt(json) ?: 0

    override fun serialize(
        src: Int?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement = if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
}

class FlexibleIntOrNullAdapter : JsonDeserializer<Int?>, JsonSerializer<Int?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Int? = parseFlexibleInt(json)

    override fun serialize(
        src: Int?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement = if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
}

internal fun parseFlexibleInt(json: JsonElement?): Int? {
    if (json == null || json.isJsonNull) return null
    if (!json.isJsonPrimitive) return null
    val primitive = json.asJsonPrimitive
    return when {
        primitive.isNumber -> primitive.asInt
        primitive.isBoolean -> if (primitive.asBoolean) 1 else 0
        primitive.isString -> {
            val raw = primitive.asString.trim()
            if (raw.isEmpty()) null else raw.toDoubleOrNull()?.toInt()
        }
        else -> null
    }
}
