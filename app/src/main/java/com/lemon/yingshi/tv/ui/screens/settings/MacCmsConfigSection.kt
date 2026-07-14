package com.lemon.yingshi.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.ui.LocalCompactUiScale
import com.lemon.yingshi.tv.ui.scale
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SuccessGreen
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MacCmsConfigSection(
    contentFocusRequester: FocusRequester,
    viewModel: MacCmsConfigViewModel = hiltViewModel()
) {
    val s = LocalCompactUiScale.current
    val focusManager = LocalFocusManager.current
    val serverUrl by viewModel.serverUrl.collectAsState()
    val lastTestTime by viewModel.lastTestTime.collectAsState()
    val lastTestStatus by viewModel.lastTestStatus.collectAsState()
    val siteName by viewModel.siteName.collectAsState()
    val maccmsVersion by viewModel.maccmsVersion.collectAsState()
    val savedCategoryCount by viewModel.savedCategoryCount.collectAsState()
    val savedApiSource by viewModel.savedApiSource.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    var inputUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    val saveButtonFocusRequester = remember { FocusRequester() }
    val testButtonFocusRequester = remember { FocusRequester() }

    val isConnected = lastTestStatus == "已连接"
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val cardShape = RoundedCornerShape(16.dp.scale(s))

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp.scale(s))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp.scale(s))
                            .clip(RoundedCornerShape(10.dp.scale(s)))
                            .background(Color.White.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = Color(0xFF60a5fa),
                            modifier = Modifier.size(22.dp.scale(s))
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp.scale(s)))
                    Column {
                        Text(
                            text = "MacCMS 服务器",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * s + 2f).sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "配置苹果 CMS 站点地址，用于筛选页数据获取",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp.scale(s)))

                Text(
                    text = "服务器地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))

                MacCmsUrlInput(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = "https://your-maccms.com",
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = contentFocusRequester,
                    downFocusRequester = testButtonFocusRequester,
                    onMoveLeft = { focusManager.moveFocus(FocusDirection.Left) }
                )

                Spacer(modifier = Modifier.height(16.dp.scale(s)))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp.scale(s)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：测试（先测后存）；非黄底
                    Button(
                        onClick = { viewModel.testConnection(inputUrl) },
                        enabled = !isTesting,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceDark,
                            focusedContainerColor = PrimaryYellow,
                            contentColor = TextPrimary,
                            focusedContentColor = BackgroundDark
                        ),
                        modifier = Modifier
                            .focusRequester(testButtonFocusRequester)
                            .focusProperties {
                                right = saveButtonFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> contentFocusRequester.tryRequestFocus()
                                    Key.DirectionLeft -> {
                                        focusManager.moveFocus(FocusDirection.Left)
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTesting) "测试中..." else "测试连通性")
                    }

                    // 右侧：保存；黄底主操作
                    Button(
                        onClick = { viewModel.saveServerUrl(inputUrl) },
                        colors = ButtonDefaults.colors(
                            containerColor = PrimaryYellow,
                            focusedContainerColor = PrimaryYellow,
                            contentColor = BackgroundDark,
                            focusedContentColor = BackgroundDark
                        ),
                        modifier = Modifier
                            .focusRequester(saveButtonFocusRequester)
                            .focusProperties {
                                left = testButtonFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> contentFocusRequester.tryRequestFocus()
                                    else -> false
                                }
                            }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存配置")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp.scale(s)))

                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp.scale(s))
                            .size(10.dp.scale(s))
                            .clip(CircleShape)
                            .background(
                                when {
                                    isTesting -> PrimaryYellow
                                    isConnected -> SuccessGreen
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp.scale(s)))
                    Text(
                        text = when {
                            isTesting -> "检测中"
                            testResult?.message?.isNotBlank() == true -> testResult!!.message
                            lastTestStatus.isNotBlank() -> lastTestStatus
                            else -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (lastTestTime > 0L) {
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text(
                        text = "上次测试: ${dateFormat.format(Date(lastTestTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (saveMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    Text(
                        text = saveMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryYellow
                    )
                }

                if (isConnected) {
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp.scale(s)))
                            .background(BackgroundDark.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp.scale(s))) {
                            Text(
                                text = "服务器信息",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp.scale(s)))
                            Text(
                                text = "站点: ${siteName.ifBlank { inputUrl }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            val displayCategoryCount = maxOf(
                                testResult?.categoryCount ?: 0,
                                savedCategoryCount
                            )
                            val displayVersion = testResult?.maccmsVersionLabel?.takeIf { it.isNotBlank() }
                                ?: maccmsVersion.ifBlank { "—" }
                            val displayApiSource = testResult?.apiSourceLabel?.takeIf { it.isNotBlank() }
                                ?: savedApiSource.ifBlank { "—" }
                            Text(
                                text = "MacCMS 版本: $displayVersion",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "分类数量: $displayCategoryCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "分类来源: $displayApiSource",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun MacCmsUrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onMoveLeft: () -> Boolean
) {
    val s = LocalCompactUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    // 聚焦不自动弹键盘；首次确定弹 IME，完成输入后再确定落到测试按钮
    var imeOpenedByConfirm by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputShape = RoundedCornerShape(8.dp.scale(s))

    fun hideImeAndMoveToTest(): Boolean {
        keyboardController?.hide()
        imeOpenedByConfirm = false
        return downFocusRequester.tryRequestFocus()
    }

    fun onConfirmWhileFocused(): Boolean {
        return if (!imeOpenedByConfirm) {
            keyboardController?.show()
            imeOpenedByConfirm = true
            true
        } else {
            hideImeAndMoveToTest()
        }
    }

    fun moveCursorBy(delta: Int): Boolean {
        val sel = textFieldValue.selection
        val collapsed = if (sel.collapsed) {
            sel.start
        } else if (delta < 0) {
            sel.min
        } else {
            sel.max
        }
        val next = (collapsed + delta).coerceIn(0, textFieldValue.text.length)
        if (next == collapsed && sel.collapsed) return false
        textFieldValue = textFieldValue.copy(selection = TextRange(next))
        return true
    }

    Box(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter, Key.DirectionCenter -> onConfirmWhileFocused()
                Key.DirectionDown, Key.Tab -> hideImeAndMoveToTest()
                Key.DirectionLeft -> {
                    // 行内先移光标；仅在行首才跳出到左侧栏
                    if (moveCursorBy(-1)) {
                        true
                    } else {
                        keyboardController?.hide()
                        imeOpenedByConfirm = false
                        onMoveLeft()
                    }
                }
                Key.DirectionRight -> moveCursorBy(1)
                else -> false
            }
        }
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != value) {
                    onValueChange(newValue.text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp.scale(s))
                .clip(inputShape)
                .background(BackgroundDark)
                .border(
                    width = if (isFocused) 2.dp.scale(s) else 0.dp,
                    color = if (isFocused) PrimaryYellow else Color.Transparent,
                    shape = inputShape
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        keyboardController?.hide()
                        imeOpenedByConfirm = false
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            cursorBrush = SolidColor(PrimaryYellow),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { hideImeAndMoveToTest() }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp.scale(s)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
