package com.example.basicgallery.ui.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import com.example.basicgallery.data.model.PhotoItem
import com.example.basicgallery.ui.theme.BasicGalleryTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryPullRevealPanelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullDown_revealsPanel_pushesGridDown_andReleaseHidesPanel() {
        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240,
                videoCount = 0
            )
        )

        val gridNode = composeRule.onNodeWithTag(GALLERY_GRID_TAG)
        val panelNode = composeRule.onNodeWithTag(GALLERY_PULL_REVEAL_PANEL_TAG)

        gridNode.assertExists()
        panelNode.assertDoesNotExist()

        val initialGridTop = gridNode.fetchSemanticsNode().boundsInRoot.top

        gridNode.performTouchInput { down(center) }
        gridNode.performTouchInput {
            moveBy(Offset(x = 0f, y = 260f))
            advanceEventTime(32L)
        }

        composeRule.waitUntil(timeoutMillis = 2_000L) {
            composeRule
                .onAllNodesWithTag(GALLERY_PULL_REVEAL_PANEL_TAG)
                .fetchSemanticsNodes().isNotEmpty()
        }

        panelNode.assertExists()

        val pulledGridTop = gridNode.fetchSemanticsNode().boundsInRoot.top
        val panelBounds = panelNode.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Grid top should move down while pull panel is revealed",
            pulledGridTop > initialGridTop + 1f
        )
        assertTrue(
            "Pull panel should be above grid and not overlay it",
            panelBounds.bottom <= pulledGridTop + 1f
        )

        gridNode.performTouchInput {
            up()
            advanceEventTime(32L)
        }

        composeRule.waitUntil(timeoutMillis = 2_000L) {
            composeRule
                .onAllNodesWithTag(GALLERY_PULL_REVEAL_PANEL_TAG)
                .fetchSemanticsNodes().isEmpty()
        }

        panelNode.assertDoesNotExist()

        val restoredGridTop = gridNode.fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Grid top should return after pull panel hides",
            abs(restoredGridTop - initialGridTop) <= 2f
        )
    }

    private fun setGalleryContent(uiState: GalleryUiState) {
        composeRule.setContent {
            var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
            val context = LocalContext.current
            val imageLoader = remember(context) {
                ImageLoader.Builder(context).build()
            }
            val photosGridState = rememberLazyGridState()
            val trashGridState = rememberLazyGridState()

            BasicGalleryTheme {
                GalleryScreen(
                    uiState = uiState,
                    imageLoader = imageLoader,
                    currentTab = currentTab,
                    photosGridState = photosGridState,
                    trashGridState = trashGridState,
                    onTabSelected = { currentTab = it },
                    onPhotoClick = {},
                    onPhotoLongClick = {},
                    onSelectPhotos = {},
                    onDeselectPhotos = {},
                    onDeleteSelected = {},
                    onRestoreSelected = {},
                    onDeleteSelectedFromTrash = {},
                    onDeleteAllFromTrash = {},
                    onClearSelection = {},
                    onRetry = {}
                )
            }
        }
    }

    private fun createPhotos(count: Int): List<PhotoItem> {
        val nowMillis = System.currentTimeMillis()
        return List(count) { index ->
            PhotoItem(
                id = index.toLong() + 1L,
                contentUri = Uri.parse("content://test/photo/${index + 1}"),
                dateTakenMillis = nowMillis - index * 60_000L
            )
        }
    }
}
