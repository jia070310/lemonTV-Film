package com.lemon.yingshi.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.SurfaceVariant
import com.lemon.yingshi.tv.ui.theme.TextMuted

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodPosterImage(
    posterUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbUrl: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    showFocusPlayIcon: Boolean = false,
    isFocused: Boolean = false,
    iconSize: Dp = 48.dp,
    crossfade: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val imageUrl = posterUrl?.takeIf { it.isNotBlank() }
        ?: thumbUrl?.takeIf { it.isNotBlank() }

    Box(modifier = modifier) {
        VodPosterSkeleton(modifier = Modifier.fillMaxSize())

        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(crossfade)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                loading = { VodPosterSkeleton(modifier = Modifier.fillMaxSize()) },
                success = { SubcomposeAsyncImageContent() },
                error = {
                    if (!isFocused) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            )
        } else if (!isFocused) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        if (showFocusPlayIcon) {
            MediaCardFocusPlayIcon(isFocused = isFocused, iconSize = iconSize)
        }
        overlay()
    }
}

@Composable
fun VodPosterSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    SurfaceVariant,
                    SurfaceDark,
                    SurfaceVariant.copy(alpha = 0.85f)
                )
            )
        )
    )
}
