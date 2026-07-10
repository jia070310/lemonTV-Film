package com.lemon.yingshi.tv.data.local.database

import androidx.room.TypeConverter
import com.lemon.yingshi.tv.domain.model.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MediaType.OTHER // 如果解析失败，默认返回OTHER
        }
    }
}
