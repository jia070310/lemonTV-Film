@file:OptIn(ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.data.preferences.PrivacyPreferences
import com.lemon.yingshi.tv.ui.DialogDimens
import com.lemon.yingshi.tv.ui.LocalCompactUiScale
import com.lemon.yingshi.tv.ui.scale
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SuccessGreen
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.theme.TvSelectableTokens
import com.lemon.yingshi.tv.ui.viewmodel.PrivacySettingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 500L

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

@Composable
private fun rememberLongPressDownKeyModifier(
    onShortDown: () -> Unit,
    onLongDown: () -> Unit,
    onShortUp: (() -> Unit)? = null
): Modifier {
    val scope = rememberCoroutineScope()
    var downJob by remember { mutableStateOf<Job?>(null) }
    var downLongTriggered by remember { mutableStateOf(false) }

    return Modifier.onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionDown -> when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount != 0) return@onPreviewKeyEvent true
                    downLongTriggered = false
                    downJob?.cancel()
                    downJob = scope.launch {
                        delay(LONG_PRESS_MS)
                        downLongTriggered = true
                        onLongDown()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    downJob?.cancel()
                    downJob = null
                    if (!downLongTriggered) {
                        onShortDown()
                    }
                    downLongTriggered = false
                    true
                }
                else -> false
            }
            Key.DirectionUp -> when (event.type) {
                KeyEventType.KeyDown -> {
                    if (onShortUp == null) return@onPreviewKeyEvent false
                    if (event.nativeKeyEvent.repeatCount != 0) return@onPreviewKeyEvent true
                    true
                }
                KeyEventType.KeyUp -> {
                    if (onShortUp == null) return@onPreviewKeyEvent false
                    onShortUp()
                    true
                }
                else -> false
            }
            else -> false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrivacySettingsSection(
    onShowKeywordsDialog: () -> Unit,
    onShowHideCategoriesDialog: () -> Unit,
    onShowClearPrivacyDialog: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val gap = 16.dp.scale(s)
    val viewModel: PrivacySettingsViewModel = hiltViewModel()
    val keywords by viewModel.filterKeywordsRaw.collectAsState()
    val hiddenIds by viewModel.hiddenTypeIds.collectAsState()

    val keywordsSubtitle = if (keywords.isBlank()) {
        PrivacyPreferences.KEYWORD_DELIMITER_HINT
    } else {
        "当前：${keywords.take(40)}${if (keywords.length > 40) "…" else ""}"
    }
    val hideSubtitle = if (hiddenIds.isEmpty()) {
        "关闭一级分类时，其下二级会一并隐藏"
    } else {
        "已隐藏 ${hiddenIds.size} 个分类"
    }

    Column {
        SettingCard(
            icon = Icons.Default.VisibilityOff,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFf472b6),
            title = "敏感关键词过滤",
            subtitle = keywordsSubtitle,
            onClick = onShowKeywordsDialog,
            modifier = Modifier.fillMaxWidth(),
            isFirstItem = true
        )

        Spacer(modifier = Modifier.height(gap))

        SettingCard(
            icon = Icons.Default.VisibilityOff,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFF60a5fa),
            title = "隐藏分类",
            subtitle = hideSubtitle,
            onClick = onShowHideCategoriesDialog,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(gap))

        SettingCard(
            icon = Icons.Default.VisibilityOff,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFef4444),
            title = "清空隐私设置",
            subtitle = "清除敏感关键词与手动隐藏分类",
            onClick = onShowClearPrivacyDialog,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrivacyKeywordsDialog(
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val saved by viewModel.filterKeywordsRaw.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    val inputFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        inputFocus.tryRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                onClick = {},
                colors = CardDefaults.colors(
                    containerColor = DialogUiTokens.ContainerColor,
                    focusedContainerColor = DialogUiTokens.ContainerColor
                ),
                modifier = Modifier
                    .width(DialogDimens.CardWidthStandard)
                    .padding(DialogDimens.CardPaddingOuter)
                    .border(
                        DialogUiTokens.BorderWidth,
                        DialogUiTokens.BorderColor,
                        RoundedCornerShape(DialogUiTokens.CornerRadius)
                    )
            ) {
                Column(modifier = Modifier.padding(DialogDimens.CardPaddingInner)) {
                    Text(
                        text = "敏感关键词过滤",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = PrivacyPreferences.KEYWORD_DELIMITER_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "分类名称包含任一关键词时，将在首页与片库中隐藏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacyKeywordTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "例如：伦理,福利,写真",
                        focusRequester = inputFocus,
                        downFocusRequester = saveFocus
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        var cancelFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextMuted,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier.onFocusChanged { cancelFocused = it.isFocused }
                        ) {
                            Text(
                                text = "取消",
                                color = if (cancelFocused) BackgroundDark else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        var saveFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.saveFilterKeywordsAwait(draft)
                                    onSuccess()
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextMuted,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .focusRequester(saveFocus)
                                .onFocusChanged { saveFocused = it.isFocused }
                        ) {
                            Text(
                                text = "保存",
                                color = if (saveFocused) BackgroundDark else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrivacyKeywordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
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
    val inputShape = RoundedCornerShape(8.dp)

    fun hideImeAndMoveDown(): Boolean {
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
            hideImeAndMoveDown()
        }
    }

    Box(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter, Key.DirectionCenter -> onConfirmWhileFocused()
                Key.DirectionDown, Key.Tab -> hideImeAndMoveDown()
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
                .height(48.dp)
                .clip(inputShape)
                .background(BackgroundDark)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
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
            keyboardActions = KeyboardActions(onDone = { hideImeAndMoveDown() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrivacyHideCategoriesDialog(
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var items by remember { mutableStateOf<List<PrivacySettingsViewModel.PrivacyHideItem>>(emptyList()) }
    var editableHidden by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val cancelFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val rowFocusRequesters = remember(items.size) {
        List(items.size) { FocusRequester() }
    }

    fun jumpToSave() {
        scope.launch {
            scrollState.scrollTo(scrollState.maxValue)
            delay(120)
            if (!saveFocus.tryRequestFocus()) {
                delay(80)
                saveFocus.tryRequestFocus()
            }
        }
    }

    fun jumpToRow(index: Int) {
        val target = rowFocusRequesters.getOrNull(index) ?: return
        scope.launch {
            if (index == 0) {
                scrollState.scrollTo(0)
            } else if (index >= items.lastIndex) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            delay(50)
            target.tryRequestFocus()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        viewModel.loadHideCandidates()
            .onSuccess { rows ->
                items = rows
                editableHidden = rows.filter { it.hidden }.map { it.typeId }.toSet()
                loadError = null
            }
            .onFailure { error ->
                items = emptyList()
                loadError = error.message ?: "加载分类失败"
            }
        isLoading = false
    }

    LaunchedEffect(items.size, isLoading) {
        if (!isLoading && items.isNotEmpty()) {
            delay(120)
            rowFocusRequesters.firstOrNull()?.tryRequestFocus()
        } else if (!isLoading) {
            cancelFocus.tryRequestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                onClick = {},
                colors = CardDefaults.colors(
                    containerColor = DialogUiTokens.ContainerColor,
                    focusedContainerColor = DialogUiTokens.ContainerColor
                ),
                modifier = Modifier
                    .width(DialogDimens.CardWidthStandard)
                    .padding(DialogDimens.CardPaddingOuter)
                    .border(
                        DialogUiTokens.BorderWidth,
                        DialogUiTokens.BorderColor,
                        RoundedCornerShape(DialogUiTokens.CornerRadius)
                    )
            ) {
                Column(modifier = Modifier.padding(DialogDimens.CardPaddingInner)) {
                    Text(
                        text = "隐藏分类",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开关关闭表示隐藏；关闭一级时，其下二级会一并关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        isLoading -> {
                            Text(
                                text = "正在加载分类…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        loadError != null -> {
                            Text(
                                text = loadError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFef4444)
                            )
                        }
                        items.isEmpty() -> {
                            Text(
                                text = "暂无分类，请先配置服务器并测试连接",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                items.forEachIndexed { index, item ->
                                    val shown = item.typeId !in editableHidden
                                    PrivacyHideRow(
                                        title = item.displayName,
                                        shown = shown,
                                        focusRequester = rowFocusRequesters[index],
                                        onShortDown = {
                                            if (index < items.lastIndex) {
                                                jumpToRow(index + 1)
                                            } else {
                                                cancelFocus.tryRequestFocus()
                                            }
                                        },
                                        onLongDown = { jumpToSave() },
                                        onShortUp = {
                                            if (index > 0) {
                                                jumpToRow(index - 1)
                                            }
                                        },
                                        onToggle = {
                                            editableHidden = PrivacySettingsViewModel.toggleHidden(
                                                items = items,
                                                typeId = item.typeId,
                                                hide = shown,
                                                currentHidden = editableHidden
                                            )
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        var cancelFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextMuted,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .focusRequester(cancelFocus)
                                .onFocusChanged { cancelFocused = it.isFocused }
                                .focusProperties { right = saveFocus }
                        ) {
                            Text(
                                text = "取消",
                                color = if (cancelFocused) BackgroundDark else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        var saveFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.saveHiddenTypeIdsAwait(editableHidden)
                                    onSuccess()
                                }
                            },
                            enabled = !isLoading && loadError == null,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextMuted,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = BackgroundDark
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .focusRequester(saveFocus)
                                .onFocusChanged { saveFocused = it.isFocused }
                        ) {
                            Text(
                                text = "保存",
                                color = if (saveFocused) BackgroundDark else TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "长按 ↓：直达「保存」｜点按 ↓↑：逐项移动｜末项↓到取消",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryYellow.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrivacyHideRow(
    title: String,
    shown: Boolean,
    focusRequester: FocusRequester,
    onShortDown: () -> Unit,
    onLongDown: () -> Unit,
    onShortUp: () -> Unit,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyNavigation = rememberLongPressDownKeyModifier(
        onShortDown = onShortDown,
        onLongDown = onLongDown,
        onShortUp = onShortUp
    )

    Button(
        onClick = onToggle,
        colors = ButtonDefaults.colors(
            containerColor = if (shown) SurfaceDark else SurfaceDark.copy(alpha = 0.7f),
            focusedContainerColor = TvSelectableTokens.focusedContainerColor,
            contentColor = TvSelectableTokens.selectedContentColor,
            focusedContentColor = TvSelectableTokens.focusedContentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(keyNavigation)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) BackgroundDark else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = shown,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isFocused && shown) {
                        Color(0xFFF59E0B)
                    } else if (shown) {
                        SuccessGreen
                    } else {
                        Color(0xFF444444)
                    },
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF444444)
                )
            )
        }
    }
}
