package com.example.basicgallery.ui.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import com.example.basicgallery.data.model.PhotoItem
import com.example.basicgallery.ui.theme.BasicGalleryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryScreenScrollbarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun photosGrid_scrollbarAppearsOnlyWhileScrolling() {
        lateinit var gridState: LazyGridState
        lateinit var scrollScope: CoroutineScope

        setGalleryContent(
            uiState = GalleryUiState(
                photos = createPhotos(count = 240),
                photoCount = 240
            ),
            onStateReady = { readyGridState, readyScope ->
                gridState = readyGridState
                scrollScope = readyScope
            }
        )

        composeRule.onNodeWithTag(GALLERY_GRID_TAG).assertExists()
        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            scrollScope.launch {
                gridState.animateScrollToItem(index = 180)
            }
        }

        composeRule.mainClock.advanceTimeBy(96L)
        composeRule.runOnIdle {}
        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(GALLERY_SCROLLBAR_TAG).assertDoesNotExist()
    }

    private fun setGalleryContent(
        uiState: GalleryUiState,
        onStateReady: (LazyGridState, CoroutineScope) -> Unit
    ) {
        composeRule.setContent {
            var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
            val context = LocalContext.current
            val imageLoader = remember(context) {
                ImageLoader.Builder(context).build()
            }
            val photosGridState = rememberLazyGridState()
            val trashGridState = rememberLazyGridState()
            val scope = rememberCoroutineScope()
            SideEffect {
                onStateReady(photosGridState, scope)
            }

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
