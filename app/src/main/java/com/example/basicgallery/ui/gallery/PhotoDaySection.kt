package com.example.basicgallery.ui.gallery

import com.example.basicgallery.data.model.PhotoItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class PhotoDaySection(
    val day: LocalDate,
    val label: String,
    val photos: List<PhotoItem>
)

internal fun groupPhotosByDay(
    photos: List<PhotoItem>,
    todayLabel: String,
    yesterdayLabel: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowDate: LocalDate = LocalDate.now(zoneId)
): List<PhotoDaySection> {
    if (photos.isEmpty()) return emptyList()

    val photosByDay = LinkedHashMap<LocalDate, MutableList<PhotoItem>>()
    photos.forEach { photo ->
        val day = Instant.ofEpochMilli(photo.dateTakenMillis)
            .atZone(zoneId)
            .toLocalDate()
        photosByDay.getOrPut(day) { mutableListOf() }.add(photo)
    }

    return photosByDay.map { (day, dayPhotos) ->
        PhotoDaySection(
            day = day,
            label = formatDayLabel(
                day = day,
                nowDate = nowDate,
                locale = locale,
                todayLabel = todayLabel,
                yesterdayLabel = yesterdayLabel
            ),
            photos = dayPhotos
        )
    }
}

private fun formatDayLabel(
    day: LocalDate,
    nowDate: LocalDate,
    locale: Locale,
    todayLabel: String,
    yesterdayLabel: String
): String {
    return when (day) {
        nowDate -> todayLabel
        nowDate.minusDays(1) -> yesterdayLabel
        else -> {
            val pattern = if (day.year == nowDate.year) "d MMM" else "d MMM yyyy"
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            val rawLabel = day.format(formatter).replace(".", "")
            val parts = rawLabel.split(" ").toMutableList()
            if (parts.size >= 2) {
                parts[1] = parts[1].replaceFirstChar { firstChar ->
                    if (firstChar.isLowerCase()) {
                        firstChar.titlecase(locale)
                    } else {
                        firstChar.toString()
                    }
                }
            }
            parts.joinToString(" ")
        }
    }
}
