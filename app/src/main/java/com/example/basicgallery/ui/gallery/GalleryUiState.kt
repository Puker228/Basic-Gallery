package com.example.basicgallery.ui.gallery

import com.example.basicgallery.data.model.PhotoItem

data class GalleryUiState(
    val isLoading: Boolean = false,
    val photos: List<PhotoItem> = emptyList(),
    val errorMessage: String? = null,
    val selectedPhotoIds: Set<Long> = emptySet(),
    val photoCount: Int = 0,
    val videoCount: Int = 0
) {
    val selectedCount: Int
        get() = selectedPhotoIds.size

    val isSelectionMode: Boolean
        get() = selectedPhotoIds.isNotEmpty()
}
