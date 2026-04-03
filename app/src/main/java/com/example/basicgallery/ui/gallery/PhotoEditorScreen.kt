@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.basicgallery.ui.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.basicgallery.R
import com.example.basicgallery.data.PhotoEditingProcessor
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoCrop
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val PREVIEW_MAX_DIMENSION = 1600

internal const val PHOTO_EDITOR_SAVE_BUTTON_TAG = "photo_editor_save_button"
internal const val PHOTO_EDITOR_EXPOSURE_SLIDER_TAG = "photo_editor_exposure_slider"
internal const val PHOTO_EDITOR_BRIGHTNESS_SLIDER_TAG = "photo_editor_brightness_slider"
internal const val PHOTO_EDITOR_CONTRAST_SLIDER_TAG = "photo_editor_contrast_slider"
internal const val PHOTO_EDITOR_SHARPNESS_SLIDER_TAG = "photo_editor_sharpness_slider"
internal const val PHOTO_EDITOR_CROP_LEFT_SLIDER_TAG = "photo_editor_crop_left_slider"
internal const val PHOTO_EDITOR_CROP_TOP_SLIDER_TAG = "photo_editor_crop_top_slider"
internal const val PHOTO_EDITOR_CROP_RIGHT_SLIDER_TAG = "photo_editor_crop_right_slider"
internal const val PHOTO_EDITOR_CROP_BOTTOM_SLIDER_TAG = "photo_editor_crop_bottom_slider"

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
    var sourceBitmap by remember(sourcePhoto.id) { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember(sourcePhoto.id) { mutableStateOf<Bitmap?>(null) }
    var crop by remember(sourcePhoto.id) { mutableStateOf(PhotoCrop()) }
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
                val previousSource = sourceBitmap
                val previousPreview = previewBitmap
                sourceBitmap = bitmap
                previewBitmap = bitmap
                isLoading = false
                hasLoadingError = false
                if (previousPreview != null &&
                    previousPreview !== previousSource &&
                    previousPreview !== bitmap &&
                    !previousPreview.isRecycled
                ) {
                    previousPreview.recycle()
                }
                if (previousSource != null && previousSource !== bitmap && !previousSource.isRecycled) {
                    previousSource.recycle()
                }
            }
            .onFailure {
                hasLoadingError = true
                isLoading = false
            }
    }

    LaunchedEffect(sourceBitmap, adjustments, crop) {
        val original = sourceBitmap ?: return@LaunchedEffect

        if (adjustments.isIdentity() && crop.isFullImage()) {
            val oldPreview = previewBitmap
            previewBitmap = original
            if (oldPreview != null && oldPreview !== original && !oldPreview.isRecycled) {
                oldPreview.recycle()
            }
            return@LaunchedEffect
        }

        val processedPreview = withContext(Dispatchers.Default) {
            PhotoEditingProcessor.applyEdits(
                source = original,
                adjustments = adjustments,
                crop = crop
            )
        }
        val oldPreview = previewBitmap
        previewBitmap = processedPreview
        if (oldPreview != null &&
            oldPreview !== original &&
            oldPreview !== processedPreview &&
            !oldPreview.isRecycled
        ) {
            oldPreview.recycle()
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
            val result = onSavePhoto(sourcePhoto, adjustments, crop)
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
                        text = stringResource(id = R.string.photo_editor_crop_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_crop_left),
                        value = crop.left,
                        valueRange = 0f..(crop.right - PhotoCrop.MIN_SPAN),
                        sliderTag = PHOTO_EDITOR_CROP_LEFT_SLIDER_TAG,
                        valueFormatter = ::formatPercentValue,
                        onValueChange = { crop = crop.copy(left = it).normalized() }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_crop_top),
                        value = crop.top,
                        valueRange = 0f..(crop.bottom - PhotoCrop.MIN_SPAN),
                        sliderTag = PHOTO_EDITOR_CROP_TOP_SLIDER_TAG,
                        valueFormatter = ::formatPercentValue,
                        onValueChange = { crop = crop.copy(top = it).normalized() }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_crop_right),
                        value = crop.right,
                        valueRange = (crop.left + PhotoCrop.MIN_SPAN)..1f,
                        sliderTag = PHOTO_EDITOR_CROP_RIGHT_SLIDER_TAG,
                        valueFormatter = ::formatPercentValue,
                        onValueChange = { crop = crop.copy(right = it).normalized() }
                    )
                    AdjustmentSlider(
                        title = stringResource(id = R.string.photo_editor_crop_bottom),
                        value = crop.bottom,
                        valueRange = (crop.top + PhotoCrop.MIN_SPAN)..1f,
                        sliderTag = PHOTO_EDITOR_CROP_BOTTOM_SLIDER_TAG,
                        valueFormatter = ::formatPercentValue,
                        onValueChange = { crop = crop.copy(bottom = it).normalized() }
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

                hasLoadingError || previewBitmap == null -> {
                    Text(
                        text = stringResource(id = R.string.photo_editor_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                else -> {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(id = R.string.photo_content_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
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

private fun formatPercentValue(value: Float): String {
    return String.format(Locale.getDefault(), "%d%%", (value * 100f).roundToInt())
}
