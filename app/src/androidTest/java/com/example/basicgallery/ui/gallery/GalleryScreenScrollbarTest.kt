package com.example.basicgallery.ui.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import com.example.basicgallery.data.model.PhotoItem
import com.example.basicgallery.ui.theme.BasicGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryScreenScrollbarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- Initial visibility ---

    @Test
    fun photosGrid_scrollbarHiddenInitiallyEvenWithManyPhotos() {
        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240
            )
        )

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()
    }

    @Test
    fun photosGrid_scrollbarHiddenWhenAllItemsFitOnScreen() {
        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 3),
                photoCount = 3
            )
        )

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()
    }

    // --- Scroll-driven visibility ---

    @Test
    fun photosGrid_scrollbarAppearsWhenGridIsScrolled() {
        composeRule.mainClock.autoAdvance = false

        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240
            )
        )
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()

        composeRule.onNodeWithTag(GALLERY_GRID_TAG).performTouchInput {
            swipeUp()
        }
        // One frame to process isScrollInProgress → true → isVisible = true
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertIsDisplayed()
    }

    @Test
    fun photosGrid_scrollbarHidesAfterScrollStops() {
        composeRule.mainClock.autoAdvance = false

        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240
            )
        )
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_GRID_TAG).performTouchInput {
            swipeUp()
        }
        // Let fling animation and hide delay complete (1200 ms delay + animation budget)
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()
    }

    @Test
    fun photosGrid_scrollbarRemainsVisibleWhileScrollContinues() {
        composeRule.mainClock.autoAdvance = false

        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240
            )
        )
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_GRID_TAG).performTouchInput {
            swipeUp()
        }
        // Advance just enough to show the scrollbar but not past the hide delay
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertIsDisplayed()
    }

    // --- Helper ---

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
