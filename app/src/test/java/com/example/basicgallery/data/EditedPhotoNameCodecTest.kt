package com.example.basicgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditedPhotoNameCodecTest {

    @Test
    fun buildDisplayName_withSourceTimestamp_encodesSourceAndSaveTimes() {
        val sourceTimestamp = 1_712_342_400_000L
        val saveTimestamp = 1_712_500_000_000L

        val result = EditedPhotoNameCodec.buildDisplayName(
            originalName = "IMG_0001.jpg",
            mimeType = "image/jpeg",
            sourceDateTakenMillis = sourceTimestamp,
            nowMillis = saveTimestamp
        )

        assertEquals(
            "IMG_0001_edited_from_1712342400000_at_1712500000000.jpg",
            result
        )
    }

    @Test
    fun buildDisplayName_withoutSourceTimestamp_usesEditedSaveTimePattern() {
        val saveTimestamp = 1_712_500_000_000L

        val result = EditedPhotoNameCodec.buildDisplayName(
            originalName = "IMG_0001.jpg",
            mimeType = "image/jpeg",
            sourceDateTakenMillis = 0L,
            nowMillis = saveTimestamp
        )

        assertEquals("IMG_0001_edited_1712500000000.jpg", result)
    }

    @Test
    fun parseSourceDateTakenMillis_fromEncodedName_returnsSourceTimestamp() {
        val result = EditedPhotoNameCodec.parseSourceDateTakenMillis(
            "IMG_0001_edited_from_1712342400000_at_1712500000000.jpg"
        )

        assertEquals(1_712_342_400_000L, result)
    }

    @Test
    fun parseSourceDateTakenMillis_fromNonEncodedName_returnsNull() {
        val result = EditedPhotoNameCodec.parseSourceDateTakenMillis(
            "IMG_0001_edited_1712500000000.jpg"
        )

        assertNull(result)
    }

    @Test
    fun resolveDateTakenMillis_prefersEncodedSourceTimestamp() {
        val result = EditedPhotoNameCodec.resolveDateTakenMillis(
            dateTakenMillis = 1_712_500_000_000L,
            dateAddedSeconds = 1_712_500_000L,
            displayName = "IMG_0001_edited_from_1712342400000_at_1712500000000.jpg"
        )

        assertEquals(1_712_342_400_000L, result)
    }

    @Test
    fun resolveDateTakenMillis_fallsBackToMediaStoreColumns() {
        val withDateTaken = EditedPhotoNameCodec.resolveDateTakenMillis(
            dateTakenMillis = 1_700_000_000_000L,
            dateAddedSeconds = 1_600_000_000L,
            displayName = "IMG_regular.jpg"
        )
        val withDateAdded = EditedPhotoNameCodec.resolveDateTakenMillis(
            dateTakenMillis = 0L,
            dateAddedSeconds = 1_600_000_000L,
            displayName = "IMG_regular.jpg"
        )

        assertEquals(1_700_000_000_000L, withDateTaken)
        assertEquals(1_600_000_000_000L, withDateAdded)
    }
}
