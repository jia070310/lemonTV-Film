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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.lemon.yingshi.tv.ui.LocalCompactUiScale
import com.lemon.yingshi.tv.ui.scale
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SuccessGreen
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun resourceActionButtonColors() = ButtonDefaults.colors(
    containerColor = SurfaceVariant,
    focusedContainerColor = PrimaryYellow,
    pressedContainerColor = PrimaryYellow,
    contentColor = TextPrimary,
    focusedContentColor = BackgroundDark,
    pressedContentColor = BackgroundDark
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResourceActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocused: (Boolean) -> Unit = {},
    icon: ImageVector? = null,
    label: String? = null,
    iconContentDescription: String? = label
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) BackgroundDark else TextPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = resourceActionButtonColors(),
        modifier = modifier.onFocusChanged { state ->
            focused = state.isFocused
            onFocused(state.isFocused)
        }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
        }
        if (icon != null && label != null) {
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (label != null) {
            Text(text = label, color = fg)
        }
    }
}

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
    val serverList by viewModel.serverList.collectAsState()
    val lastTestTime by viewModel.lastTestTime.collectAsState()
    val lastTestStatus by viewModel.lastTestStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testingUrl by viewModel.testingUrl.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    var inputUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var inputName by remember(serverUrl) {
        mutableStateOf(serverList.find { it.url == serverUrl }?.name.orEmpty())
    }
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingDeleteUrl by remember { mutableStateOf<String?>(null) }

    val saveButtonFocusRequester = remember { FocusRequester() }
    val testButtonFocusRequester = remember { FocusRequester() }
    val qrButtonFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }

    val isConnected = testResult?.success == true || lastTestStatus == "已连接"
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
                            text = "配置多个苹果 CMS 地址，当前源不通时自动切换",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp.scale(s)))

                Text(
                    text = "服务器名称",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp.scale(s)))

                MacCmsUrlInput(
                    value = inputName,
                    onValueChange = { inputName = it },
                    placeholder = "例如：红牛资源、备用源",
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = contentFocusRequester,
                    downFocusRequester = urlFocusRequester,
                    onMoveLeft = { focusManager.moveFocus(FocusDirection.Left) }
                )

                Spacer(modifier = Modifier.height(12.dp.scale(s)))

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
                    focusRequester = urlFocusRequester,
                    downFocusRequester = testButtonFocusRequester,
                    upFocusRequester = contentFocusRequester,
                    onMoveLeft = { focusManager.moveFocus(FocusDirection.Left) }
                )

                Spacer(modifier = Modifier.height(16.dp.scale(s)))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp.scale(s)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResourceActionButton(
                        onClick = { viewModel.testConnection(inputUrl) },
                        enabled = !isTesting,
                        icon = Icons.Default.Link,
                        label = if (isTesting && testingUrl == inputUrl.trimEnd('/')) "测试中..." else "测试连通性",
                        modifier = Modifier
                            .focusRequester(testButtonFocusRequester)
                            .focusProperties {
                                right = saveButtonFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> urlFocusRequester.tryRequestFocus()
                                    Key.DirectionLeft -> {
                                        focusManager.moveFocus(FocusDirection.Left)
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )

                    ResourceActionButton(
                        onClick = { viewModel.saveServerUrl(inputUrl, inputName) },
                        icon = Icons.Default.Save,
                        label = "保存配置",
                        modifier = Modifier
                            .focusRequester(saveButtonFocusRequester)
                            .focusProperties {
                                left = testButtonFocusRequester
                                right = qrButtonFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> urlFocusRequester.tryRequestFocus()
                                    else -> false
                                }
                            }
                    )

                    ResourceActionButton(
                        onClick = { showQrDialog = true },
                        icon = Icons.Default.QrCode2,
                        label = "手机扫码管理",
                        modifier = Modifier
                            .focusRequester(qrButtonFocusRequester)
                            .focusProperties {
                                left = saveButtonFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> urlFocusRequester.tryRequestFocus()
                                    Key.DirectionLeft -> saveButtonFocusRequester.tryRequestFocus()
                                    else -> false
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp.scale(s)))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp.scale(s)),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (lastTestTime > 0L) {
                            Spacer(modifier = Modifier.height(6.dp.scale(s)))
                            Text(
                                text = "上次测试: ${dateFormat.format(Date(lastTestTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = saveMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryYellow,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1.15f)
                            .padding(start = 12.dp.scale(s))
                    )
                }

                if (serverList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp.scale(s)))
                    Text(
                        text = "已保存的服务器（当前源不通时自动切换）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp.scale(s)))
                    serverList.forEach { entry ->
                        val active = entry.url == serverUrl
                        var testFocused by remember(entry.url) { mutableStateOf(false) }
                        var useFocused by remember(entry.url) { mutableStateOf(false) }
                        var deleteFocused by remember(entry.url) { mutableStateOf(false) }
                        val rowHighlighted = testFocused || useFocused || deleteFocused
                        val rowShape = RoundedCornerShape(10.dp.scale(s))
                        val title = buildString {
                            append(entry.name.ifBlank { entry.url })
                            if (active) append("  · 当前")
                        }
                        val subtitle = buildList {
                            if (entry.name.isNotBlank()) add(entry.url)
                            add(entry.compactSummary().ifBlank { "未测试" })
                        }.joinToString(" · ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp.scale(s))
                                .clip(rowShape)
                                .background(BackgroundDark.copy(alpha = 0.6f))
                                .then(
                                    if (rowHighlighted) {
                                        Modifier.border(2.dp.scale(s), PrimaryYellow, rowShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(10.dp.scale(s)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            ResourceActionButton(
                                onClick = { viewModel.testConnection(entry.url) },
                                enabled = !isTesting,
                                label = if (isTesting && testingUrl == entry.url) "测试中" else "测试",
                                onFocused = { testFocused = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp.scale(s)))
                            if (!active) {
                                ResourceActionButton(
                                    onClick = {
                                        inputUrl = entry.url
                                        inputName = entry.name
                                        viewModel.selectServer(entry.url)
                                    },
                                    label = "使用",
                                    onFocused = { useFocused = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp.scale(s)))
                            }
                            ResourceActionButton(
                                onClick = { pendingDeleteUrl = entry.url },
                                icon = Icons.Default.Delete,
                                iconContentDescription = "删除",
                                onFocused = { deleteFocused = it }
                            )
                        }
                    }
                }
            }
        }
        if (showQrDialog) {
            MacCmsAdminQrDialog(
                viewModel = viewModel,
                onDismiss = { showQrDialog = false }
            )
        }
        pendingDeleteUrl?.let { url ->
            ConfirmDialog(
                title = "删除服务器",
                message = "确定删除「$url」吗？删除后将从已保存列表中移除。",
                onConfirm = {
                    viewModel.removeServer(url)
                    pendingDeleteUrl = null
                },
                onDismiss = { pendingDeleteUrl = null }
            )
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
    upFocusRequester: FocusRequester? = null,
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
                Key.DirectionUp -> {
                    keyboardController?.hide()
                    imeOpenedByConfirm = false
                    upFocusRequester?.tryRequestFocus() ?: false
                }
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
                .background(if (isFocused) PrimaryYellow else BackgroundDark)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        keyboardController?.hide()
                        imeOpenedByConfirm = false
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (isFocused) BackgroundDark else TextPrimary
            ),
            cursorBrush = SolidColor(if (isFocused) BackgroundDark else PrimaryYellow),
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
                            color = if (isFocused) BackgroundDark.copy(alpha = 0.55f) else TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
