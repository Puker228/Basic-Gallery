package com.example.basicgallery.ui.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.basicgallery.R
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import com.example.basicgallery.ui.theme.BasicGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenMediaScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullscreenPhoto_whenOpenedFromPhotos_showsEditButton() {
        setFullscreenContent(onEditPhoto = {})

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.edit)).assertExists()
    }

    @Test
    fun fullscreenPhoto_whenOpenedFromTrash_hidesEditButton() {
        setFullscreenContent(onEditPhoto = null)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.edit)).assertDoesNotExist()
    }

    @Test
    fun fullscreenMedia_whenSwipedDownFarEnough_closesScreen() {
        var onBackCalls = 0
        setFullscreenContent(
            onEditPhoto = {},
            onBack = { onBackCalls++ }
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.photo_content_description))
            .performTouchInput {
                swipeDown()
            }

        composeRule.runOnIdle {
            assertEquals(1, onBackCalls)
        }
    }

    @Test
    fun fullscreenMedia_whenSwipeDownIsTooShort_staysOpened() {
        var onBackCalls = 0
        setFullscreenContent(
            onEditPhoto = {},
            onBack = { onBackCalls++ }
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.photo_content_description))
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, 60f))
                up()
            }
        composeRule.mainClock.advanceTimeBy(400)

        composeRule.runOnIdle {
            assertEquals(0, onBackCalls)
        }
    }

    private fun setFullscreenContent(
        onEditPhoto: ((PhotoItem) -> Unit)?,
        onBack: () -> Unit = {}
    ) {
        val photo = PhotoItem(
            id = 1L,
            contentUri = Uri.parse("content://com.example.basicgallery.test/photo/1"),
            dateTakenMillis = 0L,
            mediaType = MediaType.PHOTO
        )

        composeRule.setContent {
            BasicGalleryTheme {
                FullscreenMediaScreen(
                    mediaItems = listOf(photo),
                    initialMediaUri = photo.contentUri,
                    onBack = onBack,
                    onEditPhoto = onEditPhoto,
                    onDelete = {},
                    onCurrentMediaChanged = {}
                )
            }
        }
    }
}
