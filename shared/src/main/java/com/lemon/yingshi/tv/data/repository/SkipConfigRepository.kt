package com.lemon.yingshi.tv.data.repository

import com.lemon.yingshi.tv.data.local.database.dao.SkipConfigDao
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跳过片头片尾配置仓库
 */
@Singleton
class SkipConfigRepository @Inject constructor(
    private val skipConfigDao: SkipConfigDao
) {
    
    /**
     * 获取配置（ suspend ）
     */
    suspend fun getConfig(mediaId: String, seasonNumber: Int): SkipConfigEntity? {
        return skipConfigDao.getConfig(mediaId, seasonNumber)
    }
    
    /**
     * 获取配置（ Flow ）
     */
    fun getConfigFlow(mediaId: String, seasonNumber: Int): Flow<SkipConfigEntity?> {
        return skipConfigDao.getConfigFlow(mediaId, seasonNumber)
    }
    
    /**
     * 获取或创建默认配置
     */
    suspend fun getOrCreateConfig(mediaId: String, seasonNumber: Int): SkipConfigEntity {
        return skipConfigDao.getConfig(mediaId, seasonNumber) ?: SkipConfigEntity(
            mediaId = mediaId,
            seasonNumber = seasonNumber
        )
    }
    
    /**
     * 保存配置
     */
    suspend fun saveConfig(config: SkipConfigEntity) {
        skipConfigDao.insert(config.copy(updatedAt = System.currentTimeMillis()))
    }
    
    /**
     * 更新片头时长
     */
    suspend fun updateIntroDuration(mediaId: String, seasonNumber: Int, duration: Long) {
        val config = getOrCreateConfig(mediaId, seasonNumber)
        skipConfigDao.insert(config.copy(introDuration = duration))
    }
    
    /**
     * 更新片尾时长
     */
    suspend fun updateOutroDuration(mediaId: String, seasonNumber: Int, duration: Long) {
        val config = getOrCreateConfig(mediaId, seasonNumber)
        skipConfigDao.insert(config.copy(outroDuration = duration))
    }
    
    /**
     * 更新片头跳过开关
     */
    suspend fun updateSkipIntroEnabled(mediaId: String, seasonNumber: Int, enabled: Boolean) {
        val config = getOrCreateConfig(mediaId, seasonNumber)
        skipConfigDao.insert(config.copy(skipIntroEnabled = enabled))
    }
    
    /**
     * 更新片尾跳过开关
     */
    suspend fun updateSkipOutroEnabled(mediaId: String, seasonNumber: Int, enabled: Boolean) {
        val config = getOrCreateConfig(mediaId, seasonNumber)
        skipConfigDao.insert(config.copy(skipOutroEnabled = enabled))
    }
    
    /**
     * 删除配置
     */
    suspend fun deleteConfig(mediaId: String, seasonNumber: Int) {
        skipConfigDao.delete(mediaId, seasonNumber)
    }
    
    /**
     * 重置为默认值
     */
    suspend fun resetToDefault(mediaId: String, seasonNumber: Int) {
        skipConfigDao.insert(SkipConfigEntity(
            mediaId = mediaId,
            seasonNumber = seasonNumber
        ))
    }
}
