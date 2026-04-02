package com.example.basicgallery.data.model

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val contentUri: Uri,
    val dateTakenMillis: Long
)
