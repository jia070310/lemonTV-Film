package com.lemon.yingshi.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButtonDefaults

/**
 * TV 端可选项/按钮统一样式：
 * - 选中（未聚焦）：灰底白字
 * - 获得焦点：黄底黑字
 */
object TvSelectableTokens {
    val selectedContainerColor: Color = SurfaceVariant
    val unselectedContainerColor: Color = SurfaceVariant.copy(alpha = 0.55f)
    val focusedContainerColor: Color = PrimaryYellow
    val selectedContentColor: Color = TextPrimary
    val focusedContentColor: Color = BackgroundDark

    fun contentColor(isFocused: Boolean): Color =
        if (isFocused) focusedContentColor else selectedContentColor

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun chipColors(isSelected: Boolean) = CardDefaults.colors(
        containerColor = if (isSelected) selectedContainerColor else unselectedContainerColor,
        focusedContainerColor = focusedContainerColor
    )

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun buttonColors() = ButtonDefaults.colors(
        containerColor = selectedContainerColor,
        focusedContainerColor = focusedContainerColor,
        contentColor = selectedContentColor,
        focusedContentColor = focusedContentColor
    )

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun iconButtonColors(isSelected: Boolean = false) = IconButtonDefaults.colors(
        containerColor = if (isSelected) selectedContainerColor else Color.Transparent,
        contentColor = selectedContentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor
    )
}
