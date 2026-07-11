package com.lemon.yingshi.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCmsFilterSupportTest {

    @Test
    fun resolveFilterTypePageCount_prefersApiPageCount() {
        assertEquals(
            50,
            MacCmsFilterSupport.resolveFilterTypePageCount(
                pagecount = 50,
                total = 5000,
                batchSize = 20
            )
        )
    }

    @Test
    fun resolveFilterTypePageCount_estimatesFromTotalWhenPageCountIsOne() {
        assertEquals(
            250,
            MacCmsFilterSupport.resolveFilterTypePageCount(
                pagecount = 1,
                total = 5000,
                batchSize = 20
            )
        )
    }

    @Test
    fun resolveFilterTypePageCount_singlePageWhenBatchSmallerThanRequestAndNoMetadata() {
        assertEquals(
            1,
            MacCmsFilterSupport.resolveFilterTypePageCount(
                pagecount = 0,
                total = 0,
                batchSize = 20
            )
        )
    }

    @Test
    fun resolveFilterTypePageCount_keepsPaginatingWhenFullBatchAndNoMetadata() {
        assertEquals(
            MacCmsFilterSupport.FILTER_MAX_PAGES_PER_TYPE,
            MacCmsFilterSupport.resolveFilterTypePageCount(
                pagecount = 0,
                total = 0,
                batchSize = 100
            )
        )
    }

    @Test
    fun isFilterTypePageExhausted_respectsPageCount() {
        assertFalse(MacCmsFilterSupport.isFilterTypePageExhausted(1, 20, 250))
        assertTrue(MacCmsFilterSupport.isFilterTypePageExhausted(250, 10, 250))
        assertTrue(MacCmsFilterSupport.isFilterTypePageExhausted(3, 0, 10))
    }
}
