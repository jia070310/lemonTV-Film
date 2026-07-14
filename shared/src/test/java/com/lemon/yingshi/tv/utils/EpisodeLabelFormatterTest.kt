package com.lemon.yingshi.tv.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeLabelFormatterTest {

    @Test
    fun cellLabel_prefersApiTitle() {
        assertEquals("第01集", EpisodeLabelFormatter.cellLabel(1, "第01集"))
        assertEquals("一集", EpisodeLabelFormatter.cellLabel(1, "一集"))
        assertEquals("1", EpisodeLabelFormatter.cellLabel(1, "1"))
        assertEquals("HD中字", EpisodeLabelFormatter.cellLabel(1, "HD中字"))
        assertEquals("粤语", EpisodeLabelFormatter.cellLabel(2, "粤语"))
        assertEquals("3", EpisodeLabelFormatter.cellLabel(3, null))
        assertEquals("4", EpisodeLabelFormatter.cellLabel(4, "  "))
    }

    @Test
    fun build_keepsOriginalEpisodeAndMovieLabels() {
        assertEquals("第01集", EpisodeLabelFormatter.build(1, "第01集"))
        assertEquals("第1集", EpisodeLabelFormatter.build(1, "第1集"))
        assertEquals("1", EpisodeLabelFormatter.build(1, "1"))
        assertEquals("一集", EpisodeLabelFormatter.build(1, "一集"))
        assertEquals("HD中字", EpisodeLabelFormatter.build(1, "HD中字"))
        assertEquals("HD国语", EpisodeLabelFormatter.build(2, "HD国语"))
        assertEquals("第3集", EpisodeLabelFormatter.build(3, null))
        assertEquals("第1集", EpisodeLabelFormatter.build(1, "同名电影", "同名电影"))
    }
}
