package com.lemon.yingshi.tv.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lemon.yingshi.tv.ui.player.PlayerActivity
import com.lemon.yingshi.tv.ui.screens.detail.DetailScreen
import com.lemon.yingshi.tv.ui.screens.filter.FilterScreen
import com.lemon.yingshi.tv.ui.screens.home.HomeScreen
import com.lemon.yingshi.tv.ui.screens.home.HomeViewModel
import com.lemon.yingshi.tv.ui.screens.recentwatching.RecentWatchingScreen
import com.lemon.yingshi.tv.ui.screens.search.SearchScreen
import com.lemon.yingshi.tv.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail/{mediaId}") {
        fun createRoute(mediaId: String) = "detail/$mediaId"
    }
    data object Player : Screen("player/{mediaId}/{episodeId}") {
        fun createRoute(mediaId: String, episodeId: String? = null) =
            "player/$mediaId/${episodeId ?: "null"}"
    }
    data object RecentWatching : Screen("recent_watching")
    data object Filter : Screen("filter?typeId={typeId}&navTypeId={navTypeId}") {
        fun createRoute(typeId: Int = -1, navTypeId: Int = -1): String =
            "filter?typeId=$typeId&navTypeId=$navTypeId"
    }
}

@Composable
fun LomenTVNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = hiltViewModel()

                HomeScreen(
                    onNavigateToDetail = { mediaId ->
                        navController.navigate(Screen.Detail.createRoute(mediaId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToRecentWatching = {
                        navController.navigate(Screen.RecentWatching.route)
                    },
                    onNavigateToFilter = { typeId, navTypeId ->
                        navController.navigate(Screen.Filter.createRoute(typeId, navTypeId))
                    },
                    onPlayFromHistory = { historyItem ->
                        CoroutineScope(Dispatchers.Main).launch {
                            val playbackInfo = homeViewModel.getPlaybackInfo(
                                historyItem.mediaId,
                                historyItem.episodeId
                            )
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
                    }
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

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                DetailScreen(
                    mediaId = mediaId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onPlayClick = { videoUrl, title, episodeTitle, playMediaId, episodeId, startPosition ->
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl)
                            putExtra(PlayerActivity.EXTRA_TITLE, title)
                            putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episodeTitle)
                            putExtra(PlayerActivity.EXTRA_MEDIA_ID, playMediaId)
                            putExtra(PlayerActivity.EXTRA_EPISODE_ID, episodeId)
                            putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition)
                        }
                        context.startActivity(intent)
                    }
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
                        CoroutineScope(Dispatchers.Main).launch {
                            val playbackInfo = homeViewModel.getPlaybackInfo(
                                historyItem.mediaId,
                                historyItem.episodeId
                            )
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

            composable(
                route = Screen.Filter.route,
                arguments = listOf(
                    navArgument("typeId") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("navTypeId") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) {
                FilterScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToDetail = { mediaId ->
                        navController.navigate(Screen.Detail.createRoute(mediaId))
                    }
                )
            }
        }
    }
}
