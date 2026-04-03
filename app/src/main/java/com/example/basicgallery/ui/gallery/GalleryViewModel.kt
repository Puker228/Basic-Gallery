package com.example.basicgallery.ui.gallery

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.basicgallery.data.GalleryRepository
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

            runCatching { repository.loadPhotos() }
                .onSuccess { photos ->
                    val photoIds = photos.asSequence().map { it.id }.toSet()
                    val retainedSelection =
                        _uiState.value.selectedPhotoIds.filterTo(mutableSetOf()) { id ->
                            id in photoIds
                        }
                    _uiState.value = GalleryUiState(
                        isLoading = false,
                        photos = photos,
                        selectedPhotoIds = retainedSelection
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

    fun clearSelection() {
        _uiState.update { current ->
            if (current.selectedPhotoIds.isEmpty()) current else current.copy(selectedPhotoIds = emptySet())
        }
    }

    fun createTrashRequest(photoUris: Collection<Uri>): IntentSender? {
        return repository.createTrashRequest(photoUris)
    }
}

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
