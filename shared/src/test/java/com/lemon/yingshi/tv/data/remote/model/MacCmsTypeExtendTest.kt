package com.lemon.yingshi.tv.data.remote.model

import com.google.gson.JsonParser
import com.lemon.yingshi.tv.domain.model.MacCmsFilterSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCmsTypeExtendTest {

    @Test
    fun parse_readsJsonObject() {
        val raw = JsonParser.parseString(
            """{"class":"动作,喜剧","area":"内地,美国","lang":"国语","year":"2026,2025"}"""
        )
        val extend = MacCmsTypeExtend.parse(raw)!!
        assertEquals(listOf("动作", "喜剧"), extend.splitPlot())
        assertEquals(listOf("内地", "美国"), extend.splitArea())
        assertEquals(listOf("国语"), extend.splitLang())
        assertEquals(listOf("2026", "2025"), extend.splitYear())
    }

    @Test
    fun parse_readsJsonStringFieldAsInGetList() {
        val raw = JsonParser.parseString(
            "\"{\\\"class\\\":\\\"玄幻,科幻\\\",\\\"area\\\":\\\"日本,内地\\\",\\\"year\\\":\\\"2026,2025,更早\\\"}\""
        )
        val extend = MacCmsTypeExtend.parse(raw)!!
        assertEquals(listOf("玄幻", "科幻"), extend.splitPlot())
        assertEquals(listOf("日本", "内地"), extend.splitArea())
        assertTrue(extend.splitYear().contains("更早"))
    }

    @Test
    fun parse_returnsNullForEmpty() {
        assertNull(MacCmsTypeExtend.parse(JsonParser.parseString("\"\"")))
        assertNull(MacCmsTypeExtend.parse(JsonParser.parseString("{}")))
        assertNull(MacCmsTypeExtend.parse(null))
    }

    @Test
    fun filterOptionsFor_usesServerPlotButCanonicalAreaLangYear() {
        val extend = MacCmsTypeExtend(
            plot = "古装,权谋",
            area = "内地,中国香港",
            lang = "泰语",
            year = "2026,2025,90年代"
        )
        val options = MacCmsFilterSupport.filterOptionsFor("电影", extend)
        assertEquals(listOf("古装", "权谋"), options.plot)
        assertTrue(options.area.contains("大陆"))
        assertTrue(options.area.contains("香港"))
        assertTrue(!options.area.contains("中国香港"))
        assertTrue(options.lang.contains("闽南语"))
        assertEquals("2026", options.year.first())
        assertEquals("2000", options.year.last())
    }

    @Test
    fun fallbackDefaults_alignWithServerCommonExtend() {
        val options = MacCmsFilterSupport.filterOptionsFor("电影")
        assertTrue(options.plot.contains("动作"))
        assertTrue(options.area.contains("大陆"))
        assertTrue(options.area.contains("香港"))
        assertTrue(options.area.contains("新加坡"))
        assertEquals("2026", options.year.first())
        assertEquals("2000", options.year.last())
        assertTrue(options.lang.contains("闽南语"))
        assertTrue(options.lang.contains("其它"))
    }

    @Test
    fun fallbackDefaults_shortDramaMatchedBeforeTv() {
        val options = MacCmsFilterSupport.filterOptionsFor("短剧")
        assertTrue(options.plot.contains("男频"))
        assertTrue(options.lang.contains("国语"))
        assertTrue(options.area.contains("大陆"))
    }

    @Test
    fun areaMatchesFilter_supportsCommonAliases() {
        assertTrue(MacCmsFilterSupport.areaMatchesFilter("中国香港", "香港"))
        assertTrue(MacCmsFilterSupport.areaMatchesFilter("内地", "大陆"))
    }
}
