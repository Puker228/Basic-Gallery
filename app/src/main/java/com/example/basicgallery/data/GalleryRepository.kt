package com.example.basicgallery.data

import android.content.IntentSender
import android.net.Uri
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoCrop
import com.example.basicgallery.data.model.PhotoItem

interface GalleryRepository {
    suspend fun loadPhotos(): List<PhotoItem>
    suspend fun loadTrashPhotos(): List<PhotoItem>
    suspend fun loadVideoCount(): Int
    suspend fun loadTrashVideoCount(): Int
    suspend fun saveEditedPhoto(
        sourcePhoto: PhotoItem,
        adjustments: PhotoAdjustments,
        crop: PhotoCrop
    ): Uri

    fun createTrashRequest(photoUris: Collection<Uri>, moveToTrash: Boolean): IntentSender?
    fun createDeleteRequest(photoUris: Collection<Uri>): IntentSender?
}
