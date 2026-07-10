package com.lemon.yingshi.tv.utils

import com.lemon.yingshi.tv.domain.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class FileNameParserTest {

    @Test
    fun `test movie filename parsing`() {
        val testCases = listOf(
            "The.Matrix.1999.1080p.BluRay.x264.mp4" to Pair("The Matrix", 1999),
            "Inception (2010) [1080p].mkv" to Pair("Inception", 2010),
            "Avatar.2009.Extended.Cut.1080p.mp4" to Pair("Avatar", 2009),
            "盗梦空间.2010.HD国语中字.mp4" to Pair("盗梦空间", 2010),
            "肖申克的救赎 (1994).mkv" to Pair("肖申克的救赎", 1994),
            "周处除三害 (2023).mp4" to Pair("周处除三害", 2023),
            "【高清网发布www.xxx.com】万界独尊.2022.mp4" to Pair("万界独尊", 2022)
        )

        testCases.forEach { (filename, expected) ->
            val result = FileNameParser.parse(filename)
            assertEquals("Title mismatch for $filename", expected.first, result.title)
            assertEquals("Year mismatch for $filename", expected.second, result.year)
            assertEquals("Type should be MOVIE for $filename", MediaType.MOVIE, result.type)
        }
    }

    @Test
    fun `test tv show filename parsing with S01E01 format`() {
        data class TvTestCase(
            val filename: String,
            val expectedTitle: String,
            val expectedSeason: Int,
            val expectedEpisode: Int
        )

        val testCases = listOf(
            TvTestCase("Game.of.Thrones.S01E01.1080p.mkv", "Game of Thrones", 1, 1),
            TvTestCase("Breaking.Bad.S05E14.720p.mp4", "Breaking Bad", 5, 14),
            TvTestCase("The.Mandalorian.S02E08.mkv", "The Mandalorian", 2, 8),
            TvTestCase("甄嬛传 S01E01.mp4", "甄嬛传", 1, 1),
            TvTestCase("Friends S03E10.mkv", "Friends", 3, 10)
        )

        testCases.forEach { (filename, expectedTitle, expectedSeason, expectedEpisode) ->
            val result = FileNameParser.parse(filename)
            assertEquals("Title mismatch", expectedTitle, result.title)
            assertEquals("Season mismatch", expectedSeason, result.season)
            assertEquals("Episode mismatch", expectedEpisode, result.episode)
            assertEquals("Type should be TV_SHOW", MediaType.TV_SHOW, result.type)
        }
    }

    @Test
    fun `test variety leading episode number with dot`() {
        val cases = listOf(
            "01. 师徒.mp4" to Pair(1, "师徒"),
            "04. 音乐.mp4" to Pair(4, "音乐"),
            "12．标题.mkv" to Pair(12, "标题"),
            // 空格分隔（优酷类综艺常见）
            "01 出身：家世决定你多少.mp4" to Pair(1, "出身：家世决定你多少"),
            "02 高潜：你被团队友坑过吗.mp4" to Pair(2, "高潜：你被团队友坑过吗")
        )
        for ((filename, expected) in cases) {
            val result = FileNameParser.parse(filename)
            assertEquals("episode for $filename", expected.first, result.episode)
            assertEquals("title for $filename", expected.second, result.title)
            assertEquals(MediaType.TV_SHOW, result.type)
        }
    }

    @Test
    fun `test tv show filename parsing with Chinese format`() {
        data class TvTestCase(
            val filename: String,
            val expectedTitle: String,
            val expectedSeason: Int,
            val expectedEpisode: Int
        )

        val testCases = listOf(
            TvTestCase("权力的游戏.第一季第一集.mp4", "权力的游戏", 1, 1),
            TvTestCase("三体.第2季第5集.mkv", "三体", 2, 5),
            TvTestCase("狂飙.第1季.第12集.mp4", "狂飙", 1, 12)
        )

        testCases.forEach { (filename, expectedTitle, expectedSeason, expectedEpisode) ->
            val result = FileNameParser.parse(filename)
            assertEquals("Title mismatch for $filename", expectedTitle, result.title)
            assertEquals("Season mismatch for $filename", expectedSeason, result.season)
            assertEquals("Episode mismatch for $filename", expectedEpisode, result.episode)
            assertEquals("Type should be TV_SHOW", MediaType.TV_SHOW, result.type)
        }
    }

    @Test
    fun `test resolution extraction`() {
        val testCases = mapOf(
            "Movie.1080p.mp4" to "1080P",
            "Movie.2160p.mkv" to "2160P",
            "Movie.4K.mp4" to "4K",
            "Movie.720p.avi" to "720P"
        )

        testCases.forEach { (filename, expectedResolution) ->
            val result = FileNameParser.parse(filename)
            assertEquals("Resolution mismatch for $filename", expectedResolution, result.resolution)
        }
    }

    @Test
    fun `test video file detection`() {
        val videoFiles = listOf("movie.mp4", "show.mkv", "video.avi", "film.mov")
        val nonVideoFiles = listOf("document.pdf", "image.jpg", "music.mp3")

        videoFiles.forEach { filename ->
            assertTrue("$filename should be a video file", FileNameParser.isVideoFile(filename))
        }

        nonVideoFiles.forEach { filename ->
            assertFalse("$filename should not be a video file", FileNameParser.isVideoFile(filename))
        }
    }

    @Test
    fun `test extension function`() {
        val filename = "Test.Movie.2024.1080p.mp4"
        val result = filename.parseFileName()

        assertEquals("Test Movie", result.title)
        assertEquals(2024, result.year)
        assertEquals("1080P", result.resolution)
        assertTrue(filename.isVideoFile())
    }

    @Test
    fun `test episode only format`() {
        val filename = "Show.EP05.mp4"
        val result = FileNameParser.parse(filename)

        assertEquals("Show", result.title)
        assertNull(result.season)
        assertEquals(5, result.episode)
        assertEquals(MediaType.TV_SHOW, result.type)
    }

    @Test
    fun `test 1x01 format`() {
        val filename = "Friends.1x05.mp4"
        val result = FileNameParser.parse(filename)

        assertEquals("Friends", result.title)
        assertEquals(1, result.season)
        assertEquals(5, result.episode)
    }

    @Test
    fun `test season episode variants and specials`() {
        val seasonEpisodeDot = FileNameParser.parse("Westworld.season.1.episode.1.mkv")
        assertEquals("Westworld", seasonEpisodeDot.title)
        assertEquals(1, seasonEpisodeDot.season)
        assertEquals(1, seasonEpisodeDot.episode)
        assertEquals(MediaType.TV_SHOW, seasonEpisodeDot.type)

        val special = FileNameParser.parse("某科学的超电磁炮 S00E01.mkv")
        assertEquals("某科学的超电磁炮", special.title)
        assertEquals(0, special.season)
        assertEquals(1, special.episode)
        assertEquals(MediaType.TV_SHOW, special.type)
    }

    @Test
    fun `test multi episode range format`() {
        val result = FileNameParser.parse("剧名.S01E01-E04.mkv")
        assertEquals("剧名", result.title)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
        assertEquals(MediaType.TV_SHOW, result.type)
    }

    @Test
    fun `test numeric prefix as episode`() {
        val result = FileNameParser.parse("01 4K.国&粤.mp4")
        assertEquals("国&粤", result.title)
        assertNull(result.season)
        assertEquals(1, result.episode)
        assertEquals(MediaType.TV_SHOW, result.type)
    }

    @Test
    fun `test complex filename`() {
        val filename = "The.Lord.of.the.Rings.The.Fellowship.of.the.Ring.2001.Extended.Edition.1080p.BluRay.x264.DTS.mp4"
        val result = FileNameParser.parse(filename)

        assertEquals("The Lord of the Rings The Fellowship of the Ring", result.title)
        assertEquals(2001, result.year)
        assertEquals(MediaType.MOVIE, result.type)
        assertEquals("1080P", result.resolution)
    }
}
