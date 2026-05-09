package com.lomen.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 统一“提示窗口”样式：白底胶囊 + 黑字。
 * 用于替换黄色/绿色提示的视觉不一致问题。
 */
@Composable
fun WhitePillToast(
    message: String,
    icon: ImageVector = Icons.Filled.Info,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 72.dp,
    backgroundAlpha: Float = 0.75f,
    iconTint: Color = Color.Black,
    textColor: Color = Color.Black
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = modifier
                .padding(horizontal = 40.dp)
                .padding(bottom = bottomPadding)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = backgroundAlpha))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

