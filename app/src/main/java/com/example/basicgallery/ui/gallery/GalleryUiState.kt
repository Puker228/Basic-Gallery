package com.example.basicgallery.ui.gallery

import com.example.basicgallery.data.model.PhotoItem

data class GalleryUiState(
    val isLoading: Boolean = false,
    val photos: List<PhotoItem> = emptyList(),
    val errorMessage: String? = null
)
