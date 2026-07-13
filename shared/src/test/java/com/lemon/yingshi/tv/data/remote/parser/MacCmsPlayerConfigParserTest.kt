package com.lemon.yingshi.tv.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun parsePlayerConfigs_readsParseFlags() {
        val js = """
            var MacPlayerConfig={};
            MacPlayerConfig.player_list={"lz":{"show":"\u901f\u64ad","des":"","ps":"1","parse":"https://jx.example.com/?url="},"m3u8":{"show":"\u76f4\u64ad","des":"","ps":"0","parse":""}},MacPlayerConfig.downer_list={};
        """.trimIndent()

        val configs = MacCmsPlayerConfigParser.parsePlayerConfigs(js)

        assertEquals("\u901f\u64ad", configs["lz"]?.show)
        assertEquals("https://jx.example.com/?url=", configs["lz"]?.parse)
        assertTrue(configs["lz"]?.ps == true)
        assertTrue(configs["m3u8"]?.ps == false)
    }
}
