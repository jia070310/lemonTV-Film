package com.lemon.yingshi.tv.data.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderSeriesNameParserTest {

    @Test
    fun stripArabicAndChineseSeason() {
        val (t1, s1) = FolderSeriesNameParser.stripSeasonMarkers("圆桌派 第1季")
        assertEquals("圆桌派", t1)
        assertEquals(1, s1)

        val (t2, s2) = FolderSeriesNameParser.stripSeasonMarkers("圆桌派 第一季")
        assertEquals("圆桌派", t2)
        assertEquals(1, s2)

        val (t3, s3) = FolderSeriesNameParser.stripSeasonMarkers("某剧 第2季")
        assertEquals("某剧", t3)
        assertEquals(2, s3)
    }

    @Test
    fun stripEnglishSeason() {
        val (t, s) = FolderSeriesNameParser.stripSeasonMarkers("Show Name S02")
        assertEquals("Show Name", t.trim())
        assertEquals(2, s)
    }

    @Test
    fun noSeason() {
        val (t, s) = FolderSeriesNameParser.stripSeasonMarkers("你好美国")
        assertEquals("你好美国", t)
        assertNull(s)
    }

    @Test
    fun stripAllRemovesMultipleSeasonFragments() {
        assertEquals(
            "圆桌派",
            FolderSeriesNameParser.stripAllSeasonMarkers("圆桌派 第八季 第2季")
        )
        assertEquals(
            "圆桌派",
            FolderSeriesNameParser.stripAllSeasonMarkers("圆桌派 第八季")
        )
    }
}
