package com.lemon.yingshi.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** 白底胶囊形、黑字，用于简短信息提示（如 TMDB 未配置） */
private val PillBackground = Color.White
private const val PillBackgroundAlpha = 0.75f
private val PillText = Color.Black

@Composable
fun InfoPillToast(
    message: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 72.dp
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
                .background(PillBackground.copy(alpha = PillBackgroundAlpha))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = PillText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = PillText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
