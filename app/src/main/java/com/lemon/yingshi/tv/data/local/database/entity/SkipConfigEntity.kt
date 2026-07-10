package com.lemon.yingshi.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 跳过片头片尾配置实体
 * 按剧集（季）存储跳过配置
 */
@Entity(
    tableName = "skip_config",
    indices = [Index(value = ["mediaId", "seasonNumber"], unique = true)]
)
data class SkipConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 媒体ID（电影或剧集ID）
     */
    val mediaId: String,
    
    /**
     * 季数（电影为0）
     */
    val seasonNumber: Int = 0,
    
    /**
     * 片头时长（毫秒）
     */
    val introDuration: Long = 90000, // 默认90秒
    
    /**
     * 片尾时长（毫秒）
     */
    val outroDuration: Long = 120000, // 默认120秒
    
    /**
     * 是否启用片头跳过
     */
    val skipIntroEnabled: Boolean = true,
    
    /**
     * 是否启用片尾跳过
     */
    val skipOutroEnabled: Boolean = true,
    
    /**
     * 更新时间
     */
    val updatedAt: Long = System.currentTimeMillis()
)
