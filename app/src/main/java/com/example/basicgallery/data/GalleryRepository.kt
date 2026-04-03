package com.example.basicgallery.data

import android.content.IntentSender
import android.net.Uri
import com.example.basicgallery.data.model.PhotoItem

interface GalleryRepository {
    suspend fun loadPhotos(): List<PhotoItem>
    suspend fun loadVideoCount(): Int

    fun createTrashRequest(photoUris: Collection<Uri>): IntentSender?
}
