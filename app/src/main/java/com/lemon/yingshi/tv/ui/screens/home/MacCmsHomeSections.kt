package com.lemon.yingshi.tv.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lemon.yingshi.tv.data.remote.model.MacCmsVodItem
import com.lemon.yingshi.tv.domain.model.mapMacCmsTypeName
import com.lemon.yingshi.tv.ui.components.MediaCardFocusPlayIcon
import com.lemon.yingshi.tv.ui.theme.BackgroundDark
import com.lemon.yingshi.tv.ui.theme.PrimaryYellow
import com.lemon.yingshi.tv.ui.theme.SurfaceDark
import com.lemon.yingshi.tv.ui.theme.TextMuted
import com.lemon.yingshi.tv.ui.theme.TextPrimary

const val HOME_MACCMS_MAX_ITEMS = 10

fun accentColorForCategory(typeName: String): Color {
    return when (mapMacCmsTypeName(typeName)) {
        com.lemon.yingshi.tv.domain.model.MediaType.TV_SHOW -> Color(0xFF10b981)
        com.lemon.yingshi.tv.domain.model.MediaType.ANIME -> Color(0xFFf59e0b)
        com.lemon.yingshi.tv.domain.model.MediaType.MOVIE -> PrimaryYellow
        com.lemon.yingshi.tv.domain.model.MediaType.VARIETY -> Color(0xFFec4899)
        com.lemon.yingshi.tv.domain.model.MediaType.CONCERT -> Color(0xFFa855f7)
        com.lemon.yingshi.tv.domain.model.MediaType.DOCUMENTARY -> Color(0xFF3b82f6)
        else -> Color(0xFF6b7280)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MacCmsVodRow(
    items: List<MacCmsVodItem>,
    onItemClick: (MacCmsVodItem) -> Unit,
    showMore: Boolean,
    moreLabel: String,
    onMoreClick: () -> Unit,
    currentRowFocusRequesters: List<FocusRequester>?,
    headerFocusRequesters: List<FocusRequester>,
    isFirstContentRow: Boolean,
    onFocusedColumnChanged: (Int) -> Unit,
    showTopVersionBadge: Boolean,
    topVersionBadgeFocusRequester: FocusRequester?
) {
    val displayCount = items.size.coerceAtMost(HOME_MACCMS_MAX_ITEMS)
    val totalItems = displayCount + if (showMore) 1 else 0

    TvLazyRow(
        contentPadding = PaddingValues(start = 48.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.2f)
    ) {
        items(count = totalItems, key = { index ->
            if (index < displayCount) "cms_${items[index].vodId}" else "more_$moreLabel"
        }) { index ->
            if (index < displayCount) {
                val vod = items[index]
                MacCmsVodCard(
                    vod = vod,
                    onClick = { onItemClick(vod) },
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters?.getOrNull(index)?.let {
                                Modifier.focusRequester(it)
                            } ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up = if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                    topVersionBadgeFocusRequester
                                } else {
                                    headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                }
                            }
                        }
                )
            } else {
                MoreMediaCard(
                    label = moreLabel,
                    onClick = onMoreClick,
                    modifier = Modifier
                        .then(
                            currentRowFocusRequesters?.getOrNull(index)?.let {
                                Modifier.focusRequester(it)
                            } ?: Modifier
                        )
                        .onFocusChanged { if (it.isFocused) onFocusedColumnChanged(index.coerceAtMost(2)) }
                        .focusProperties {
                            if (isFirstContentRow) {
                                up = if (showTopVersionBadge && topVersionBadgeFocusRequester != null) {
                                    topVersionBadgeFocusRequester
                                } else {
                                    headerFocusRequesters[index.coerceAtMost(headerFocusRequesters.lastIndex)]
                                }
                            }
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MacCmsVodCard(
    vod: MacCmsVodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(160.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier
                .width(160.dp)
                .height(240.dp)
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = SurfaceDark,
                focusedContainerColor = PrimaryYellow
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                )
            )
        ) {
            Box {
                if (!vod.vodPic.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(vod.vodPic)
                            .crossfade(false)
                            .build(),
                        contentDescription = vod.vodName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (!isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    }
                }
                MediaCardFocusPlayIcon(isFocused = isFocused, iconSize = 48.dp)
                if (!vod.vodRemarks.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryYellow.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = vod.vodRemarks,
                            style = MaterialTheme.typography.labelSmall,
                            color = BackgroundDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = vod.vodName,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
