@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.basicgallery.ui.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.basicgallery.R
import com.example.basicgallery.data.PhotoEditingProcessor
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoCrop
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREVIEW_MAX_DIMENSION = 1600
private const val PREVIEW_RECALC_DEBOUNCE_MS = 80L

internal const val PHOTO_EDITOR_SAVE_BUTTON_TAG = "photo_editor_save_button"
internal const val PHOTO_EDITOR_EXPOSURE_SLIDER_TAG = "photo_editor_exposure_slider"
internal const val PHOTO_EDITOR_BRIGHTNESS_SLIDER_TAG = "photo_editor_brightness_slider"
internal const val PHOTO_EDITOR_CONTRAST_SLIDER_TAG = "photo_editor_contrast_slider"
internal const val PHOTO_EDITOR_SHARPNESS_SLIDER_TAG = "photo_editor_sharpness_slider"
internal const val PHOTO_EDITOR_CROP_FRAME_TAG = "photo_editor_crop_frame"
internal const val PHOTO_EDITOR_CROP_HANDLE_TOP_LEFT_TAG = "photo_editor_crop_handle_top_left"
internal const val PHOTO_EDITOR_CROP_HANDLE_TOP_RIGHT_TAG = "photo_editor_crop_handle_top_right"
internal const val PHOTO_EDITOR_CROP_HANDLE_BOTTOM_LEFT_TAG = "photo_editor_crop_handle_bottom_left"
internal const val PHOTO_EDITOR_CROP_HANDLE_BOTTOM_RIGHT_TAG = "photo_editor_crop_handle_bottom_right"

private enum class CropHandleCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

private data class DisplayImageFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height
}

@Composable
internal fun PhotoEditorScreen(
    sourcePhoto: PhotoItem,
    onBack: () -> Unit,
    onSavePhoto: suspend (PhotoItem, PhotoAdjustments, PhotoCrop) -> Result<Uri>,
    onSaved: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adjustments by remember(sourcePhoto.id) { mutableStateOf(PhotoAdjustments()) }
    var crop by remember(sourcePhoto.id) { mutableStateOf(PhotoCrop()) }
    var sourceBitmap by remember(sourcePhoto.id) { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember(sourcePhoto.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(sourcePhoto.id) { mutableStateOf(true) }
    var hasLoadingError by remember(sourcePhoto.id) { mutableStateOf(false) }
    var isSaving by remember(sourcePhoto.id) { mutableStateOf(false) }
    val canSave = !isLoading && !isSaving && sourceBitmap != null

    BackHandler(onBack = onBack)

    LaunchedEffect(sourcePhoto.contentUri) {
        isLoading = true
        hasLoadingError = false
        val loadedBitmap = withContext(Dispatchers.IO) {
            runCatching {
                decodeScaledBitmap(
                    contentResolver = context.contentResolver,
                    uri = sourcePhoto.contentUri,
                    maxDimension = PREVIEW_MAX_DIMENSION
                )
            }
        }
        loadedBitmap
            .onSuccess { bitmap ->
                sourceBitmap = bitmap
                previewBitmap = bitmap
                crop = PhotoCrop()
                isLoading = false
                hasLoadingError = false
            }
            .onFailure {
                hasLoadingError = true
                isLoading = false
            }
    }

    LaunchedEffect(sourceBitmap, adjustments) {
        val original = sourceBitmap ?: return@LaunchedEffect

        if (adjustments.isIdentity()) {
            val oldPreview = previewBitmap
            previewBitmap = original
            if (oldPreview != null && oldPreview !== original && !oldPreview.isRecycled) {
                withFrameNanos { }
                if (previewBitmap !== oldPreview && !oldPreview.isRecycled) {
                    oldPreview.recycle()
                }
            }
            return@LaunchedEffect
        }

        delay(PREVIEW_RECALC_DEBOUNCE_MS)

        val processedPreview = withContext(Dispatchers.Default) {
            PhotoEditingProcessor.applyAdjustments(original, adjustments)
        }
        if (!isActive) {
            if (processedPreview !== original && !processedPreview.isRecycled) {
                processedPreview.recycle()
            }
            return@LaunchedEffect
        }
        val oldPreview = previewBitmap
        previewBitmap = processedPreview
        if (oldPreview != null &&
            oldPreview !== original &&
            oldPreview !== processedPreview &&
            !oldPreview.isRecycled
        ) {
            withFrameNanos { }
            if (previewBitmap !== oldPreview && !oldPreview.isRecycled) {
                oldPreview.recycle()
            }
        }
    }

    DisposableEffect(sourcePhoto.id) {
        onDispose {
            val currentSource = sourceBitmap
            val currentPreview = previewBitmap
            if (currentPreview != null && currentPreview !== currentSource && !currentPreview.isRecycled) {
                currentPreview.recycle()
            }
            if (currentSource != null && !currentSource.isRecycled) {
                currentSource.recycle()
            }
        }
    }

    fun savePhoto() {
        if (!canSave) return
        isSaving = true
        scope.launch {
            val result = onSavePhoto(sourcePhoto, adjustments, crop.normalized())
            isSaving = false
            result
                .onSuccess { savedUri ->
                    Toast.makeText(context, R.string.photo_editor_saved, Toast.LENGTH_SHORT).show()
                    onSaved(savedUri)
                }
                .onFailure {
                    Toast.makeText(context, R.string.photo_editor_save_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.photo_editor_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(id = R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag(PHOTO_EDITOR_SAVE_BUTTON_TAG),
                        onClick = { savePhoto() },
                        enabled = canSave
                    ) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_exposure),
                        value = adjustments.exposure,
                        valueRange = -2f..2f,
                        sliderTag = PHOTO_EDITOR_EXPOSURE_SLIDER_TAG,
                        onValueChange = { adjustments = adjustments.copy(exposure = it) }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_brightness),
                        value = adjustments.brightness,
                        valueRange = -1f..1f,
                        sliderTag = PHOTO_EDITOR_BRIGHTNESS_SLIDER_TAG,
                        onValueChange = { adjustments = adjustments.copy(brightness = it) }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_contrast),
                        value = adjustments.contrast,
                        valueRange = -1f..1f,
                        sliderTag = PHOTO_EDITOR_CONTRAST_SLIDER_TAG,
                        onValueChange = { adjustments = adjustments.copy(contrast = it) }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_sharpness),
                        value = adjustments.sharpness,
                        valueRange = 0f..1f,
                        sliderTag = PHOTO_EDITOR_SHARPNESS_SLIDER_TAG,
                        onValueChange = { adjustments = adjustments.copy(sharpness = it) }
                    )
                    Text(
                        text = stringResource(id = R.string.photo_editor_crop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.scrim),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                hasLoadingError || previewBitmap == null || sourceBitmap == null -> {
                    Text(
                        text = stringResource(id = R.string.photo_editor_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                else -> {
                    CropPreviewArea(
                        bitmap = previewBitmap!!,
                        sourceBitmapWidth = sourceBitmap!!.width,
                        sourceBitmapHeight = sourceBitmap!!.height,
                        crop = crop,
                        onCropChange = { crop = it }
                    )
                }
            }

            if (isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun CropPreviewArea(
    bitmap: Bitmap,
    sourceBitmapWidth: Int,
    sourceBitmapHeight: Int,
    crop: PhotoCrop,
    onCropChange: (PhotoCrop) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val containerHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val imageFrame = remember(
            containerWidthPx,
            containerHeightPx,
            sourceBitmapWidth,
            sourceBitmapHeight
        ) {
            calculateDisplayImageFrame(
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                imageWidthPx = sourceBitmapWidth.toFloat().coerceAtLeast(1f),
                imageHeightPx = sourceBitmapHeight.toFloat().coerceAtLeast(1f)
            )
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(id = R.string.photo_content_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        CropFrameOverlay(
            imageFrame = imageFrame,
            crop = crop,
            onCropChange = onCropChange
        )
    }
}

@Composable
private fun CropFrameOverlay(
    imageFrame: DisplayImageFrame,
    crop: PhotoCrop,
    onCropChange: (PhotoCrop) -> Unit
) {
    val normalizedCrop = crop.normalized()
    val cropLeft = imageFrame.left + (normalizedCrop.left * imageFrame.width)
    val cropTop = imageFrame.top + (normalizedCrop.top * imageFrame.height)
    val cropRight = imageFrame.left + (normalizedCrop.right * imageFrame.width)
    val cropBottom = imageFrame.top + (normalizedCrop.bottom * imageFrame.height)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PHOTO_EDITOR_CROP_FRAME_TAG)
    ) {
        val dimColor = Color.Black.copy(alpha = 0.45f)
        drawRect(
            color = dimColor,
            topLeft = Offset.Zero,
            size = Size(width = size.width, height = cropTop)
        )
        drawRect(
            color = dimColor,
            topLeft = Offset(0f, cropBottom),
            size = Size(width = size.width, height = size.height - cropBottom)
        )
        drawRect(
            color = dimColor,
            topLeft = Offset(0f, cropTop),
            size = Size(width = cropLeft, height = cropBottom - cropTop)
        )
        drawRect(
            color = dimColor,
            topLeft = Offset(cropRight, cropTop),
            size = Size(width = size.width - cropRight, height = cropBottom - cropTop)
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(cropLeft, cropTop),
            size = Size(width = cropRight - cropLeft, height = cropBottom - cropTop),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    CropCornerHandle(
        tag = PHOTO_EDITOR_CROP_HANDLE_TOP_LEFT_TAG,
        centerX = cropLeft,
        centerY = cropTop,
        onDrag = { dragX, dragY ->
            onCropChange(
                updateCropFromCorner(
                    crop = normalizedCrop,
                    corner = CropHandleCorner.TOP_LEFT,
                    dragXNorm = dragX / imageFrame.width,
                    dragYNorm = dragY / imageFrame.height
                )
            )
        }
    )
    CropCornerHandle(
        tag = PHOTO_EDITOR_CROP_HANDLE_TOP_RIGHT_TAG,
        centerX = cropRight,
        centerY = cropTop,
        onDrag = { dragX, dragY ->
            onCropChange(
                updateCropFromCorner(
                    crop = normalizedCrop,
                    corner = CropHandleCorner.TOP_RIGHT,
                    dragXNorm = dragX / imageFrame.width,
                    dragYNorm = dragY / imageFrame.height
                )
            )
        }
    )
    CropCornerHandle(
        tag = PHOTO_EDITOR_CROP_HANDLE_BOTTOM_LEFT_TAG,
        centerX = cropLeft,
        centerY = cropBottom,
        onDrag = { dragX, dragY ->
            onCropChange(
                updateCropFromCorner(
                    crop = normalizedCrop,
                    corner = CropHandleCorner.BOTTOM_LEFT,
                    dragXNorm = dragX / imageFrame.width,
                    dragYNorm = dragY / imageFrame.height
                )
            )
        }
    )
    CropCornerHandle(
        tag = PHOTO_EDITOR_CROP_HANDLE_BOTTOM_RIGHT_TAG,
        centerX = cropRight,
        centerY = cropBottom,
        onDrag = { dragX, dragY ->
            onCropChange(
                updateCropFromCorner(
                    crop = normalizedCrop,
                    corner = CropHandleCorner.BOTTOM_RIGHT,
                    dragXNorm = dragX / imageFrame.width,
                    dragYNorm = dragY / imageFrame.height
                )
            )
        }
    )
}

@Composable
private fun CropCornerHandle(
    tag: String,
    centerX: Float,
    centerY: Float,
    onDrag: (dragX: Float, dragY: Float) -> Unit
) {
    val touchSize = 36.dp
    val visualSize = 14.dp
    val density = LocalDensity.current
    val halfTouchPx = with(density) { touchSize.toPx() / 2f }
    val latestOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (centerX - halfTouchPx).roundToInt(),
                    y = (centerY - halfTouchPx).roundToInt()
                )
            }
            .size(touchSize)
            .testTag(tag)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestOnDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .background(Color.White)
                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun AdjustmentSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    sliderTag: String,
    valueFormatter: (Float) -> String = ::formatSliderValue,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Slider(
            modifier = Modifier.testTag(sliderTag),
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

private fun calculateDisplayImageFrame(
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageWidthPx: Float,
    imageHeightPx: Float
): DisplayImageFrame {
    val fitScale = min(
        containerWidthPx / imageWidthPx,
        containerHeightPx / imageHeightPx
    )
    val displayedWidth = imageWidthPx * fitScale
    val displayedHeight = imageHeightPx * fitScale
    return DisplayImageFrame(
        left = (containerWidthPx - displayedWidth) / 2f,
        top = (containerHeightPx - displayedHeight) / 2f,
        width = displayedWidth,
        height = displayedHeight
    )
}

private fun updateCropFromCorner(
    crop: PhotoCrop,
    corner: CropHandleCorner,
    dragXNorm: Float,
    dragYNorm: Float
): PhotoCrop {
    val normalized = crop.normalized()
    var left = normalized.left
    var top = normalized.top
    var right = normalized.right
    var bottom = normalized.bottom

    when (corner) {
        CropHandleCorner.TOP_LEFT -> {
            left = (left + dragXNorm).coerceIn(0f, right - PhotoCrop.MIN_SPAN)
            top = (top + dragYNorm).coerceIn(0f, bottom - PhotoCrop.MIN_SPAN)
        }

        CropHandleCorner.TOP_RIGHT -> {
            right = (right + dragXNorm).coerceIn(left + PhotoCrop.MIN_SPAN, 1f)
            top = (top + dragYNorm).coerceIn(0f, bottom - PhotoCrop.MIN_SPAN)
        }

        CropHandleCorner.BOTTOM_LEFT -> {
            left = (left + dragXNorm).coerceIn(0f, right - PhotoCrop.MIN_SPAN)
            bottom = (bottom + dragYNorm).coerceIn(top + PhotoCrop.MIN_SPAN, 1f)
        }

        CropHandleCorner.BOTTOM_RIGHT -> {
            right = (right + dragXNorm).coerceIn(left + PhotoCrop.MIN_SPAN, 1f)
            bottom = (bottom + dragYNorm).coerceIn(top + PhotoCrop.MIN_SPAN, 1f)
        }
    }

    return PhotoCrop(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    ).normalized()
}

private fun decodeScaledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDimension: Int
): Bitmap {
    val source = ImageDecoder.createSource(contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val sourceWidth = info.size.width
        val sourceHeight = info.size.height
        val longestSide = max(sourceWidth, sourceHeight)
        if (longestSide > maxDimension) {
            val scale = maxDimension.toFloat() / longestSide.toFloat()
            val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
            decoder.setTargetSize(targetWidth, targetHeight)
        }
    }
}

private fun formatSliderValue(value: Float): String {
    return String.format(
        Locale.getDefault(),
        if (value >= 0f) "+%.2f" else "%.2f",
        value
    )
}
