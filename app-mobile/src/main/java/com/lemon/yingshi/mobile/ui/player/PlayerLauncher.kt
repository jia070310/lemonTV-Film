package com.lemon.yingshi.mobile.ui.player

import android.content.Context
import android.content.Intent
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_EPISODE_ID
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_EPISODE_TITLE
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_MEDIA_ID
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_POSTER_URL
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_START_POSITION
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_TITLE
import com.lemon.yingshi.mobile.ui.player.PlayerActivity.Companion.EXTRA_VIDEO_URL

object PlayerLauncher {

    fun createIntent(
        context: Context,
        videoUrl: String,
        title: String?,
        episodeTitle: String? = null,
        mediaId: String? = null,
        episodeId: String? = null,
        startPosition: Long = 0L,
        posterUrl: String? = null
    ): Intent {
        return Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_URL, videoUrl)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_EPISODE_TITLE, episodeTitle)
            putExtra(EXTRA_MEDIA_ID, mediaId)
            putExtra(EXTRA_EPISODE_ID, episodeId)
            putExtra(EXTRA_START_POSITION, startPosition)
            putExtra(EXTRA_POSTER_URL, posterUrl)
        }
    }

    fun launch(
        context: Context,
        videoUrl: String,
        title: String?,
        episodeTitle: String? = null,
        mediaId: String? = null,
        episodeId: String? = null,
        startPosition: Long = 0L,
        posterUrl: String? = null
    ) {
        context.startActivity(
            createIntent(
                context = context,
                videoUrl = videoUrl,
                title = title,
                episodeTitle = episodeTitle,
                mediaId = mediaId,
                episodeId = episodeId,
                startPosition = startPosition,
                posterUrl = posterUrl
            )
        )
    }
}
