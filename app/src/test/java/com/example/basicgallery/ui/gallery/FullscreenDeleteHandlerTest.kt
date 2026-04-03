package com.example.basicgallery.ui.gallery

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class FullscreenDeleteHandlerTest {

    @Test
    fun createFullscreenDeleteHandler_openedFromPhotos_movesToTrashAndClosesViewer() {
        val uri = mock(Uri::class.java)
        var movedUris: List<Uri>? = null
        var moveClosesViewer: Boolean? = null
        var deleteCalled = false

        val handler = createFullscreenDeleteHandler(
            openedFromTrash = false,
            launchMoveToTrashRequest = { photoUris, closeViewerAfterSuccess ->
                movedUris = photoUris
                moveClosesViewer = closeViewerAfterSuccess
            },
            launchDeleteRequest = { _, _ ->
                deleteCalled = true
            }
        )

        handler(uri)

        assertEquals(listOf(uri), movedUris)
        assertTrue(moveClosesViewer == true)
        assertFalse(deleteCalled)
    }

    @Test
    fun createFullscreenDeleteHandler_openedFromTrash_deletesPermanentlyAndClosesViewer() {
        val uri = mock(Uri::class.java)
        var deletedUris: List<Uri>? = null
        var deleteClosesViewer: Boolean? = null
        var moveToTrashCalled = false

        val handler = createFullscreenDeleteHandler(
            openedFromTrash = true,
            launchMoveToTrashRequest = { _, _ ->
                moveToTrashCalled = true
            },
            launchDeleteRequest = { photoUris, closeViewerAfterSuccess ->
                deletedUris = photoUris
                deleteClosesViewer = closeViewerAfterSuccess
            }
        )

        handler(uri)

        assertEquals(listOf(uri), deletedUris)
        assertTrue(deleteClosesViewer == true)
        assertFalse(moveToTrashCalled)
    }
}
