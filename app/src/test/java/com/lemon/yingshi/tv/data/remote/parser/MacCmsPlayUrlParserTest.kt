package com.lemon.yingshi.tv.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class MacCmsPlayUrlParserTest {

    private val dollar = "$"
    private val groupSep = "$$$"

    @Test
    fun parse_mapsPlayerCodeToShowName() {
        val playFrom = "sdm3u8${groupSep}subm3u8"
        val playUrl = "\u7b2c1\u96c6${dollar}https://example.com/a.m3u8${groupSep}\u7b2c1\u96c6${dollar}https://example.com/b.m3u8"
        val playerNames = mapOf(
            "sdm3u8" to "\u95ea\u7535\u64ad\u653e",
            "subm3u8" to "\u901f\u64ad\u8d44\u6e90"
        )

        val sources = MacCmsPlayUrlParser.parse(playFrom, playUrl, playerShowNames = playerNames)

        assertEquals(2, sources.size)
        assertEquals("\u95ea\u7535\u64ad\u653e", sources[0].name)
        assertEquals("\u901f\u64ad\u8d44\u6e90", sources[1].name)
    }

    @Test
    fun parse_fallsBackToPlayNoteWhenPlayerNameMissing() {
        val playFrom = "sdm3u8${groupSep}subm3u8"
        val playNote = "\u95ea\u7535\u64ad\u653e${groupSep}\u901f\u64ad\u8d44\u6e90"
        val playUrl = "\u7b2c1\u96c6${dollar}https://example.com/a.m3u8${groupSep}\u7b2c1\u96c6${dollar}https://example.com/b.m3u8"

        val sources = MacCmsPlayUrlParser.parse(playFrom, playUrl, playNote)

        assertEquals(2, sources.size)
        assertEquals("\u95ea\u7535\u64ad\u653e", sources[0].name)
        assertEquals("\u901f\u64ad\u8d44\u6e90", sources[1].name)
    }

    @Test
    fun parse_fallsBackToLineNumberWhenNameMissing() {
        val playFrom = "sdm3u8${groupSep}subm3u8"
        val playUrl = "\u7b2c1\u96c6${dollar}https://example.com/a.m3u8${groupSep}\u7b2c1\u96c6${dollar}https://example.com/b.m3u8"

        val sources = MacCmsPlayUrlParser.parse(playFrom, playUrl)

        assertEquals(2, sources.size)
        assertEquals("\u7ebf\u8def1", sources[0].name)
        assertEquals("\u7ebf\u8def2", sources[1].name)
    }
}
