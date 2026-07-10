package com.lemon.yingshi.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.data.local.database.entity.SkipConfigEntity
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import java.util.concurrent.TimeUnit

/**
 * 跳过片头片尾设置弹窗
 * 尺寸：405 x 320 dp（增加高度避免遮盖）
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SkipConfigDialog(
    show: Boolean,
    config: SkipConfigEntity,
    scopeTitle: String = "",
    currentPosition: Long = 0,
    duration: Long = 0,
    onDismiss: () -> Unit,
    onSave: (SkipConfigEntity) -> Unit,
    onReset: () -> Unit,
    onSetCurrentAsIntroEnd: () -> Unit,
    onSetCurrentAsOutroStart: () -> Unit
) {
    if (!show) return

    val resetButtonFocusRequester = remember { FocusRequester() }
    val introSliderFocusRequester = remember { FocusRequester() }
    val outroSliderFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }
    
    // 本地状态
    var introDuration by remember { mutableLongStateOf(config.introDuration) }
    var outroDuration by remember { mutableLongStateOf(config.outroDuration) }
    var skipIntroEnabled by remember { mutableStateOf(config.skipIntroEnabled) }
    var skipOutroEnabled by remember { mutableStateOf(config.skipOutroEnabled) }
    
    // 计算最大值 - 最大10分钟（600000毫秒）
    val maxIntroDuration = if (duration > 0) (duration / 2).coerceAtMost(600000) else 600000
    val maxOutroDuration = if (duration > 0) (duration / 2).coerceAtMost(600000) else 600000

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(405.dp)
                .height(360.dp)
                .background(
                    color = DialogUiTokens.ContainerColor,
                    shape = RoundedCornerShape(DialogUiTokens.CornerRadius)
                )
                .border(
                    width = DialogUiTokens.BorderWidth,
                    color = DialogUiTokens.BorderColor,
                    shape = RoundedCornerShape(DialogUiTokens.CornerRadius)
                )
                // 焦点陷阱：阻止焦点逃出窗口
                .focusProperties {
                    exit = { FocusRequester.Cancel }
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .focusGroup()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "跳过片头/片尾",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (scopeTitle.isNotEmpty()) {
                            Text(
                                text = "生效范围：整部剧集（$scopeTitle）",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            Text(
                                text = "生效范围：整部剧集",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                    
                    // 重置按钮 - 未选中灰底白字，高亮黄底黑字
                    var isResetFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            // 重置为0，但不禁用（方便重新设置）
                            introDuration = 0L
                            outroDuration = 0L
                            skipIntroEnabled = true
                            skipOutroEnabled = true
                            onReset()
                        },
                        modifier = Modifier
                            .focusRequester(resetButtonFocusRequester)
                            .onFocusChanged { isResetFocused = it.isFocused },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF444444),
                            contentColor = Color.White,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "重置",
                            fontSize = 14.sp,
                            color = if (isResetFocused) Color.Black else Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 片头时长设置
                DurationSliderWithControls(
                    label = "片头时长",
                    duration = introDuration,
                    maxDuration = maxIntroDuration,
                    currentPosition = currentPosition,
                    onDurationChange = { introDuration = it },
                    onSetCurrentPosition = {
                        if (currentPosition > 0 && currentPosition <= maxIntroDuration) {
                            introDuration = currentPosition
                            onSetCurrentAsIntroEnd()
                        }
                    },
                    isEnabled = skipIntroEnabled,
                    sliderFocusRequester = introSliderFocusRequester,
                    onToggleEnabled = { skipIntroEnabled = it }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 片尾时长设置
                DurationSliderWithControls(
                    label = "片尾时长",
                    duration = outroDuration,
                    maxDuration = maxOutroDuration,
                    currentPosition = currentPosition,
                    onDurationChange = { outroDuration = it },
                    onSetCurrentPosition = {
                        if (duration > 0 && currentPosition > 0) {
                            val newOutroDuration = duration - currentPosition
                            if (newOutroDuration > 0 && newOutroDuration <= maxOutroDuration) {
                                outroDuration = newOutroDuration
                                onSetCurrentAsOutroStart()
                            }
                        }
                    },
                    isEnabled = skipOutroEnabled,
                    sliderFocusRequester = outroSliderFocusRequester,
                    onToggleEnabled = { skipOutroEnabled = it },
                    isOutro = true,
                    totalDuration = duration
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 保存按钮 - 高亮时黄底黑字，非高亮时保持原样
                var isSaveFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        onSave(
                            config.copy(
                                introDuration = introDuration,
                                outroDuration = outroDuration,
                                skipIntroEnabled = skipIntroEnabled,
                                skipOutroEnabled = skipOutroEnabled
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .focusRequester(saveButtonFocusRequester)
                        .onFocusChanged { isSaveFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF444444),
                        contentColor = Color.White,
                        focusedContainerColor = PrimaryYellow,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = PrimaryYellow,
                        pressedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "保存设置",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSaveFocused) Color.Black else Color.White
                    )
                }
            }
        }
    }
    
    LaunchedEffect(show) {
        if (show) {
            // 重置本地状态
            introDuration = config.introDuration
            outroDuration = config.outroDuration
            skipIntroEnabled = config.skipIntroEnabled
            skipOutroEnabled = config.skipOutroEnabled
            // 延迟请求焦点到重置按钮
            kotlinx.coroutines.delay(100)
            resetButtonFocusRequester.requestFocus()
        }
    }
}

/**
 * 带控制功能的时长滑块组件
 * 支持左右键调整时间
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DurationSliderWithControls(
    label: String,
    duration: Long,
    maxDuration: Long,
    currentPosition: Long,
    onDurationChange: (Long) -> Unit,
    onSetCurrentPosition: () -> Unit,
    isEnabled: Boolean,
    sliderFocusRequester: FocusRequester,
    onToggleEnabled: (Boolean) -> Unit,
    isOutro: Boolean = false,
    totalDuration: Long = 0
) {
    var isSliderFocused by remember { mutableStateOf(false) }
    val step = 5000L // 每次调整5秒
    
    Column {
        // 标签行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = if (isEnabled) TextPrimary else TextMuted
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 时间显示
                Text(
                    text = formatDuration(duration),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) PrimaryYellow else TextMuted
                )
            }
            
            // 设置当前位置按钮
            var isSetCurrentFocused by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (isEnabled && currentPosition > 0) {
                        onSetCurrentPosition()
                    }
                },
                modifier = Modifier
                    .height(40.dp)
                    .onFocusChanged { isSetCurrentFocused = it.isFocused },
                enabled = isEnabled && currentPosition > 0,
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFB0B0B0), // 灰白色
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color(0xFF666666),
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = Color.Black
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = if (isOutro) "将当前设为片尾" else "将当前设为片头",
                    fontSize = 12.sp,
                    color = when {
                        !isEnabled || currentPosition <= 0 -> Color(0xFF666666)
                        isSetCurrentFocused -> Color.Black
                        else -> Color(0xFFB0B0B0) // 灰白色
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 滑块区域（可聚焦，支持左右键调整）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 开始/结束标签
            Text(
                text = if (isOutro) formatDuration((totalDuration - duration).coerceAtLeast(0)) else "开始",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.width(45.dp)
            )
            
            // 滑块轨道（可聚焦）- 使用独立的可聚焦区域
            val interactionSource = remember { MutableInteractionSource() }
            val isFocusedState = interactionSource.collectIsFocusedAsState()
            isSliderFocused = isFocusedState.value
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp) // 进一步增加高度以便更容易聚焦
                    .focusRequester(sliderFocusRequester)
                    .focusable(
                        enabled = isEnabled,
                        interactionSource = interactionSource
                    )

                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .onKeyEvent { event ->
                        if (!isEnabled) return@onKeyEvent false
                        
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    // 片尾：按左键增加时长（滑块往左移动）
                                    // 片头：按左键减少时长（滑块往左移动）
                                    val newDuration = if (isOutro) {
                                        (duration + step).coerceAtMost(maxDuration)
                                    } else {
                                        (duration - step).coerceAtLeast(0)
                                    }
                                    onDurationChange(newDuration)
                                    true
                                }
                                Key.DirectionRight -> {
                                    // 片尾：按右键减少时长（滑块往右移动）
                                    // 片头：按右键增加时长（滑块往右移动）
                                    val newDuration = if (isOutro) {
                                        (duration - step).coerceAtLeast(0)
                                    } else {
                                        (duration + step).coerceAtMost(maxDuration)
                                    }
                                    onDurationChange(newDuration)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // 进度
                val progress = if (maxDuration > 0) {
                    (duration.toFloat() / maxDuration).coerceIn(0f, 1f)
                } else 0f
                
                // 轨道背景 - 灰色
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                
                // 滑块指示器 - 片头从左往右，片尾从右往左
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isOutro) {
                        // 片尾：从右往左滑，0 在右边
                        // 进度越大，滑块越靠左
                        val adjustedProgress = progress.coerceIn(0f, 1f)
                        // 右边距 6%，左边距 0%
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((1f - adjustedProgress) * 0.94f + 0.06f)
                                .height(16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp)
                                    .then(
                                        if (isSliderFocused && isEnabled) {
                                            Modifier.shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(8.dp),
                                                spotColor = Color.White,
                                                ambientColor = Color.White
                                            )
                                        } else Modifier
                                    )
                                    .background(
                                        color = when {
                                            !isEnabled -> TextMuted
                                            isSliderFocused -> PrimaryYellow
                                            else -> Color.White
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                        }
                    } else {
                        // 片头：从左往右滑，0 在左边
                        val adjustedProgress = progress.coerceIn(0f, 1f)
                        // 左边距 6%，右边距 0%
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(adjustedProgress * 0.94f + 0.06f)
                                .height(16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp)
                                    .then(
                                        if (isSliderFocused && isEnabled) {
                                            Modifier.shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(8.dp),
                                                spotColor = Color.White,
                                                ambientColor = Color.White
                                            )
                                        } else Modifier
                                    )
                                    .background(
                                        color = when {
                                            !isEnabled -> TextMuted
                                            isSliderFocused -> PrimaryYellow
                                            else -> Color.White
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                        }
                    }
                }
            }
            
            // 结束标签
            Text(
                text = if (isOutro) "结束" else formatDuration(duration),
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.width(45.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * 格式化时长为 mm:ss
 */
private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
