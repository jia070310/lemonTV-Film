package com.lemon.yingshi.tv.ui

import androidx.compose.ui.unit.dp

/**
 * 全局弹窗尺寸：统一略小于原 TV 默认，减少占屏与留白。
 */
object DialogDimens {
    val CardWidthStandard = 480.dp
    val CardPaddingOuter = 24.dp
    val CardPaddingInner = 24.dp

    val CardWidthSort = 560.dp
    val CardHeightSort = 480.dp

    val CardWidthClassification = 576.dp
    val CardHeightClassification = 560.dp

    /** 历史直播源 / UA / 节目单 等 AlertDialog */
    val CardWidthHistoryList = 480.dp

    /** 二维码类（TMDB / WebDAV / 网页配置） */
    val QrCardWidth = 280.dp
    val QrCardHeight = 310.dp
    val QrCardPadding = 16.dp
    val QrImageMaxHeight = 148.dp

    /** 添加成功等提示卡片 */
    val SuccessHintWidthMin = 220.dp
    val SuccessHintWidthMax = 340.dp

    /** WebDAV 编辑表单 */
    val WebDavFormWidthMin = 400.dp
    val WebDavFormWidthMax = 460.dp
    val WebDavFormHeightMax = 680.dp
    val WebDavBoxPadding = 24.dp

    // 版本更新对话框：发现尺寸异常后，统一调整为 380x350
    val VersionUpdateWidth = 380.dp
    val VersionUpdateHeightMin = 350.dp
    val VersionUpdateHeightMax = 350.dp

    val ErrorOverlayWidthMin = 240.dp
    val ErrorOverlayWidthMax = 300.dp

    /** 直播页「选择直播源」浮层 */
    val LiveSourcePanelSize = 280.dp
}
