package com.example.basicgallery.ui.gallery

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.basicgallery.R
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoCrop
import com.example.basicgallery.data.model.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoEditorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editor_showsRequestedAdjustmentControls() {
        setEditorContent()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_exposure)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_brightness)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_contrast)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_sharpness)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_crop_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_crop_left)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_crop_top)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_crop_right)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.photo_editor_crop_bottom)).assertIsDisplayed()
    }

    @Test
    fun save_passesUpdatedAdjustmentsAndCrop() {
        var savedAdjustments: PhotoAdjustments? = null
        var savedCrop: PhotoCrop? = null
        setEditorContent(
            onSavePhoto = { _, adjustments, crop ->
                savedAdjustments = adjustments
                savedCrop = crop
                Result.success(Uri.parse("content://test/saved"))
            }
        )

        waitForSaveButtonEnabled()
        setSliderProgress(PHOTO_EDITOR_EXPOSURE_SLIDER_TAG, 1.25f)
        setSliderProgress(PHOTO_EDITOR_BRIGHTNESS_SLIDER_TAG, 0.35f)
        setSliderProgress(PHOTO_EDITOR_CONTRAST_SLIDER_TAG, 0.45f)
        setSliderProgress(PHOTO_EDITOR_SHARPNESS_SLIDER_TAG, 0.8f)
        setSliderProgress(PHOTO_EDITOR_CROP_LEFT_SLIDER_TAG, 0.1f)
        setSliderProgress(PHOTO_EDITOR_CROP_TOP_SLIDER_TAG, 0.2f)
        setSliderProgress(PHOTO_EDITOR_CROP_RIGHT_SLIDER_TAG, 0.9f)
        setSliderProgress(PHOTO_EDITOR_CROP_BOTTOM_SLIDER_TAG, 0.8f)

        composeRule.onNodeWithTag(PHOTO_EDITOR_SAVE_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            savedAdjustments != null && savedCrop != null
        }

        val actual = savedAdjustments
        val actualCrop = savedCrop
        assertNotNull(actual)
        assertNotNull(actualCrop)
        assertEquals(1.25f, actual!!.exposure, 0.001f)
        assertEquals(0.35f, actual.brightness, 0.001f)
        assertEquals(0.45f, actual.contrast, 0.001f)
        assertEquals(0.8f, actual.sharpness, 0.001f)
        assertEquals(0.1f, actualCrop!!.left, 0.001f)
        assertEquals(0.2f, actualCrop.top, 0.001f)
        assertEquals(0.9f, actualCrop.right, 0.001f)
        assertEquals(0.8f, actualCrop.bottom, 0.001f)
    }

    private fun waitForSaveButtonEnabled() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag(PHOTO_EDITOR_SAVE_BUTTON_TAG))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PHOTO_EDITOR_SAVE_BUTTON_TAG).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(value)
        }
    }

    private fun setEditorContent(
        onSavePhoto: suspend (PhotoItem, PhotoAdjustments, PhotoCrop) -> Result<Uri> = { _, _, _ ->
            Result.success(Uri.parse("content://test/saved"))
        }
    ) {
        val uri = createTempImageUri()
        val sourcePhoto = PhotoItem(
            id = 42L,
            contentUri = uri,
            dateTakenMillis = 1_700_000_000_000L
        )
        composeRule.setContent {
            PhotoEditorScreen(
                sourcePhoto = sourcePhoto,
                onBack = {},
                onSavePhoto = onSavePhoto,
                onSaved = {}
            )
        }
    }

    private fun createTempImageUri(): Uri {
        val file = File(composeRule.activity.cacheDir, "photo_editor_test_${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(120, 130, 140))
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
