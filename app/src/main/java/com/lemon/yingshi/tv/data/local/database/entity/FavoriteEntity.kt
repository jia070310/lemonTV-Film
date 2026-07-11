package com.lemon.yingshi.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lemon.yingshi.tv.domain.model.MediaType

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["mediaId"], unique = true),
        Index(value = ["addedAt"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
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
