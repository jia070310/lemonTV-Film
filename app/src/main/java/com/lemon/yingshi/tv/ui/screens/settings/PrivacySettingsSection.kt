@file:OptIn(ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
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
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
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
private fun PrivacyDialogFrame(
    fillHeight: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val cardMaxHeight = minOf(DialogDimens.CardHeightSort, maxHeight * 0.9f)
        val shape = RoundedCornerShape(DialogUiTokens.CornerRadius)
        Box(
            modifier = Modifier
                .width(DialogDimens.CardWidthStandard)
                .then(
                    if (fillHeight) Modifier.height(cardMaxHeight) else Modifier
                        .wrapContentHeight()
                        .heightIn(max = cardMaxHeight)
                )
                .padding(DialogDimens.CardPaddingOuter)
                .background(DialogUiTokens.ContainerColor, shape)
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, shape)
        ) {
            Column(
                modifier = Modifier
                    .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
                    .padding(DialogDimens.CardPaddingInner),
                content = content
            )
        }
    }
}

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

    Column {
        SettingCard(
            icon = Icons.Default.VisibilityOff,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFf472b6),
            title = "敏感关键词过滤",
            subtitle = "打开后先选择服务器，再设置该源的关键词",
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
            subtitle = "打开后先选择服务器，再按该源目录隐藏分类",
            onClick = onShowHideCategoriesDialog,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(gap))

        SettingCard(
            icon = Icons.Default.VisibilityOff,
            iconBackgroundColor = Color.White.copy(alpha = 0.4f),
            iconTint = Color(0xFFef4444),
            title = "清空隐私设置",
            subtitle = "清除当前播放源已保存的关键词与隐藏分类",
            onClick = onShowClearPrivacyDialog,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun serverPickerLabel(entry: com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry): String =
    entry.name.ifBlank { entry.url }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrivacyDialogServerPicker(
    servers: List<com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry>,
    selectedUrl: String,
    activeUrl: String,
    firstFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onSelect: (String) -> Unit
) {
    val s = LocalCompactUiScale.current
    if (servers.isEmpty()) {
        Text(
            text = "请先在资源管理中保存服务器",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var triggerFocused by remember { mutableStateOf(false) }
    val itemFocusRequesters = remember(servers.size) { List(servers.size) { FocusRequester() } }
    val selectedEntry = servers.find { it.url == selectedUrl } ?: servers.first()
    val triggerShape = RoundedCornerShape(10.dp.scale(s))
    val menuShape = RoundedCornerShape(12.dp.scale(s))
    val menuScroll = rememberScrollState()

    LaunchedEffect(expanded, selectedUrl) {
        if (!expanded) return@LaunchedEffect
        delay(48)
        val idx = servers.indexOfFirst { it.url == selectedUrl }.coerceAtLeast(0)
        itemFocusRequesters.getOrNull(idx)?.tryRequestFocus()
    }

    fun collapseAndFocusTrigger() {
        expanded = false
        firstFocusRequester.tryRequestFocus()
    }

    Column {
        Text(
            text = "选择服务器",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp.scale(s)))
        val density = LocalDensity.current
        val menuOffsetY = with(density) { 52.dp.scale(s).roundToPx() }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val menuWidth = maxWidth
            Card(
                onClick = { expanded = !expanded },
                colors = CardDefaults.colors(
                    containerColor = SurfaceVariant,
                    focusedContainerColor = PrimaryYellow
                ),
                scale = CardDefaults.scale(scale = 1f, focusedScale = 1.02f, pressedScale = 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp.scale(s))
                    .focusRequester(firstFocusRequester)
                    .onFocusChanged { triggerFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (expanded) {
                                    true
                                } else {
                                    downFocusRequester.tryRequestFocus()
                                }
                            }
                            else -> false
                        }
                    }
                    .then(
                        if (!triggerFocused) {
                            Modifier.border(1.dp.scale(s), PrimaryYellow.copy(alpha = 0.45f), triggerShape)
                        } else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp.scale(s)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = serverPickerLabel(selectedEntry),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (triggerFocused) BackgroundDark else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedEntry.url == activeUrl) {
                        Text(
                            text = "播放中",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (triggerFocused) BackgroundDark.copy(alpha = 0.75f) else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp.scale(s)))
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = if (triggerFocused) BackgroundDark else TextPrimary,
                        modifier = Modifier.size(22.dp.scale(s))
                    )
                }
            }

            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, menuOffsetY),
                    onDismissRequest = { collapseAndFocusTrigger() },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .width(menuWidth)
                            .shadow(10.dp.scale(s), menuShape, ambientColor = Color.Black.copy(alpha = 0.5f))
                            .clip(menuShape)
                            .background(Color(0xFF2A2A2A))
                            .focusProperties { exit = { FocusRequester.Cancel } }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp.scale(s))
                                .verticalScroll(menuScroll)
                                .padding(horizontal = 8.dp.scale(s), vertical = 6.dp.scale(s))
                                .focusGroup(),
                            verticalArrangement = Arrangement.spacedBy(4.dp.scale(s))
                        ) {
                            servers.forEachIndexed { index, entry ->
                                val selected = entry.url == selectedUrl
                                var itemFocused by remember { mutableStateOf(false) }
                                val prev = itemFocusRequesters.getOrNull(index - 1)
                                val next = itemFocusRequesters.getOrNull(index + 1)
                                Card(
                                    onClick = {
                                        onSelect(entry.url)
                                        collapseAndFocusTrigger()
                                    },
                                    colors = CardDefaults.colors(
                                        containerColor = if (selected) SurfaceVariant else Color.Transparent,
                                        focusedContainerColor = PrimaryYellow
                                    ),
                                    scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp.scale(s))
                                        .focusRequester(itemFocusRequesters[index])
                                        .onFocusChanged { itemFocused = it.isFocused }
                                        .focusProperties {
                                            up = prev ?: FocusRequester.Cancel
                                            down = next ?: FocusRequester.Cancel
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp.scale(s)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = serverPickerLabel(entry),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (itemFocused) BackgroundDark else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (entry.url == activeUrl) {
                                            Text(
                                                text = "播放中",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (itemFocused) {
                                                    BackgroundDark.copy(alpha = 0.75f)
                                                } else {
                                                    TextSecondary
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrivacyKeywordsDialog(
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val servers by viewModel.serverList.collectAsState()
    val activeServer by viewModel.activeServerUrl.collectAsState()
    val defaultUrl = activeServer.ifBlank { servers.firstOrNull()?.url.orEmpty() }
    var selectedUrl by remember(defaultUrl) { mutableStateOf(defaultUrl) }
    var draft by remember { mutableStateOf("") }
    val firstServerFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedUrl) {
        draft = if (selectedUrl.isBlank()) "" else viewModel.keywordsFor(selectedUrl)
    }

    LaunchedEffect(Unit) {
        if (servers.isNotEmpty()) firstServerFocus.tryRequestFocus() else inputFocus.tryRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        PrivacyDialogFrame(fillHeight = false, onBack = onDismiss) {
                    Text(
                        text = "敏感关键词过滤",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "先选择服务器，再设置该源的关键词。自动切换后会启用对应规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PrivacyDialogServerPicker(
                        servers = servers,
                        selectedUrl = selectedUrl,
                        activeUrl = activeServer,
                        firstFocusRequester = firstServerFocus,
                        downFocusRequester = inputFocus,
                        onSelect = { selectedUrl = it }
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
                        upFocusRequester = firstServerFocus,
                        downFocusRequester = cancelFocus
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                if (event.key == Key.DirectionUp) {
                                    inputFocus.tryRequestFocus()
                                } else {
                                    false
                                }
                            },
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
                                    if (selectedUrl.isBlank()) return@launch
                                    viewModel.saveFilterKeywordsAwait(selectedUrl, draft)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrivacyKeywordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
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
                Key.DirectionUp -> {
                    keyboardController?.hide()
                    imeOpenedByConfirm = false
                    upFocusRequester.tryRequestFocus()
                }
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
    val servers by viewModel.serverList.collectAsState()
    val activeServer by viewModel.activeServerUrl.collectAsState()
    val defaultUrl = activeServer.ifBlank { servers.firstOrNull()?.url.orEmpty() }
    var selectedUrl by remember(defaultUrl) { mutableStateOf(defaultUrl) }
    var items by remember { mutableStateOf<List<PrivacySettingsViewModel.PrivacyHideItem>>(emptyList()) }
    var editableHidden by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val firstServerFocus = remember { FocusRequester() }
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

    LaunchedEffect(selectedUrl) {
        if (selectedUrl.isBlank()) {
            items = emptyList()
            loadError = "请先选择要设置的服务器"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        viewModel.loadHideCandidates(selectedUrl)
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
        if (servers.isNotEmpty()) {
            delay(80)
            firstServerFocus.tryRequestFocus()
        } else if (!isLoading && items.isNotEmpty()) {
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
        PrivacyDialogFrame(fillHeight = true, onBack = onDismiss) {
            Text(
                text = "隐藏分类",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "先选择服务器，再按该源目录隐藏分类。自动切换后会启用对应规则。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            PrivacyDialogServerPicker(
                servers = servers,
                selectedUrl = selectedUrl,
                activeUrl = activeServer,
                firstFocusRequester = firstServerFocus,
                downFocusRequester = rowFocusRequesters.firstOrNull() ?: cancelFocus,
                onSelect = { selectedUrl = it }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "开关关闭表示隐藏；关闭一级时，其下二级会一并关闭",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                                .fillMaxSize()
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
                                        } else {
                                            firstServerFocus.tryRequestFocus()
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                        val lastRow = rowFocusRequesters.lastOrNull()
                        if (lastRow != null) lastRow.tryRequestFocus() else firstServerFocus.tryRequestFocus()
                    },
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
                            viewModel.saveHiddenTypeIdsAwait(selectedUrl, editableHidden)
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
