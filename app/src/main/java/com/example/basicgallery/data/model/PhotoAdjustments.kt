package com.example.basicgallery.data.model

import kotlin.math.abs

data class PhotoAdjustments(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val sharpness: Float = 0f
) {
    fun isIdentity(): Boolean {
        return abs(exposure) < EPSILON &&
                abs(brightness) < EPSILON &&
                abs(contrast) < EPSILON &&
                abs(sharpness) < EPSILON
    }

    companion object {
        private const val EPSILON = 0.0001f
    }
}
