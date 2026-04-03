package com.example.basicgallery.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.basicgallery.data.model.PhotoAdjustments
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoEditingProcessorInstrumentedTest {

    @Test
    fun applyAdjustments_identity_returnsSameBitmapInstance() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(120, 130, 140))

        val result = PhotoEditingProcessor.applyAdjustments(source, PhotoAdjustments())

        assertSame(source, result)
        source.recycle()
    }

    @Test
    fun applyAdjustments_brightnessIncreasesPixelValue() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(100, 100, 100))

        val result = PhotoEditingProcessor.applyAdjustments(
            source = source,
            adjustments = PhotoAdjustments(brightness = 0.2f)
        )

        val editedRed = Color.red(result.getPixel(1, 1))
        assertTrue(editedRed > 100)

        result.recycle()
        source.recycle()
    }

    @Test
    fun applyAdjustments_sharpnessEnhancesCenterContrast() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(64, 64, 64))
        source.setPixel(1, 1, Color.rgb(128, 128, 128))

        val result = PhotoEditingProcessor.applyAdjustments(
            source = source,
            adjustments = PhotoAdjustments(sharpness = 1f)
        )

        val center = Color.red(result.getPixel(1, 1))
        assertTrue(center > 128)

        result.recycle()
        source.recycle()
    }
}
