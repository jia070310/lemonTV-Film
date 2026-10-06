package com.lemon.yingshi.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lemon.yingshi.tv.ui.LocalCompactUiScale
import com.lemon.yingshi.tv.ui.scale
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.DialogUiTokens
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextPrimary
import com.lemon.yingshi.tv.ui.theme.TextSecondary
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MacCmsAdminQrDialog(
    viewModel: MacCmsConfigViewModel,
    onDismiss: () -> Unit
) {
    val s = LocalCompactUiScale.current
    val urls by viewModel.adminUrls.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val adminError by viewModel.adminError.collectAsState()
    val closeFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        viewModel.startAdminPortal()
        onDispose { viewModel.stopAdminPortal() }
    }
    LaunchedEffect(Unit) {
        closeFocus.tryRequestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp.scale(s))
                .clip(RoundedCornerShape(DialogUiTokens.CornerRadius.scale(s)))
                .background(DialogUiTokens.ContainerColor)
                .border(
                    DialogUiTokens.BorderWidth,
                    DialogUiTokens.BorderColor,
                    RoundedCornerShape(DialogUiTokens.CornerRadius.scale(s))
                )
                .padding(24.dp.scale(s)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "扫码管理资源服务器",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp.scale(s)))
            Text(
                text = "手机与盒子需在同一 Wi-Fi。也可在浏览器输入下方地址。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp.scale(s)))
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "资源管理二维码",
                    modifier = Modifier
                        .size(220.dp.scale(s))
                        .clip(RoundedCornerShape(12.dp.scale(s)))
                )
            }
            Spacer(modifier = Modifier.height(12.dp.scale(s)))
            if (!adminError.isNullOrBlank()) {
                Text(text = adminError!!, color = PrimaryYellow, style = MaterialTheme.typography.bodySmall)
            } else {
                urls.forEach { url ->
                    Text(
                        text = url,
                        color = PrimaryYellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp.scale(s)))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = SurfaceVariant,
                    focusedContainerColor = PrimaryYellow,
                    pressedContainerColor = PrimaryYellow,
                    contentColor = TextPrimary,
                    focusedContentColor = BackgroundDark,
                    pressedContentColor = BackgroundDark
                ),
                scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.04f),
                contentPadding = PaddingValues(
                    horizontal = 20.dp.scale(s),
                    vertical = 6.dp.scale(s)
                ),
                modifier = Modifier
                    .height(36.dp.scale(s))
                    .focusRequester(closeFocus)
                    .onFocusChanged { closeFocused = it.isFocused }
            ) {
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (closeFocused) BackgroundDark else TextPrimary
                )
            }
        }
    }
}

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)
