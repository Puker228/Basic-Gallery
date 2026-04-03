package com.example.basicgallery.ui.gallery

import android.net.Uri
import com.example.basicgallery.data.model.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class PhotoDaySectionTest {

    private val zoneId = ZoneId.of("Europe/Moscow")
    private val nowDate = LocalDate.of(2026, 4, 3)

    @Test
    fun groupPhotosByDay_groupsPhotosByLocalDayInInputOrder() {
        val photos = listOf(
            createPhoto(id = 11L, dateTime = LocalDateTime.of(2026, 4, 3, 11, 30)),
            createPhoto(id = 10L, dateTime = LocalDateTime.of(2026, 4, 3, 8, 0)),
            createPhoto(id = 9L, dateTime = LocalDateTime.of(2026, 4, 2, 23, 0)),
            createPhoto(id = 8L, dateTime = LocalDateTime.of(2026, 3, 28, 12, 0))
        )

        val sections = groupPhotosByDay(
            photos = photos,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            zoneId = zoneId,
            locale = Locale.ENGLISH,
            nowDate = nowDate
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 3, 28)
            ),
            sections.map { it.day }
        )
        assertEquals(
            listOf(listOf(11L, 10L), listOf(9L), listOf(8L)),
            sections.map { section -> section.photos.map { it.id } }
        )
    }

    @Test
    fun groupPhotosByDay_formatsRelativeAndAbsoluteHeaders() {
        val photos = listOf(
            createPhoto(id = 3L, dateTime = LocalDateTime.of(2026, 4, 3, 9, 15)),
            createPhoto(id = 2L, dateTime = LocalDateTime.of(2026, 4, 2, 21, 20)),
            createPhoto(id = 1L, dateTime = LocalDateTime.of(2026, 3, 30, 18, 10))
        )

        val sections = groupPhotosByDay(
            photos = photos,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            zoneId = zoneId,
            locale = Locale.ENGLISH,
            nowDate = nowDate
        )

        assertEquals(listOf("Today", "Yesterday", "30 Mar"), sections.map { it.label })
    }

    @Test
    fun groupPhotosByDay_formatsRussianMonthLikeSystemGallery() {
        val photos = listOf(
            createPhoto(id = 1L, dateTime = LocalDateTime.of(2026, 4, 1, 18, 10))
        )

        val sections = groupPhotosByDay(
            photos = photos,
            todayLabel = "Сегодня",
            yesterdayLabel = "Вчера",
            zoneId = zoneId,
            locale = Locale("ru"),
            nowDate = nowDate
        )

        assertEquals(listOf("1 Апр"), sections.map { it.label })
    }

    private fun createPhoto(id: Long, dateTime: LocalDateTime): PhotoItem {
        return PhotoItem(
            id = id,
            contentUri = mock(Uri::class.java),
            dateTakenMillis = dateTime.atZone(zoneId).toInstant().toEpochMilli()
        )
    }
}
