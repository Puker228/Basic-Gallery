package com.example.basicgallery.ui.gallery

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.basicgallery.R
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import com.example.basicgallery.ui.theme.BasicGalleryTheme
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

    private fun setFullscreenContent(onEditPhoto: ((PhotoItem) -> Unit)?) {
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
                    onBack = {},
                    onEditPhoto = onEditPhoto,
                    onDelete = {},
                    onCurrentMediaChanged = {}
                )
            }
        }
    }
}
