package com.example.basicgallery.data

import java.util.Locale

internal object EditedPhotoNameCodec {

    fun buildDisplayName(
        originalName: String?,
        mimeType: String,
        sourceDateTakenMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val baseName = originalName
            ?.substringBeforeLast('.', missingDelimiterValue = originalName)
            ?.takeIf { it.isNotBlank() }
            ?: "IMG_$nowMillis"

        val extension = when (mimeType.lowercase(Locale.US)) {
            MIME_TYPE_PNG -> "png"
            MIME_TYPE_WEBP -> "webp"
            else -> "jpg"
        }

        return if (sourceDateTakenMillis > 0L) {
            "${baseName}_edited_from_${sourceDateTakenMillis}_at_${nowMillis}.$extension"
        } else {
            "${baseName}_edited_${nowMillis}.$extension"
        }
    }

    fun parseSourceDateTakenMillis(displayName: String?): Long? {
        return displayName
            ?.let { EDITED_NAME_DATE_REGEX.find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    fun resolveDateTakenMillis(
        dateTakenMillis: Long,
        dateAddedSeconds: Long,
        displayName: String?
    ): Long {
        return parseSourceDateTakenMillis(displayName)
            ?: if (dateTakenMillis > 0L) dateTakenMillis else dateAddedSeconds * 1000L
    }

    private const val MIME_TYPE_PNG = "image/png"
    private const val MIME_TYPE_WEBP = "image/webp"
    private val EDITED_NAME_DATE_REGEX = Regex("_edited_from_(\\d+)_at_\\d+\\.[^.]+$")
}
