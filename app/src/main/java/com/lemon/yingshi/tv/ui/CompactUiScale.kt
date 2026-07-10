package com.lemon.yingshi.tv.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 低分辨率（如 720p 及以下或较窄宽度）下小于 1，用于整体缩小间距与控件尺寸，避免内容被裁切。
 */
val LocalCompactUiScale = compositionLocalOf { 1f }

fun computeCompactUiScale(screenHeightDp: Int, screenWidthDp: Int): Float {
    val byHeight = when {
        screenHeightDp >= 900 -> 1f
        screenHeightDp >= 800 -> 0.95f
        screenHeightDp >= 720 -> 0.86f
        screenHeightDp >= 600 -> 0.78f
        else -> 0.7f
    }
    val byWidth = when {
        screenWidthDp >= 1280 -> 1f
        screenWidthDp >= 1100 -> 0.93f
        screenWidthDp >= 960 -> 0.86f
        else -> 0.8f
    }
    return min(byHeight, byWidth).coerceIn(0.65f, 1f)
}

fun Dp.scale(scale: Float): Dp = (this.value * scale).coerceAtLeast(0.5f).dp
