package com.lomen.tv.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lomen.tv.data.preferences.LiveSettingsPreferences
import com.lomen.tv.domain.model.MediaType
import com.lomen.tv.ui.player.PlayerActivity
import com.lomen.tv.ui.screens.category.CategoryScreen
import com.lomen.tv.ui.screens.detail.DetailScreen
import com.lomen.tv.ui.screens.home.HomeScreen
import com.lomen.tv.ui.screens.home.HomeViewModel
import com.lomen.tv.ui.screens.home.ResourceLibraryScreen
import com.lomen.tv.ui.screens.library.LibraryScreen
import com.lomen.tv.ui.screens.live.LiveScreen
import com.lomen.tv.ui.screens.recentwatching.RecentWatchingScreen
import com.lomen.tv.ui.screens.search.SearchScreen
import com.lomen.tv.ui.screens.settings.SettingsScreen
import com.lomen.tv.ui.viewmodel.MediaSyncViewModel
import com.lomen.tv.ui.viewmodel.ResourceLibraryViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.lomen.tv.ui.components.WhitePillToast
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object ResourceLibrary : Screen("resource_library")
    data object Search : Screen("search")
    data object Settings : Screen("settings") {
        fun createRoute(libraryId: String? = null) = 
            if (libraryId != null) "settings?libraryId=$libraryId" else "settings"
    }
    data object Detail : Screen("detail/{mediaId}") {
        fun createRoute(mediaId: String) = "detail/$mediaId"
    }
    data object Player : Screen("player/{mediaId}/{episodeId}") {
        fun createRoute(mediaId: String, episodeId: String? = null) = 
            "player/$mediaId/${episodeId ?: "null"}"
    }
    data object Category : Screen("category/{mediaType}") {
        fun createRoute(mediaType: MediaType) = "category/${mediaType.name}"
    }
    data object RecentWatching : Screen("recent_watching")
    data object Live : Screen("live")
}

@Composable
fun LomenTVNavigation(
    navController: NavHostController = rememberNavController()
) {
    // 在NavHost级别创建共享的ViewModel
    val sharedResourceLibraryViewModel: ResourceLibraryViewModel = hiltViewModel()
    val sharedMediaSyncViewModel: MediaSyncViewModel = hiltViewModel()
    
    // 获取LiveSettingsPreferences
    val context = LocalContext.current
    val liveSettingsPreferences = remember { LiveSettingsPreferences(context) }
    
    // 检查是否自动进入直播
    val autoEnterLive by liveSettingsPreferences.autoEnterLive.collectAsState(initial = false)
    
    // 在NavHost级别维护自动导航状态，确保整个APP会话中只自动导航一次
    // 使用 remember 确保APP进程重启后状态重置
    var hasAutoNavigatedToLive by remember { mutableStateOf(false) }
    
    val syncState by sharedMediaSyncViewModel.syncState.collectAsState()
    var globalToastMessage by remember { mutableStateOf<String?>(null) }
    var toastNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(syncState) {
        if (syncState is MediaSyncViewModel.SyncState.Completed) {
            // 立刻消费 Completed，避免后续进入某些页面才“补弹”
            sharedMediaSyncViewModel.resetState()
            toastNonce++
        }
    }

    LaunchedEffect(toastNonce) {
        if (toastNonce == 0) return@LaunchedEffect
        globalToastMessage = "刮削完成"
        delay(2000)
        // 只有当前这次 nonce 仍是最新才清理，避免连续完成时被前一次提前清掉
        globalToastMessage = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
            val homeViewModel: com.lomen.tv.ui.screens.home.HomeViewModel = hiltViewModel()
            
            // 自动进入直播 - 只在首次显示Home且设置开启时跳转
            // 监听 autoEnterLive 变化，当值为 true 且还没导航过时自动跳转
            LaunchedEffect(autoEnterLive, hasAutoNavigatedToLive) {
                if (autoEnterLive && !hasAutoNavigatedToLive) {
                    hasAutoNavigatedToLive = true
                    navController.navigate(Screen.Live.route)
                }
            }
            
            HomeScreen(
                onNavigateToDetail = { mediaId ->
                    navController.navigate(Screen.Detail.createRoute(mediaId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.ResourceLibrary.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCategory = { mediaType ->
                    navController.navigate(Screen.Category.createRoute(mediaType))
                },
                onNavigateToRecentWatching = {
                    navController.navigate(Screen.RecentWatching.route)
                },
                onNavigateToLive = {
                    navController.navigate(Screen.Live.route)
                },
                onPlayFromHistory = { historyItem ->
                    // 在协程中获取播放信息并启动播放器
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val playbackInfo = homeViewModel.getPlaybackInfo(historyItem.mediaId, historyItem.episodeId)
                        if (playbackInfo != null && playbackInfo.videoPath != null) {
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_VIDEO_URL, playbackInfo.videoPath)
                                putExtra(PlayerActivity.EXTRA_TITLE, playbackInfo.title)
                                putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, playbackInfo.episodeTitle)
                                putExtra(PlayerActivity.EXTRA_MEDIA_ID, playbackInfo.mediaId)
                                putExtra(PlayerActivity.EXTRA_EPISODE_ID, playbackInfo.episodeId)
                                putExtra(PlayerActivity.EXTRA_START_POSITION, playbackInfo.startPosition)
                            }
                            context.startActivity(intent)
                        }
                    }
                },
                resourceLibraryViewModel = sharedResourceLibraryViewModel,
                mediaSyncViewModel = sharedMediaSyncViewModel
            )
            }
        
            composable(Screen.Library.route) {
            val libraryContext = LocalContext.current
            LibraryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { mediaId ->
                    navController.navigate(Screen.Detail.createRoute(mediaId))
                }
            )
        }

            composable(Screen.ResourceLibrary.route) {
            val libraries = sharedResourceLibraryViewModel.resourceLibraries.collectAsState()
            val currentLibraryId = sharedResourceLibraryViewModel.currentLibraryId.collectAsState()
            ResourceLibraryScreen(
                libraries = libraries.value,
                currentLibraryId = currentLibraryId.value,
                syncState = sharedMediaSyncViewModel.syncState.collectAsState().value,
                syncProgress = sharedMediaSyncViewModel.syncProgress.collectAsState().value,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLibrarySelected = { library ->
                    runCatching {
                        sharedResourceLibraryViewModel.setCurrentLibrary(library.id)
                        // 触发增量同步（只刮削新增和修改的文件）
                        sharedMediaSyncViewModel.syncLibraryIncremental(library)
                    }.onFailure { e ->
                        android.util.Log.e("LomenTVNavigation", "选择资源库并触发同步失败", e)
                    }
                },
                onSyncComplete = {
                    // 刮削完成后不自动返回，停留在资源库页面
                },
                onAddLibrary = {
                    navController.navigate(Screen.Settings.route)
                },
                onDeleteLibrary = { library ->
                    sharedResourceLibraryViewModel.removeLibrary(library.id)
                },
                onEditLibrary = { library ->
                    // 导航到设置页面进行编辑
                    navController.navigate(Screen.Settings.createRoute(library.id))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                resourceLibraryViewModel = sharedResourceLibraryViewModel
            )
        }
        
            composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { mediaId ->
                    navController.navigate(Screen.Detail.createRoute(mediaId))
                }
            )
        }
        
            composable(
            route = "settings?libraryId={libraryId}",
            arguments = listOf(
                navArgument("libraryId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId")
            val libraryToEdit = libraryId?.let { id ->
                sharedResourceLibraryViewModel.resourceLibraries.collectAsState().value.find { it.id == id }
            }
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                sharedResourceLibraryViewModel = sharedResourceLibraryViewModel,
                sharedMediaSyncViewModel = sharedMediaSyncViewModel,
                libraryToEdit = libraryToEdit
            )
        }
        
            composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            DetailScreen(
                mediaId = mediaId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayClick = { videoUrl, title, episodeTitle, mediaId, episodeId, startPosition ->
                    // 启动播放器 Activity
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl)
                        putExtra(PlayerActivity.EXTRA_TITLE, title)
                        putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episodeTitle)
                        putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId)
                        putExtra(PlayerActivity.EXTRA_EPISODE_ID, episodeId)
                        putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition)
                    }
                    context.startActivity(intent)
                }
            )
        }
        
            composable(
            route = Screen.Category.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaTypeName = backStackEntry.arguments?.getString("mediaType") ?: ""
            val mediaType = try {
                MediaType.valueOf(mediaTypeName)
            } catch (e: IllegalArgumentException) {
                MediaType.MOVIE // 默认值
            }
            CategoryScreen(
                mediaType = mediaType,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { mediaId ->
                    navController.navigate(Screen.Detail.createRoute(mediaId))
                },
                resourceLibraryViewModel = sharedResourceLibraryViewModel
            )
        }

            composable(Screen.RecentWatching.route) {
            val recentContext = LocalContext.current
            val homeViewModel: HomeViewModel = hiltViewModel()
            RecentWatchingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayFromHistory = { historyItem ->
                    // 在协程中获取播放信息并启动播放器
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val playbackInfo = homeViewModel.getPlaybackInfo(historyItem.mediaId, historyItem.episodeId)
                        if (playbackInfo != null && playbackInfo.videoPath != null) {
                            val intent = Intent(recentContext, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_VIDEO_URL, playbackInfo.videoPath)
                                putExtra(PlayerActivity.EXTRA_TITLE, playbackInfo.title)
                                putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, playbackInfo.episodeTitle)
                                putExtra(PlayerActivity.EXTRA_MEDIA_ID, playbackInfo.mediaId)
                                putExtra(PlayerActivity.EXTRA_EPISODE_ID, playbackInfo.episodeId)
                                putExtra(PlayerActivity.EXTRA_START_POSITION, playbackInfo.startPosition)
                            }
                            recentContext.startActivity(intent)
                        }
                    }
                }
            )
        }

            composable(Screen.Live.route) {
            LiveScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        }

        if (globalToastMessage != null) {
            WhitePillToast(
                message = globalToastMessage!!,
                icon = Icons.Default.CheckCircle
            )
        }
    }
}
