package com.example.basicgallery.data.model

import android.net.Uri

enum class MediaType {
    PHOTO,
    VIDEO
}

data class PhotoItem(
    val id: Long,
    val contentUri: Uri,
    val dateTakenMillis: Long,
    val sizeBytes: Long = 0L,
    val mediaType: MediaType = MediaType.PHOTO
)
