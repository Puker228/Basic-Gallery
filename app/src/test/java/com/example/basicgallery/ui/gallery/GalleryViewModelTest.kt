package com.example.basicgallery.ui.gallery

import android.content.IntentSender
import android.net.Uri
import com.example.basicgallery.data.GalleryRepository
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPhotos_populatesGalleryAndTrashState() = runTest {
        val repository = FakeGalleryRepository(
            photos = listOf(
                createPhoto(id = 1L, sizeBytes = 100L),
                createPhoto(id = 2L, sizeBytes = 200L)
            ),
            trashPhotos = listOf(
                createPhoto(id = 10L, sizeBytes = 300L),
                createPhoto(id = 11L, sizeBytes = 500L)
            ),
            videoCount = 4,
            trashVideoCount = 2
        )
        val viewModel = GalleryViewModel(repository)

        viewModel.loadPhotos(forceRefresh = true)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.photos.map { it.id })
        assertEquals(listOf(10L, 11L), state.trashPhotos.map { it.id })
        assertEquals(2, state.photoCount)
        assertEquals(4, state.videoCount)
        assertEquals(2, state.trashPhotoCount)
        assertEquals(2, state.trashVideoCount)
        assertEquals(800L, state.trashSizeBytes)
    }

    @Test
    fun loadPhotos_keepsSelectionOnlyForExistingIdsAcrossSections() = runTest {
        val repository = FakeGalleryRepository(
            photos = listOf(createPhoto(id = 1L)),
            trashPhotos = listOf(createPhoto(id = 2L))
        )
        val viewModel = GalleryViewModel(repository)

        viewModel.startSelection(1L)
        viewModel.startSelection(2L)
        viewModel.startSelection(999L)
        viewModel.loadPhotos(forceRefresh = true)
        runCurrent()

        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedPhotoIds)

        repository.photos = emptyList()
        repository.trashPhotos = listOf(createPhoto(id = 2L))
        viewModel.loadPhotos(forceRefresh = true)
        runCurrent()

        assertEquals(setOf(2L), viewModel.uiState.value.selectedPhotoIds)
    }

    @Test
    fun loadPhotos_withoutForceRefresh_doesNotReloadAfterFirstCall() = runTest {
        val repository = FakeGalleryRepository(
            photos = listOf(createPhoto(id = 1L)),
            trashPhotos = listOf(createPhoto(id = 2L))
        )
        val viewModel = GalleryViewModel(repository)

        viewModel.loadPhotos()
        runCurrent()
        viewModel.loadPhotos()
        runCurrent()

        assertEquals(1, repository.loadPhotosCalls)
        assertEquals(1, repository.loadTrashPhotosCalls)
        assertEquals(1, repository.loadVideoCountCalls)
        assertEquals(1, repository.loadTrashVideoCountCalls)

        viewModel.loadPhotos(forceRefresh = true)
        runCurrent()

        assertEquals(2, repository.loadPhotosCalls)
        assertEquals(2, repository.loadTrashPhotosCalls)
        assertEquals(2, repository.loadVideoCountCalls)
        assertEquals(2, repository.loadTrashVideoCountCalls)
    }

    @Test
    fun selectPhotos_addsAllIdsWithoutLosingExistingSelection() {
        val repository = FakeGalleryRepository()
        val viewModel = GalleryViewModel(repository)

        viewModel.startSelection(1L)
        viewModel.selectPhotos(listOf(2L, 3L, 1L))
        viewModel.selectPhotos(emptyList())

        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedPhotoIds)
    }

    @Test
    fun createRequests_delegateToRepositoryWithExpectedFlags() {
        val repository = FakeGalleryRepository()
        val viewModel = GalleryViewModel(repository)
        val uri = mock(Uri::class.java)
        val sender = mock(IntentSender::class.java)
        repository.trashRequestResult = sender
        repository.deleteRequestResult = sender

        assertSame(sender, viewModel.createMoveToTrashRequest(listOf(uri)))
        assertSame(sender, viewModel.createRestoreRequest(listOf(uri)))
        assertSame(sender, viewModel.createDeleteRequest(listOf(uri)))

        assertEquals(
            listOf(
                TrashRequestCall(photoUris = listOf(uri), moveToTrash = true),
                TrashRequestCall(photoUris = listOf(uri), moveToTrash = false)
            ),
            repository.trashRequests
        )
        assertEquals(listOf(listOf(uri)), repository.deleteRequests)
    }

    private fun createPhoto(id: Long, sizeBytes: Long = 0L): PhotoItem {
        return PhotoItem(
            id = id,
            contentUri = mock(Uri::class.java),
            dateTakenMillis = 1_700_000_000_000L + id,
            sizeBytes = sizeBytes
        )
    }
}

private data class TrashRequestCall(
    val photoUris: List<Uri>,
    val moveToTrash: Boolean
)

private class FakeGalleryRepository(
    var photos: List<PhotoItem> = emptyList(),
    var trashPhotos: List<PhotoItem> = emptyList(),
    var videoCount: Int = 0,
    var trashVideoCount: Int = 0
) : GalleryRepository {

    var loadPhotosCalls = 0
    var loadTrashPhotosCalls = 0
    var loadVideoCountCalls = 0
    var loadTrashVideoCountCalls = 0

    val trashRequests = mutableListOf<TrashRequestCall>()
    val deleteRequests = mutableListOf<List<Uri>>()

    var trashRequestResult: IntentSender? = null
    var deleteRequestResult: IntentSender? = null
    var saveEditedPhotoResult: Uri = mock(Uri::class.java)

    override suspend fun loadPhotos(): List<PhotoItem> {
        loadPhotosCalls += 1
        return photos
    }

    override suspend fun loadTrashPhotos(): List<PhotoItem> {
        loadTrashPhotosCalls += 1
        return trashPhotos
    }

    override suspend fun loadVideoCount(): Int {
        loadVideoCountCalls += 1
        return videoCount
    }

    override suspend fun loadTrashVideoCount(): Int {
        loadTrashVideoCountCalls += 1
        return trashVideoCount
    }

    override fun createTrashRequest(photoUris: Collection<Uri>, moveToTrash: Boolean): IntentSender? {
        trashRequests += TrashRequestCall(photoUris = photoUris.toList(), moveToTrash = moveToTrash)
        return trashRequestResult
    }

    override fun createDeleteRequest(photoUris: Collection<Uri>): IntentSender? {
        deleteRequests += photoUris.toList()
        return deleteRequestResult
    }

    override suspend fun saveEditedPhoto(sourcePhoto: PhotoItem, adjustments: PhotoAdjustments): Uri {
        return saveEditedPhotoResult
    }
}
