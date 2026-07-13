package com.lemon.yingshi.tv.domain.model

import kotlinx.serialization.Serializable

/**
 * 通知模型
 */
@Serializable
data class Notification(
    val id: String,
    val message: String,
    val textColor: String = "#FFFFFF",  // 文字颜色，默认白色
    val scrollSpeed: Float = 50f,       // 滚动速度（像素/秒），默认50
    val startTime: Long? = null,        // 开启时间（Unix时间戳，毫秒），null表示立即开启
    val endTime: Long? = null,          // 结束时间（Unix时间戳，毫秒），null表示不结束
    val enabled: Boolean = true         // 是否启用
) {
    /**
     * 检查通知是否应该显示
     */
    fun shouldDisplay(): Boolean {
        if (!enabled) return false
        
        val now = System.currentTimeMillis()
        
        // 检查开始时间
        if (startTime != null && now < startTime) {
            return false
        }
        
        // 检查结束时间
        if (endTime != null && now > endTime) {
            return false
        }
        
        return true
    }
}

/**
 * 通知列表响应
 */
@Serializable
data class NotificationResponse(
    val notifications: List<Notification>
)
