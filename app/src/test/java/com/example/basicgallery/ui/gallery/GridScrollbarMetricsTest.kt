package com.example.basicgallery.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridScrollbarMetricsTest {

    @Test
    fun calculateGridScrollbarMetrics_whenAllItemsFit_returnsNull() {
        val metrics = calculateGridScrollbarMetrics(
            totalItemsCount = 12,
            firstVisibleItemIndex = 0,
            visibleItemsCount = 12
        )

        assertNull(metrics)
    }

    @Test
    fun calculateGridScrollbarMetrics_calculatesThumbSizeAndOffsetFractions() {
        val metrics = calculateGridScrollbarMetrics(
            totalItemsCount = 100,
            firstVisibleItemIndex = 30,
            visibleItemsCount = 20
        )

        requireNotNull(metrics)
        assertEquals(0.2f, metrics.thumbHeightFraction, 0.0001f)
        assertEquals(0.375f, metrics.thumbOffsetFraction, 0.0001f)
    }

    @Test
    fun calculateGridScrollbarMetrics_appliesMinimumThumbAndClampsOffset() {
        val metrics = calculateGridScrollbarMetrics(
            totalItemsCount = 500,
            firstVisibleItemIndex = 9_999,
            visibleItemsCount = 1
        )

        requireNotNull(metrics)
        assertEquals(0.12f, metrics.thumbHeightFraction, 0.0001f)
        assertEquals(1f, metrics.thumbOffsetFraction, 0.0001f)
    }
}
