@file:OptIn(ExperimentalComposeUiApi::class)

package com.lemon.yingshi.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.data.preferences.MacCmsCategorySortPreferences
import com.lemon.yingshi.tv.data.repository.MacCmsRepository
import com.lemon.yingshi.tv.domain.model.MacCmsHomeSectionRef
import com.lemon.yingshi.tv.domain.model.MacCmsTaxonomy
import com.lemon.yingshi.tv.ui.DialogDimens
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SuccessGreen
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 500L

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)

private enum class RowControl {
    Toggle, MoveUp, MoveDown
}

private data class FocusTarget(
    val row: Int,
    val control: RowControl
)

private data class CategoryRowFocusRequesters(
    val toggle: FocusRequester = FocusRequester(),
    val moveUp: FocusRequester = FocusRequester(),
    val moveDown: FocusRequester = FocusRequester()
)

private fun swapSectionOrder(order: List<String>, fromIndex: Int, toIndex: Int): List<String> {
    if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
        return order
    }
    val list = order.toMutableList()
    val item = list.removeAt(fromIndex)
    list.add(toIndex, item)
    return list
}

@Composable
private fun rememberCategorySortKeyModifier(
    enableUpLongPress: Boolean,
    onShortDown: () -> Unit,
    onLongDown: () -> Unit,
    onShortUp: () -> Unit,
    onLongUp: () -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    var downJob by remember { mutableStateOf<Job?>(null) }
    var downLongTriggered by remember { mutableStateOf(false) }
    var upJob by remember { mutableStateOf<Job?>(null) }
    var upLongTriggered by remember { mutableStateOf(false) }

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
                    if (event.nativeKeyEvent.repeatCount != 0) return@onPreviewKeyEvent true
                    if (enableUpLongPress) {
                        upLongTriggered = false
                        upJob?.cancel()
                        upJob = scope.launch {
                            delay(LONG_PRESS_MS)
                            upLongTriggered = true
                            onLongUp()
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    upJob?.cancel()
                    upJob = null
                    if (!upLongTriggered) {
                        onShortUp()
                    }
                    upLongTriggered = false
                    true
                }
                else -> false
            }
            else -> false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MacCmsCategorySortDialog(
    sortPreferences: MacCmsCategorySortPreferences,
    macCmsRepository: MacCmsRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val savedOrder by sortPreferences.sectionOrder.collectAsState(initial = emptyList())
    val savedVisibleKeys by sortPreferences.visibleSectionKeys.collectAsState(initial = emptySet())
    val visibilityConfigured by sortPreferences.visibilityConfigured.collectAsState(initial = false)
    var taxonomy by remember { mutableStateOf<MacCmsTaxonomy?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editableOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var editableVisibleKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentFocus by remember { mutableStateOf<FocusTarget?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(editableOrder.size) {
        List(editableOrder.size) { CategoryRowFocusRequesters() }
    }

    fun requesterFor(target: FocusTarget): FocusRequester? {
        val row = rowFocusRequesters.getOrNull(target.row) ?: return null
        return when (target.control) {
            RowControl.Toggle -> row.toggle
            RowControl.MoveUp -> if (target.row > 0) row.moveUp else null
            RowControl.MoveDown -> if (target.row < editableOrder.lastIndex) row.moveDown else null
        }
    }

    fun moveFocusVertically(from: FocusTarget, down: Boolean): Boolean {
        val lastIndex = editableOrder.lastIndex
        if (lastIndex < 0) return false

        if (down) {
            if (from.row < lastIndex) {
                val next = FocusTarget(from.row + 1, from.control)
                return requesterFor(next)?.tryRequestFocus() == true
            }
            currentFocus = null
            return cancelFocusRequester.tryRequestFocus()
        }

        if (from.row > 0) {
            val prev = FocusTarget(from.row - 1, from.control)
            return requesterFor(prev)?.tryRequestFocus() == true
        }
        return false
    }

    fun jumpToSave() {
        scope.launch {
            scrollState.scrollTo(Int.MAX_VALUE)
            delay(120)
            if (!saveFocusRequester.tryRequestFocus()) {
                delay(80)
                saveFocusRequester.tryRequestFocus()
            }
        }
    }

    fun jumpToFirstRow() {
        scope.launch {
            scrollState.scrollTo(0)
            delay(50)
            currentFocus = FocusTarget(0, RowControl.Toggle)
            rowFocusRequesters.firstOrNull()?.toggle?.tryRequestFocus()
        }
    }

    fun jumpToLastRow() {
        val lastIndex = editableOrder.lastIndex
        if (lastIndex < 0) return
        scope.launch {
            scrollState.scrollTo(scrollState.maxValue)
            delay(50)
            currentFocus = FocusTarget(lastIndex, RowControl.Toggle)
            rowFocusRequesters[lastIndex].toggle.tryRequestFocus()
        }
    }

    val listKeyModifier = rememberCategorySortKeyModifier(
        enableUpLongPress = false,
        onShortDown = {
            currentFocus?.let { moveFocusVertically(it, down = true) }
        },
        onLongDown = { jumpToSave() },
        onShortUp = {
            currentFocus?.let { moveFocusVertically(it, down = false) }
        },
        onLongUp = {}
    )

    val footerKeyModifier = rememberCategorySortKeyModifier(
        enableUpLongPress = true,
        onShortDown = {},
        onLongDown = { jumpToSave() },
        onShortUp = { jumpToLastRow() },
        onLongUp = { jumpToFirstRow() }
    )

    LaunchedEffect(Unit) {
        runCatching {
            macCmsRepository.fetchTaxonomy(forceRefresh = true)
        }.onSuccess { loaded ->
            taxonomy = loaded
            loadError = null
        }.onFailure { error ->
            loadError = error.message ?: "加载分类失败"
        }
    }

    LaunchedEffect(savedOrder, savedVisibleKeys, visibilityConfigured, taxonomy) {
        val tax = taxonomy ?: return@LaunchedEffect
        editableOrder = tax.resolveSectionOrder(savedOrder)
        editableVisibleKeys = tax.resolveVisibleSectionKeys(
            savedVisible = savedVisibleKeys,
            visibilityConfigured = visibilityConfigured
        )
    }

    LaunchedEffect(editableOrder.size) {
        if (editableOrder.isNotEmpty()) {
            delay(150)
            currentFocus = FocusTarget(0, RowControl.Toggle)
            rowFocusRequesters.firstOrNull()?.toggle?.tryRequestFocus()
        }
    }

    val tax = taxonomy
    if (tax == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = loadError ?: "正在加载服务器分类…",
                color = TextSecondary
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(DialogDimens.CardWidthSort)
                .height(DialogDimens.CardHeightSort)
                .padding(DialogDimens.CardPaddingOuter)
                .background(DialogUiTokens.ContainerColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
                .border(DialogUiTokens.BorderWidth, DialogUiTokens.BorderColor, RoundedCornerShape(DialogUiTokens.CornerRadius))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DialogDimens.CardPaddingInner)
            ) {
                Text(
                    text = "首页分类设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开关：是否在首页显示该类目；↑↓按钮：调整排序位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    editableOrder.forEachIndexed { index, sectionKey ->
                        val sectionRef = tax.parseSectionKey(sectionKey)
                        val isVisible = sectionKey in editableVisibleKeys
                        val isSecondary = sectionRef is MacCmsHomeSectionRef.Secondary
                        val title = sectionRef?.displayName ?: sectionKey
                        val rowFocus = rowFocusRequesters.getOrNull(index) ?: return@forEachIndexed
                        val canMoveUp = index > 0
                        val canMoveDown = index < editableOrder.lastIndex
                        val isLastRow = index == editableOrder.lastIndex

                        CategorySortRow(
                            index = index,
                            title = title,
                            isSecondary = isSecondary,
                            isVisible = isVisible,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            toggleFocusRequester = rowFocus.toggle,
                            moveUpFocusRequester = rowFocus.moveUp,
                            moveDownFocusRequester = rowFocus.moveDown,
                            onToggleFocused = {
                                currentFocus = FocusTarget(index, RowControl.Toggle)
                            },
                            onMoveUpFocused = {
                                currentFocus = FocusTarget(index, RowControl.MoveUp)
                            },
                            onMoveDownFocused = {
                                currentFocus = FocusTarget(index, RowControl.MoveDown)
                            },
                            toggleFocusProperties = {
                                left = FocusRequester.Cancel
                                right = if (canMoveUp) rowFocus.moveUp else rowFocus.moveDown
                                if (isLastRow) down = cancelFocusRequester
                            },
                            moveUpFocusProperties = {
                                left = rowFocus.toggle
                                right = rowFocus.moveDown
                                if (isLastRow) down = cancelFocusRequester
                            },
                            moveDownFocusProperties = {
                                left = if (canMoveUp) rowFocus.moveUp else rowFocus.toggle
                                right = FocusRequester.Cancel
                                if (isLastRow) down = cancelFocusRequester
                            },
                            onToggleVisible = { checked ->
                                editableVisibleKeys = if (checked) {
                                    editableVisibleKeys + sectionKey
                                } else {
                                    editableVisibleKeys - sectionKey
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    editableOrder = swapSectionOrder(editableOrder, index, index - 1)
                                    currentFocus = FocusTarget(index - 1, RowControl.MoveUp)
                                    rowFocusRequesters.getOrNull(index - 1)?.moveUp?.tryRequestFocus()
                                }
                            },
                            onMoveDown = {
                                if (index < editableOrder.lastIndex) {
                                    editableOrder = swapSectionOrder(editableOrder, index, index + 1)
                                    currentFocus = FocusTarget(index + 1, RowControl.MoveDown)
                                    rowFocusRequesters.getOrNull(index + 1)?.moveDown?.tryRequestFocus()
                                }
                            },
                            verticalKeyNavigation = listKeyModifier
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "←→ 切换开关/排序 | 点按 ↓↑ 移动 | 末项↓到取消 →到保存 | 返回键退出",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = "长按 ↓：直达「保存」| 在取消/保存上长按 ↑：直达第一条",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryYellow.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            exit = { FocusRequester.Cancel }
                        },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var cancelFocused by remember { mutableStateOf(false) }
                    var saveFocused by remember { mutableStateOf(false) }
                    val lastRowMoveDown = rowFocusRequesters.lastOrNull()?.moveDown
                        ?: rowFocusRequesters.lastOrNull()?.toggle

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextMuted,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 96.dp, minHeight = 44.dp)
                            .focusRequester(cancelFocusRequester)
                            .then(footerKeyModifier)
                            .onFocusChanged {
                                cancelFocused = it.isFocused
                                if (it.isFocused) currentFocus = null
                            }
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = saveFocusRequester
                                up = lastRowMoveDown ?: FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "取消",
                            color = if (cancelFocused) BackgroundDark else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                sortPreferences.saveCategoryConfig(
                                    sectionOrder = editableOrder,
                                    visibleKeys = editableVisibleKeys
                                )
                                onSuccess()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = TextSecondary,
                            focusedContainerColor = PrimaryYellow,
                            focusedContentColor = BackgroundDark
                        ),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 96.dp, minHeight = 44.dp)
                            .focusRequester(saveFocusRequester)
                            .then(footerKeyModifier)
                            .onFocusChanged {
                                saveFocused = it.isFocused
                                if (it.isFocused) currentFocus = null
                            }
                            .focusProperties {
                                left = cancelFocusRequester
                                right = FocusRequester.Cancel
                                up = lastRowMoveDown ?: FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                    ) {
                        Text(
                            text = "保存",
                            color = if (saveFocused) BackgroundDark else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CategorySortRow(
    index: Int,
    title: String,
    isSecondary: Boolean,
    isVisible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    toggleFocusRequester: FocusRequester,
    moveUpFocusRequester: FocusRequester,
    moveDownFocusRequester: FocusRequester,
    onToggleFocused: () -> Unit,
    onMoveUpFocused: () -> Unit,
    onMoveDownFocused: () -> Unit,
    toggleFocusProperties: androidx.compose.ui.focus.FocusProperties.() -> Unit,
    moveUpFocusProperties: androidx.compose.ui.focus.FocusProperties.() -> Unit,
    moveDownFocusProperties: androidx.compose.ui.focus.FocusProperties.() -> Unit,
    onToggleVisible: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    verticalKeyNavigation: Modifier
) {
    var toggleFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. $title",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isVisible) TextPrimary else TextMuted
            )
            if (isSecondary) {
                Text(
                    text = "二级类目",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .border(
                    width = if (toggleFocused) 2.dp else 0.dp,
                    color = if (toggleFocused) PrimaryYellow else Color.Transparent,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(3.dp)
        ) {
            Switch(
                checked = isVisible,
                onCheckedChange = onToggleVisible,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = if (toggleFocused) BackgroundDark else PrimaryYellow,
                    checkedTrackColor = if (toggleFocused) PrimaryYellow else SuccessGreen,
                    uncheckedThumbColor = if (toggleFocused) BackgroundDark else TextMuted,
                    uncheckedTrackColor = if (toggleFocused) PrimaryYellow.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .focusRequester(toggleFocusRequester)
                    .then(verticalKeyNavigation)
                    .focusProperties(toggleFocusProperties)
                    .onFocusChanged {
                        toggleFocused = it.isFocused
                        if (it.isFocused) onToggleFocused()
                    }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (canMoveUp) {
            IconButton(
                onClick = onMoveUp,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(moveUpFocusRequester)
                    .then(verticalKeyNavigation)
                    .focusProperties(moveUpFocusProperties)
                    .onFocusChanged { if (it.isFocused) onMoveUpFocused() }
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        if (canMoveDown) {
            IconButton(
                onClick = onMoveDown,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary,
                    focusedContainerColor = PrimaryYellow,
                    focusedContentColor = BackgroundDark
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(moveDownFocusRequester)
                    .then(verticalKeyNavigation)
                    .focusProperties(moveDownFocusProperties)
                    .onFocusChanged { if (it.isFocused) onMoveDownFocused() }
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}
