package com.lemon.yingshi.tv.domain.model

import com.lemon.yingshi.tv.data.remote.model.MacCmsEpisodeUrl
import com.lemon.yingshi.tv.data.remote.model.MacCmsPlaySource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCmsPlayLayoutTest {

    @Test
    fun shouldShowEpisodePicker_falseForMultiLineMovie() {
        val sources = listOf(
            MacCmsPlaySource("线路1", listOf(MacCmsEpisodeUrl("正片", "http://a", 1))),
            MacCmsPlaySource("线路2", listOf(MacCmsEpisodeUrl("正片", "http://b", 1)))
        )

        assertFalse(
            MacCmsPlayLayout.shouldShowEpisodePicker(MediaType.MOVIE, sources, sourceIndex = 0)
        )
        assertFalse(
            MacCmsPlayLayout.shouldShowEpisodePicker(MediaType.TV_SHOW, sources, sourceIndex = 0)
        )
    }

    @Test
    fun shouldShowEpisodePicker_falseForMovieLinesInSingleSource() {
        val sources = listOf(
            MacCmsPlaySource(
                name = "默认",
                episodes = listOf(
                    MacCmsEpisodeUrl("正片", "http://a", 1),
                    MacCmsEpisodeUrl("HD国语", "http://b", 2),
                    MacCmsEpisodeUrl("TC", "http://c", 3)
                )
            )
        )

        assertFalse(
            MacCmsPlayLayout.shouldShowEpisodePicker(MediaType.MOVIE, sources, sourceIndex = 0)
        )
    }

    @Test
    fun shouldShowEpisodePicker_trueForTvSeries() {
        val sources = listOf(
            MacCmsPlaySource(
                name = "默认",
                episodes = listOf(
                    MacCmsEpisodeUrl("第1集", "http://a", 1),
                    MacCmsEpisodeUrl("第2集", "http://b", 2),
                    MacCmsEpisodeUrl("第3集", "http://c", 3)
                )
            )
        )

        assertTrue(
            MacCmsPlayLayout.shouldShowEpisodePicker(MediaType.TV_SHOW, sources, sourceIndex = 0)
        )
    }

    @Test
    fun shouldShowEpisodePicker_trueForMultiSourceTvSeries() {
        val sources = listOf(
            MacCmsPlaySource(
                name = "线路1",
                episodes = (1..3).map {
                    MacCmsEpisodeUrl("第${it}集", "http://$it", it)
                }
            ),
            MacCmsPlaySource(
                name = "线路2",
                episodes = (1..3).map {
                    MacCmsEpisodeUrl("第${it}集", "http://alt-$it", it)
                }
            )
        )

        assertTrue(
            MacCmsPlayLayout.shouldShowEpisodePicker(MediaType.TV_SHOW, sources, sourceIndex = 0)
        )
    }
}
