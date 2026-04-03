package com.example.basicgallery.data

import android.graphics.Bitmap
import com.example.basicgallery.data.model.PhotoAdjustments
import kotlin.math.pow
import kotlin.math.roundToInt

object PhotoEditingProcessor {

    fun applyAdjustments(
        source: Bitmap,
        adjustments: PhotoAdjustments
    ): Bitmap {
        if (adjustments.isIdentity()) return source

        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        val sourcePixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        val toneAdjustedPixels = applyToneAdjustments(sourcePixels, adjustments)
        val outputPixels = applySharpness(
            pixels = toneAdjustedPixels,
            width = width,
            height = height,
            strength = adjustments.sharpness.coerceIn(0f, 1f)
        )

        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun applyToneAdjustments(
        sourcePixels: IntArray,
        adjustments: PhotoAdjustments
    ): IntArray {
        val exposure = adjustments.exposure.coerceIn(-2f, 2f)
        val exposureFactor = 2.0.pow(exposure.toDouble()).toFloat()
        val contrastFactor = (1f + adjustments.contrast).coerceAtLeast(0f)
        val brightnessOffset = adjustments.brightness.coerceIn(-1f, 1f) * 255f
        val adjusted = IntArray(sourcePixels.size)

        for (index in sourcePixels.indices) {
            val color = sourcePixels[index]
            val alpha = (color ushr 24) and 0xFF
            val red = (color ushr 16) and 0xFF
            val green = (color ushr 8) and 0xFF
            val blue = color and 0xFF

            val adjustedRed = adjustColorChannel(red, exposureFactor, contrastFactor, brightnessOffset)
            val adjustedGreen = adjustColorChannel(green, exposureFactor, contrastFactor, brightnessOffset)
            val adjustedBlue = adjustColorChannel(blue, exposureFactor, contrastFactor, brightnessOffset)

            adjusted[index] = (alpha shl 24) or
                    (adjustedRed shl 16) or
                    (adjustedGreen shl 8) or
                    adjustedBlue
        }

        return adjusted
    }

    private fun applySharpness(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float
    ): IntArray {
        if (strength <= 0f || width < 3 || height < 3) {
            return pixels
        }

        val result = pixels.copyOf()
        val clampedStrength = strength.coerceIn(0f, 1f)
        val centerWeight = 1f + (4f * clampedStrength)
        val sideWeight = -clampedStrength

        for (y in 1 until (height - 1)) {
            val rowOffset = y * width
            val topOffset = (y - 1) * width
            val bottomOffset = (y + 1) * width

            for (x in 1 until (width - 1)) {
                val centerIndex = rowOffset + x
                val leftIndex = centerIndex - 1
                val rightIndex = centerIndex + 1
                val topIndex = topOffset + x
                val bottomIndex = bottomOffset + x

                val centerColor = pixels[centerIndex]
                val alpha = (centerColor ushr 24) and 0xFF

                val sharpenedRed = sharpenChannel(
                    center = (centerColor ushr 16) and 0xFF,
                    left = (pixels[leftIndex] ushr 16) and 0xFF,
                    right = (pixels[rightIndex] ushr 16) and 0xFF,
                    top = (pixels[topIndex] ushr 16) and 0xFF,
                    bottom = (pixels[bottomIndex] ushr 16) and 0xFF,
                    centerWeight = centerWeight,
                    sideWeight = sideWeight
                )
                val sharpenedGreen = sharpenChannel(
                    center = (centerColor ushr 8) and 0xFF,
                    left = (pixels[leftIndex] ushr 8) and 0xFF,
                    right = (pixels[rightIndex] ushr 8) and 0xFF,
                    top = (pixels[topIndex] ushr 8) and 0xFF,
                    bottom = (pixels[bottomIndex] ushr 8) and 0xFF,
                    centerWeight = centerWeight,
                    sideWeight = sideWeight
                )
                val sharpenedBlue = sharpenChannel(
                    center = centerColor and 0xFF,
                    left = pixels[leftIndex] and 0xFF,
                    right = pixels[rightIndex] and 0xFF,
                    top = pixels[topIndex] and 0xFF,
                    bottom = pixels[bottomIndex] and 0xFF,
                    centerWeight = centerWeight,
                    sideWeight = sideWeight
                )

                result[centerIndex] = (alpha shl 24) or
                        (sharpenedRed shl 16) or
                        (sharpenedGreen shl 8) or
                        sharpenedBlue
            }
        }

        return result
    }

    private fun sharpenChannel(
        center: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        centerWeight: Float,
        sideWeight: Float
    ): Int {
        val value = (center * centerWeight) +
                ((left + right + top + bottom) * sideWeight)
        return value.roundToInt().coerceIn(0, 255)
    }

    private fun adjustColorChannel(
        channel: Int,
        exposureFactor: Float,
        contrastFactor: Float,
        brightnessOffset: Float
    ): Int {
        val contrasted = ((channel - 128f) * contrastFactor) + 128f
        val exposed = contrasted * exposureFactor
        val brightened = exposed + brightnessOffset
        return brightened.roundToInt().coerceIn(0, 255)
    }
}
