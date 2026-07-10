package com.lemon.yingshi.tv.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * 跳过片头片尾配置DAO
 */
@Dao
interface SkipConfigDao {
    
    /**
     * 根据媒体ID和季数获取配置
     */
    @Query("SELECT * FROM skip_config WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber LIMIT 1")
    suspend fun getConfig(mediaId: String, seasonNumber: Int): SkipConfigEntity?
    
    /**
     * 根据媒体ID和季数获取配置（Flow）
     */
    @Query("SELECT * FROM skip_config WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber LIMIT 1")
    fun getConfigFlow(mediaId: String, seasonNumber: Int): Flow<SkipConfigEntity?>
    
    /**
     * 插入配置
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SkipConfigEntity): Long
    
    /**
     * 更新配置
     */
    @Update
    suspend fun update(config: SkipConfigEntity)
    
    /**
     * 删除配置
     */
    @Query("DELETE FROM skip_config WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber")
    suspend fun delete(mediaId: String, seasonNumber: Int)
    
    /**
     * 获取所有配置
     */
    @Query("SELECT * FROM skip_config ORDER BY updatedAt DESC")
    fun getAllConfigs(): Flow<List<SkipConfigEntity>>
}
