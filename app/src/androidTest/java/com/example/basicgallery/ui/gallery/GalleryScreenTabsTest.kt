package com.example.basicgallery.ui.gallery

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import com.example.basicgallery.R
import com.example.basicgallery.ui.theme.BasicGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryScreenTabsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun topBar_showsPhotosAndTrashTabs() {
        setGalleryContent()

        composeRule.onNodeWithTag(GALLERY_TAB_ROW_TAG).assertExists()
        composeRule.onNodeWithTag(GALLERY_TAB_PHOTOS_TAG).assertIsSelected()
        composeRule.onNodeWithTag(GALLERY_TAB_TRASH_TAG).assertIsNotSelected()
    }

    @Test
    fun tabs_switchToTrash_updatesSelectionAndActions() {
        setGalleryContent()

        val deleteLabel = composeRule.activity.getString(R.string.delete)

        composeRule.onNodeWithText(deleteLabel).assertDoesNotExist()

        composeRule.onNodeWithTag(GALLERY_TAB_TRASH_TAG).performClick()

        composeRule.onNodeWithTag(GALLERY_TAB_TRASH_TAG).assertIsSelected()
        composeRule.onNodeWithTag(GALLERY_TAB_PHOTOS_TAG).assertIsNotSelected()
        composeRule.onNodeWithText(deleteLabel).assertExists()
        composeRule.onAllNodes(
            matcher = hasText(deleteLabel) and hasAnyAncestor(hasTestTag(GALLERY_TOP_APP_BAR_TAG))
        ).assertCountEquals(0)
    }

    @Test
    fun photosTopBar_containsOnlyTabsNoExtraButtons() {
        setGalleryContent()

        composeRule.onAllNodes(
            matcher = hasClickAction() and hasAnyAncestor(hasTestTag(GALLERY_TOP_APP_BAR_TAG))
        ).assertCountEquals(2)
    }

    @Test
    fun swipe_switchesBetweenPhotosAndTrash() {
        setGalleryContent()

        val deleteLabel = composeRule.activity.getString(R.string.delete)

        composeRule.onNodeWithTag(GALLERY_CONTENT_PAGER_TAG).performTouchInput { swipeLeft() }

        composeRule.onNodeWithTag(GALLERY_TAB_TRASH_TAG).assertIsSelected()
        composeRule.onNodeWithText(deleteLabel).assertExists()

        composeRule.onNodeWithTag(GALLERY_CONTENT_PAGER_TAG).performTouchInput { swipeRight() }

        composeRule.onNodeWithTag(GALLERY_TAB_PHOTOS_TAG).assertIsSelected()
        composeRule.onNodeWithText(deleteLabel).assertDoesNotExist()
    }

    @Test
    fun swipe_backFromTrash_updatesIndicator_whenTabCallbackUsesDerivedCurrentTab() {
        composeRule.setContent {
            var currentTabName by remember { mutableStateOf(GalleryTab.PHOTOS.name) }
            val currentTab = GalleryTab.valueOf(currentTabName)
            val context = LocalContext.current
            val imageLoader = remember(context) {
                ImageLoader.Builder(context).build()
            }

            BasicGalleryTheme {
                GalleryScreen(
                    uiState = GalleryUiState(),
                    imageLoader = imageLoader,
                    currentTab = currentTab,
                    photosGridState = rememberLazyGridState(),
                    trashGridState = rememberLazyGridState(),
                    onTabSelected = { tab ->
                        if (currentTab != tab) {
                            currentTabName = tab.name
                        }
                    },
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

        composeRule.onNodeWithTag(GALLERY_CONTENT_PAGER_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(GALLERY_TAB_TRASH_TAG).assertIsSelected()

        composeRule.onNodeWithTag(GALLERY_CONTENT_PAGER_TAG).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(GALLERY_TAB_PHOTOS_TAG).assertIsSelected()
    }

    private fun setGalleryContent() {
        composeRule.setContent {
            var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
            val context = LocalContext.current
            val imageLoader = remember(context) {
                ImageLoader.Builder(context).build()
            }

            BasicGalleryTheme {
                GalleryScreen(
                    uiState = GalleryUiState(),
                    imageLoader = imageLoader,
                    currentTab = currentTab,
                    photosGridState = rememberLazyGridState(),
                    trashGridState = rememberLazyGridState(),
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
}
