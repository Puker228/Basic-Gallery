package com.example.basicgallery.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

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

    @Test
    fun buildTimelineSectionAnchors_calculatesContiguousRanges() {
        val anchors = buildTimelineSectionAnchors(
            sections = listOf(
                createSection(day = LocalDate.of(2026, 3, 10)),
                createSection(day = LocalDate.of(2026, 3, 9)),
                createSection(day = LocalDate.of(2026, 3, 8))
            )
        )

        assertEquals(0, anchors[0].startIndex)
        assertEquals(1, anchors[0].endIndexExclusive)
        assertEquals(1, anchors[1].startIndex)
        assertEquals(2, anchors[1].endIndexExclusive)
        assertEquals(2, anchors[2].startIndex)
        assertEquals(3, anchors[2].endIndexExclusive)
    }

    @Test
    fun gridIndexForScrollbarFraction_clampsAndRoundsWithinBounds() {
        assertEquals(0, gridIndexForScrollbarFraction(fraction = -1f, totalItemsCount = 10))
        assertEquals(5, gridIndexForScrollbarFraction(fraction = 0.51f, totalItemsCount = 10))
        assertEquals(9, gridIndexForScrollbarFraction(fraction = 5f, totalItemsCount = 10))
    }

    @Test
    fun findTimelineDayForGridIndex_returnsMatchingSectionDay() {
        val firstDay = LocalDate.of(2026, 3, 10)
        val secondDay = LocalDate.of(2026, 1, 1)
        val foundDay = findTimelineDayForGridIndex(
            itemIndex = 4,
            timelineSectionAnchors = listOf(
                GridTimelineSectionAnchor(day = firstDay, startIndex = 0, endIndexExclusive = 3),
                GridTimelineSectionAnchor(day = secondDay, startIndex = 3, endIndexExclusive = 6)
            )
        )

        assertEquals(secondDay, foundDay)
    }

    @Test
    fun calculateTimelineYearMarkers_usesFirstSectionPerYear() {
        val markers = calculateTimelineYearMarkers(
            timelineSectionAnchors = listOf(
                GridTimelineSectionAnchor(
                    day = LocalDate.of(2026, 3, 10),
                    startIndex = 0,
                    endIndexExclusive = 1
                ),
                GridTimelineSectionAnchor(
                    day = LocalDate.of(2026, 1, 2),
                    startIndex = 1,
                    endIndexExclusive = 2
                ),
                GridTimelineSectionAnchor(
                    day = LocalDate.of(2025, 12, 31),
                    startIndex = 2,
                    endIndexExclusive = 3
                )
            ),
            totalItemsCount = 3
        )

        assertEquals(2, markers.size)
        assertEquals(2026, markers[0].year)
        assertEquals(0f, markers[0].offsetFraction, 0.0001f)
        assertEquals(2025, markers[1].year)
        assertEquals(1f, markers[1].offsetFraction, 0.0001f)
    }

    @Test
    fun formatTimelineMonthYearLabel_capitalizesRussianMonth() {
        val label = formatTimelineMonthYearLabel(
            day = LocalDate.of(2025, 1, 5),
            locale = Locale.forLanguageTag("ru")
        )

        assertEquals("Январь 2025", label)
    }

    private fun createSection(day: LocalDate): PhotoDaySection {
        return PhotoDaySection(
            day = day,
            label = "",
            photos = emptyList()
        )
    }
}
