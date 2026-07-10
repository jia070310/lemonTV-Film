package com.lemon.yingshi.tv.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class MacCmsVersionDetectorTest {

    @Test
    fun parseProvideXmlProtocolVersion_readsRssVersion() {
        val xml = """<?xml version="1.0"?><rss version="5.1"><list></list></rss>"""
        assertEquals("5.1", MacCmsVersionDetector.parseProvideXmlProtocolVersion(xml))
    }

    @Test
    fun formatVersionLabel_includesProtocolAndRest() {
        val label = MacCmsVersionDetector.formatVersionLabel(
            MacCmsVersionDetector.ProbeResult(
                provideProtocolVersion = "5.1",
                restTypeApiAvailable = true
            )
        )
        assertEquals("MacCMS v10（采集协议 v5.1 · REST 模块）", label)
    }
}
