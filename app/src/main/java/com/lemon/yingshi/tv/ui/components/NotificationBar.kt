package com.lemon.yingshi.tv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemon.yingshi.tv.domain.model.Notification

/**
 * 通知栏组件
 * 显示滚动的通知消息
 */
@Composable
fun NotificationBar(
    notification: Notification?,
    modifier: Modifier = Modifier
) {
    if (notification == null) return
    
    // 解析文字颜色
    val textColor = try {
        Color(android.graphics.Color.parseColor(notification.textColor))
    } catch (e: Exception) {
        Color.White
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.CenterStart
    ) {
        ScrollingText(
            text = notification.message,
            textColor = textColor,
            scrollSpeed = notification.scrollSpeed
        )
    }
}

/**
 * 滚动文字组件
 */
@Composable
private fun ScrollingText(
    text: String,
    textColor: Color,
    scrollSpeed: Float
) {
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(0f) }
    var textWidth by remember { mutableStateOf(0f) }
    
    // 确保尺寸已经测量完成后再启动动画
    val isReady = textWidth > 0 && containerWidth > 0 && scrollSpeed > 0
    
    // 计算滚动持续时间（毫秒）
    val scrollDuration = remember(textWidth, containerWidth, scrollSpeed) {
        if (isReady) {
            ((textWidth + containerWidth) / scrollSpeed * 1000).toInt()
        } else {
            5000 // 默认5秒
        }
    }
    
    // 创建无限循环动画（只在尺寸准备好后启动）
    val infiniteTransition = rememberInfiniteTransition(label = "notification_scroll")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = containerWidth,
        targetValue = if (isReady) -textWidth else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = scrollDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "notification_scroll_offset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                with(density) {
                    containerWidth = size.width.toFloat()
                }
            }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .offset(x = with(density) { offsetX.toDp() })
                .align(Alignment.CenterStart)
                .onSizeChanged { size ->
                    with(density) {
                        textWidth = size.width.toFloat()
                    }
                }
        )
    }
}
