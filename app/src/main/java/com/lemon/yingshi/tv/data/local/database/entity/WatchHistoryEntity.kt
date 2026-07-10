package com.lemon.yingshi.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["movieId"]),
        Index(value = ["lastWatchedAt"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val movieId: String,
    val episodeId: String? = null,
    val progress: Long = 0,
    val duration: Long = 0,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val watchCount: Int = 1,
    val title: String? = null,
    val posterUrl: String? = null,
    /** 本地截帧封面路径（优先于 posterUrl 展示） */
    val localCoverPath: String? = null,
    val episodeTitle: String? = null,
    val videoUrl: String? = null
)
