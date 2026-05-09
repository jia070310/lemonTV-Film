package com.lomen.tv.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lomen.tv.domain.model.ResourceLibrary
import com.lomen.tv.ui.theme.BackgroundDark
import com.lomen.tv.ui.theme.GlassBackground
import com.lomen.tv.ui.theme.PrimaryYellow
import com.lomen.tv.ui.theme.SurfaceDark
import com.lomen.tv.ui.theme.TextMuted
import com.lomen.tv.ui.theme.TextPrimary
import com.lomen.tv.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResourceLibraryDialog(
    libraries: List<ResourceLibrary>,
    currentLibraryId: String?,
    onDismiss: () -> Unit,
    onLibrarySelected: (ResourceLibrary) -> Unit,
    onAddLibrary: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                onClick = {},
                colors = CardDefaults.colors(
                    containerColor = SurfaceDark
                ),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "资源库",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (libraries.isEmpty()) {
                        // 空状态
                        EmptyLibraryView(onAddLibrary = onAddLibrary)
                    } else {
                        // 资源库列表
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(libraries) { library ->
                                LibraryItem(
                                    library = library,
                                    isSelected = library.id == currentLibraryId,
                                    onClick = { onLibrarySelected(library) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 添加按钮
                        Button(
                            onClick = onAddLibrary,
                            colors = ButtonDefaults.colors(
                                containerColor = PrimaryYellow,
                                contentColor = BackgroundDark
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "添加资源库",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 8.dp)
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
private fun EmptyLibraryView(
    onAddLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(TextMuted.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "暂无资源库",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "添加WebDAV网盘或网盘",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddLibrary,
            colors = ButtonDefaults.colors(
                containerColor = PrimaryYellow,
                contentColor = BackgroundDark
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "添加资源库",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryItem(
    library: ResourceLibrary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) PrimaryYellow.copy(alpha = 0.2f) else SurfaceDark,
            focusedContainerColor = PrimaryYellow.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (library.type) {
                            ResourceLibrary.LibraryType.WEBDAV -> Color(0xFF3b82f6).copy(alpha = 0.2f)
                            ResourceLibrary.LibraryType.QUARK -> Color(0xFF10b981).copy(alpha = 0.2f)
                            ResourceLibrary.LibraryType.GUANGYA -> Color(0xFFf59e0b).copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (library.type) {
                        ResourceLibrary.LibraryType.WEBDAV -> Icons.Default.Storage
                        ResourceLibrary.LibraryType.QUARK -> Icons.Default.Cloud
                        ResourceLibrary.LibraryType.GUANGYA -> Icons.Default.Cloud
                    },
                    contentDescription = null,
                    tint = when (library.type) {
                        ResourceLibrary.LibraryType.WEBDAV -> Color(0xFF60a5fa)
                        ResourceLibrary.LibraryType.QUARK -> Color(0xFF34d399)
                        ResourceLibrary.LibraryType.GUANGYA -> Color(0xFFfbbf24)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = library.getDisplayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (library.type == ResourceLibrary.LibraryType.GUANGYA) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val mode = library.guangyaLoginMode ?: ResourceLibrary.GuangyaLoginMode.QR
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mode == ResourceLibrary.GuangyaLoginMode.QR) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "二维码登录",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "手机号登录",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "当前选中",
                    tint = if (isFocused) BackgroundDark else PrimaryYellow,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
