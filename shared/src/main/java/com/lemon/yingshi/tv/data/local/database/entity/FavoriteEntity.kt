package com.lemon.yingshi.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import com.lemon.yingshi.tv.domain.model.MediaType

@Entity(
    tableName = "favorites",
    primaryKeys = ["mediaId", "serverUrl"],
    indices = [
        Index(value = ["serverUrl"]),
        Index(value = ["addedAt"])
    ]
)
data class FavoriteEntity(
    val mediaId: String,
    val serverUrl: String = "",
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val year: String? = null,
    val genres: String? = null,
    val mediaType: MediaType = MediaType.OTHER,
    val rating: Float? = null,
    val addedAt: Long = System.currentTimeMillis()
)
