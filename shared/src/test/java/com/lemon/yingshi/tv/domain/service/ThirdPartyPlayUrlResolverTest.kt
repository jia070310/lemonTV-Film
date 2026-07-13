package com.lemon.yingshi.tv.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyPlayUrlResolverTest {

    private val resolver = ThirdPartyPlayUrlResolver(
        okHttpClient = okhttp3.OkHttpClient()
    )

    @Test
    fun isDirectPlayableUrl_acceptsM3u8AndMp4() {
        assertTrue(ThirdPartyPlayUrlResolver.isDirectPlayableUrl("https://v.example.com/a/index.m3u8"))
        assertTrue(ThirdPartyPlayUrlResolver.isDirectPlayableUrl("https://v.example.com/a.mp4"))
    }

    @Test
    fun extractStreamUrlFromHtml_readsLz15uuSharePage() {
        val html = """
            <script type="text/javascript">
            var main = "/20260713/15595_9996e95c/index.m3u8?sign=bbb9d393c05393a11565a609a9151b1e";
            </script>
        """.trimIndent()

        val resolved = resolver.extractStreamUrlFromHtml(
            html = html,
            pageUrl = "https://v.lz15uu.com/share/f3c013d50e1737ca632a8f17e5815afc"
        )

        assertEquals(
            "https://v.lz15uu.com/20260713/15595_9996e95c/index.m3u8?sign=bbb9d393c05393a11565a609a9151b1e",
            resolved
        )
    }

    @Test
    fun extractStreamUrlFromParseResponse_readsJsonUrlField() {
        val body = """{"code":200,"url":"https://cdn.example.com/play/index.m3u8?token=abc"}"""

        val resolved = resolver.extractStreamUrlFromParseResponse(
            body = body,
            baseUrl = "https://jx.example.com/?url=encoded"
        )

        assertEquals("https://cdn.example.com/play/index.m3u8?token=abc", resolved)
    }
}
