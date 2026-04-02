package com.example.basicgallery.data

import com.example.basicgallery.data.model.PhotoItem

interface GalleryRepository {
    suspend fun loadPhotos(): List<PhotoItem>
}
