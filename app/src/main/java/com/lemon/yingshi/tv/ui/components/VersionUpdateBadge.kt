package com.lemon.yingshi.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import kotlinx.coroutines.delay

/**
 * 版本更新提示徽章（TV 可聚焦；下键回到顶栏对应列，右键到刷新）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VersionUpdateBadge(
    versionName: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    headerFocusRequesters: List<FocusRequester>? = null,
    focusedHeaderColumnIndex: Int = 0
) {
    LaunchedEffect(Unit) {
        delay(10000)
        onDismiss()
    }

    var isFocused by remember { mutableStateOf(false) }
    val iconBackgroundColor = if (isFocused) Color.Red else Color.White
    val iconTintColor = if (isFocused) Color.White else PrimaryYellow

    Card(
        onClick = onClick,
        shape = CardDefaults.shape(RoundedCornerShape(24.dp)),
        colors = CardDefaults.colors(
            containerColor = PrimaryYellow,
            focusedContainerColor = PrimaryYellow.copy(alpha = 0.92f),
            pressedContainerColor = PrimaryYellow.copy(alpha = 0.88f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, BackgroundDark),
                shape = RoundedCornerShape(24.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusProperties {
                headerFocusRequesters?.let { hdr ->
                    if (hdr.isNotEmpty()) {
                        val idx = focusedHeaderColumnIndex.coerceIn(0, hdr.lastIndex)
                        down = hdr[idx]
                        right = hdr[0]
                    }
                }
            }
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "新版本: v$versionName",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
