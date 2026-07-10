package com.lemon.yingshi.tv.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class MacCmsPlayerConfigParserTest {

    @Test
    fun parsePlayerShowNames_readsMacPlayerConfigJs() {
        val js = """
            var MacPlayerConfig={};
            MacPlayerConfig.player_list={"sdm3u8":{"show":"\u95ea\u7535\u64ad\u653e","des":"","ps":"0","parse":""},"subm3u8":{"show":"\u901f\u64ad\u8d44\u6e90","des":"","ps":"0","parse":""}},MacPlayerConfig.downer_list={};
        """.trimIndent()

        val names = MacCmsPlayerConfigParser.parsePlayerShowNames(js)

        assertEquals("\u95ea\u7535\u64ad\u653e", names["sdm3u8"])
        assertEquals("\u901f\u64ad\u8d44\u6e90", names["subm3u8"])
    }
}
