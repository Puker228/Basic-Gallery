package com.example.basicgallery.data.model

import kotlin.math.abs

data class PhotoCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun normalized(): PhotoCrop {
        val normalizedLeft = left.coerceIn(0f, 1f - MIN_SPAN)
        val normalizedTop = top.coerceIn(0f, 1f - MIN_SPAN)
        val normalizedRight = right.coerceIn(normalizedLeft + MIN_SPAN, 1f)
        val normalizedBottom = bottom.coerceIn(normalizedTop + MIN_SPAN, 1f)
        return copy(
            left = normalizedLeft,
            top = normalizedTop,
            right = normalizedRight,
            bottom = normalizedBottom
        )
    }

    fun isFullImage(): Boolean {
        val normalized = normalized()
        return abs(normalized.left) < EPSILON &&
                abs(normalized.top) < EPSILON &&
                abs(normalized.right - 1f) < EPSILON &&
                abs(normalized.bottom - 1f) < EPSILON
    }

    companion object {
        const val MIN_SPAN = 0.1f
        private const val EPSILON = 0.0001f
    }
}
