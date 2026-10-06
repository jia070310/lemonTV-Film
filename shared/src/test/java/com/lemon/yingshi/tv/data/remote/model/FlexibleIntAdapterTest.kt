package com.lemon.yingshi.tv.data.remote.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlexibleIntAdapterTest {

    private val gson: Gson = GsonBuilder().create()

    @Test
    fun parse_readsNumberAndString() {
        assertEquals(9, parseFlexibleInt(JsonParser.parseString("9")))
        assertEquals(9, parseFlexibleInt(JsonParser.parseString("\"9\"")))
        assertEquals(9, parseFlexibleInt(JsonParser.parseString("\"9.0\"")))
        assertNull(parseFlexibleInt(JsonParser.parseString("\"\"")))
        assertNull(parseFlexibleInt(JsonParser.parseString("null")))
    }

    @Test
    fun vodItem_readsStringLevel() {
        val item = gson.fromJson(
            """{"vod_id":"12","vod_name":"测试","vod_level":"9"}""",
            MacCmsVodItem::class.java
        )
        assertEquals(12, item.vodId)
        assertEquals(9, item.vodLevel)
    }

    @Test
    fun listResponse_readsStringPage() {
        val body = gson.fromJson(
            """{"code":1,"page":"1","pagecount":"3","total":"40","list":[]}""",
            MacCmsListResponse::class.java
        )
        assertEquals(1, body.page)
        assertEquals(3, body.pagecount)
        assertEquals(40, body.total)
    }
}
