package com.example.basicgallery.ui.gallery

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.basicgallery.data.GalleryRepository
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    fun loadPhotos(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedOnce) return
        loadedOnce = true

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                val photos = repository.loadPhotos()
                val trashPhotos = repository.loadTrashPhotos()
                val videoCount = repository.loadVideoCount()
                val trashVideoCount = repository.loadTrashVideoCount()
                LoadedMedia(
                    photos = photos,
                    trashPhotos = trashPhotos,
                    videoCount = videoCount,
                    trashVideoCount = trashVideoCount
                )
            }
                .onSuccess { photos ->
                    val allPhotoIds = buildSet {
                        addAll(photos.photos.asSequence().map { it.id })
                        addAll(photos.trashPhotos.asSequence().map { it.id })
                    }
                    val retainedSelection =
                        _uiState.value.selectedPhotoIds.filterTo(mutableSetOf()) { id ->
                            id in allPhotoIds
                        }
                    _uiState.value = GalleryUiState(
                        isLoading = false,
                        photos = photos.photos,
                        trashPhotos = photos.trashPhotos,
                        selectedPhotoIds = retainedSelection,
                        photoCount = photos.photos.count { it.mediaType == MediaType.PHOTO },
                        videoCount = photos.videoCount,
                        trashPhotoCount = photos.trashPhotos.count { it.mediaType == MediaType.PHOTO },
                        trashVideoCount = photos.trashVideoCount,
                        trashSizeBytes = photos.trashPhotos
                            .asSequence()
                            .filter { it.mediaType == MediaType.PHOTO }
                            .sumOf { it.sizeBytes }
                    )
                }
                .onFailure { error ->
                    _uiState.value = GalleryUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load photos."
                    )
                }
        }
    }

    fun startSelection(photoId: Long) {
        _uiState.update { current ->
            current.copy(selectedPhotoIds = current.selectedPhotoIds + photoId)
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { current ->
            val updatedSelection = if (photoId in current.selectedPhotoIds) {
                current.selectedPhotoIds - photoId
            } else {
                current.selectedPhotoIds + photoId
            }
            current.copy(selectedPhotoIds = updatedSelection)
        }
    }

    fun selectPhotos(photoIds: Collection<Long>) {
        if (photoIds.isEmpty()) return

        _uiState.update { current ->
            current.copy(selectedPhotoIds = current.selectedPhotoIds + photoIds)
        }
    }

    fun clearSelection() {
        _uiState.update { current ->
            if (current.selectedPhotoIds.isEmpty()) current else current.copy(selectedPhotoIds = emptySet())
        }
    }

    fun createMoveToTrashRequest(photoUris: Collection<Uri>): IntentSender? {
        return repository.createTrashRequest(photoUris, moveToTrash = true)
    }

    fun createRestoreRequest(photoUris: Collection<Uri>): IntentSender? {
        return repository.createTrashRequest(photoUris, moveToTrash = false)
    }

    fun createDeleteRequest(photoUris: Collection<Uri>): IntentSender? {
        return repository.createDeleteRequest(photoUris)
    }
}

private data class LoadedMedia(
    val photos: List<PhotoItem>,
    val trashPhotos: List<PhotoItem>,
    val videoCount: Int,
    val trashVideoCount: Int
)

class GalleryViewModelFactory(
    private val repository: GalleryRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
