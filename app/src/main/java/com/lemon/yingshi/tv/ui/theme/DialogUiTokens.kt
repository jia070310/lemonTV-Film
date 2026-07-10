package com.lemon.yingshi.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 全局弹窗样式变量：统一在此调整透明度、边框和圆角。
 */
object DialogUiTokens {
    val ContainerColor = Color(0xFF1A1A1A).copy(alpha = 0.92f)
    val BorderColor = Color.White.copy(alpha = 0.15f)
    val CornerRadius = 12.dp
    val BorderWidth = 1.dp
}
