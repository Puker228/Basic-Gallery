@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.basicgallery.ui.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.example.basicgallery.R
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal const val GALLERY_TOP_APP_BAR_TAG = "gallery_top_app_bar"
internal const val GALLERY_TAB_ROW_TAG = "gallery_tab_row"
internal const val GALLERY_TAB_PHOTOS_TAG = "gallery_tab_photos"
internal const val GALLERY_TAB_TRASH_TAG = "gallery_tab_trash"

internal enum class GalleryTab {
    PHOTOS,
    TRASH
}

private data class PendingMediaRequest(
    val closeViewerAfterSuccess: Boolean
)

@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { requiredReadPermissions() }
    val mediaImageLoader = rememberMediaImageLoader()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(context.hasGalleryReadPermission()) }
    var permissionRequestStarted by rememberSaveable { mutableStateOf(false) }
    var selectedMediaUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMediaOpenedFromTrash by rememberSaveable { mutableStateOf(false) }
    var editorPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorPhotoDateTakenMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var currentTabName by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS.name) }
    val currentTab = GalleryTab.valueOf(currentTabName)
    var pendingMediaRequest by remember { mutableStateOf<PendingMediaRequest?>(null) }
    val photosGridState = rememberLazyGridState()
    val trashGridState = rememberLazyGridState()
    val currentGridState = if (currentTab == GalleryTab.PHOTOS) {
        photosGridState
    } else {
        trashGridState
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestStarted = true
        hasPermission = context.hasGalleryReadPermission()
    }

    val mediaActionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val request = pendingMediaRequest
        pendingMediaRequest = null

        if (result.resultCode == Activity.RESULT_OK && request != null) {
            if (request.closeViewerAfterSuccess) {
                selectedMediaUri = null
            }
            viewModel.clearSelection()
            viewModel.loadPhotos(forceRefresh = true)
        }
    }

    fun launchMediaRequest(
        intentSender: IntentSender?,
        unsupportedMessageRes: Int,
        closeViewerAfterSuccess: Boolean = false
    ) {
        if (intentSender == null) {
            Toast.makeText(context, unsupportedMessageRes, Toast.LENGTH_SHORT).show()
            return
        }

        pendingMediaRequest = PendingMediaRequest(
            closeViewerAfterSuccess = closeViewerAfterSuccess
        )
        mediaActionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    fun launchMoveToTrashRequest(photoUris: List<Uri>, closeViewerAfterSuccess: Boolean) {
        if (photoUris.isEmpty()) return

        launchMediaRequest(
            intentSender = viewModel.createMoveToTrashRequest(photoUris),
            unsupportedMessageRes = R.string.trash_not_supported,
            closeViewerAfterSuccess = closeViewerAfterSuccess
        )
    }

    fun launchRestoreRequest(photoUris: List<Uri>) {
        if (photoUris.isEmpty()) return

        launchMediaRequest(
            intentSender = viewModel.createRestoreRequest(photoUris),
            unsupportedMessageRes = R.string.restore_not_supported
        )
    }

    fun launchDeleteRequest(photoUris: List<Uri>) {
        if (photoUris.isEmpty()) return

        launchMediaRequest(
            intentSender = viewModel.createDeleteRequest(photoUris),
            unsupportedMessageRes = R.string.delete_not_supported
        )
    }

    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermissionNow = context.hasGalleryReadPermission()
                hasPermission = hasPermissionNow

                if (hasPermissionNow) {
                    viewModel.loadPhotos(forceRefresh = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadPhotos()
        }
    }

    BackHandler(enabled = selectedMediaUri == null && uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val openedMediaUri = selectedMediaUri
        val editingPhotoUriValue = editorPhotoUri
        val editingPhotoIdValue = editorPhotoId
        val editingPhotoDateValue = editorPhotoDateTakenMillis
        when {
            editingPhotoUriValue != null &&
                    editingPhotoIdValue != null &&
                    editingPhotoDateValue != null -> {
                PhotoEditorScreen(
                    sourcePhoto = PhotoItem(
                        id = editingPhotoIdValue,
                        contentUri = Uri.parse(editingPhotoUriValue),
                        dateTakenMillis = editingPhotoDateValue,
                        mediaType = MediaType.PHOTO
                    ),
                    onBack = {
                        editorPhotoUri = null
                        editorPhotoId = null
                        editorPhotoDateTakenMillis = null
                    },
                    onSavePhoto = { sourcePhoto, adjustments ->
                        viewModel.saveEditedPhoto(sourcePhoto, adjustments)
                    },
                    onSaved = {
                        editorPhotoUri = null
                        editorPhotoId = null
                        editorPhotoDateTakenMillis = null
                        viewModel.loadPhotos(forceRefresh = true)
                    }
                )
            }

            openedMediaUri != null -> {
                val currentMedia = if (selectedMediaOpenedFromTrash) {
                    uiState.trashPhotos
                } else {
                    uiState.photos
                }
                val onDeleteRequest: ((Uri) -> Unit)? = if (selectedMediaOpenedFromTrash) {
                    null
                } else {
                    { uri ->
                        launchMoveToTrashRequest(
                            photoUris = listOf(uri),
                            closeViewerAfterSuccess = true
                        )
                    }
                }
                val mediaUri = Uri.parse(openedMediaUri)

                FullscreenMediaScreen(
                    mediaItems = currentMedia,
                    initialMediaUri = mediaUri,
                    onBack = { selectedMediaUri = null },
                    onEditPhoto = { media ->
                        editorPhotoUri = media.contentUri.toString()
                        editorPhotoId = media.id
                        editorPhotoDateTakenMillis = media.dateTakenMillis
                    },
                    onDelete = onDeleteRequest,
                    onCurrentMediaChanged = { currentMediaItem ->
                        selectedMediaUri = currentMediaItem.contentUri.toString()
                    }
                )
            }

            !hasPermission -> {
                PermissionRequiredScreen(
                    showSettingsButton = permissionRequestStarted &&
                            !context.shouldShowAnyPermissionRationale(permissions),
                    onRequestPermission = {
                        permissionRequestStarted = true
                        permissionLauncher.launch(permissions)
                    },
                    onOpenSettings = { context.openAppSettings() }
                )
            }

            else -> {
                GalleryScreen(
                    uiState = uiState,
                    imageLoader = mediaImageLoader,
                    currentTab = currentTab,
                    gridState = currentGridState,
                    onTabSelected = { tab ->
                        if (currentTab != tab) {
                            currentTabName = tab.name
                            viewModel.clearSelection()
                        }
                    },
                    onPhotoClick = { photo ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(photo.id)
                        } else {
                            selectedMediaUri = photo.contentUri.toString()
                            selectedMediaOpenedFromTrash = currentTab == GalleryTab.TRASH
                        }
                    },
                    onPhotoLongClick = { photo ->
                        viewModel.startSelection(photo.id)
                    },
                    onSelectPhotos = { photoIds ->
                        viewModel.selectPhotos(photoIds)
                    },
                    onDeleteSelected = {
                        val selectedUris = uiState.photos
                            .asSequence()
                            .filter { it.id in uiState.selectedPhotoIds }
                            .map { it.contentUri }
                            .toList()

                        launchMoveToTrashRequest(
                            photoUris = selectedUris,
                            closeViewerAfterSuccess = false
                        )
                    },
                    onRestoreSelected = {
                        val selectedUris = uiState.trashPhotos
                            .asSequence()
                            .filter { it.id in uiState.selectedPhotoIds }
                            .map { it.contentUri }
                            .toList()
                        launchRestoreRequest(selectedUris)
                    },
                    onDeleteSelectedFromTrash = {
                        val selectedUris = uiState.trashPhotos
                            .asSequence()
                            .filter { it.id in uiState.selectedPhotoIds }
                            .map { it.contentUri }
                            .toList()
                        launchDeleteRequest(selectedUris)
                    },
                    onDeleteAllFromTrash = {
                        launchDeleteRequest(
                            uiState.trashPhotos.map { it.contentUri }
                        )
                    },
                    onClearSelection = { viewModel.clearSelection() },
                    onRetry = { viewModel.loadPhotos(forceRefresh = true) }
                )
            }
        }
    }
}

@Composable
internal fun GalleryScreen(
    uiState: GalleryUiState,
    imageLoader: ImageLoader,
    currentTab: GalleryTab,
    gridState: LazyGridState,
    onTabSelected: (GalleryTab) -> Unit,
    onPhotoClick: (PhotoItem) -> Unit,
    onPhotoLongClick: (PhotoItem) -> Unit,
    onSelectPhotos: (Collection<Long>) -> Unit,
    onDeleteSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelectedFromTrash: () -> Unit,
    onDeleteAllFromTrash: () -> Unit,
    onClearSelection: () -> Unit,
    onRetry: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        if (configuration.locales.isEmpty) {
            Locale.getDefault()
        } else {
            configuration.locales[0]
        }
    }
    val isTrashTab = currentTab == GalleryTab.TRASH
    val currentPhotos = if (isTrashTab) uiState.trashPhotos else uiState.photos
    val currentPhotoCount = if (isTrashTab) uiState.trashPhotoCount else uiState.photoCount
    val currentVideoCount = if (isTrashTab) uiState.trashVideoCount else uiState.videoCount

    val todayLabel = stringResource(id = R.string.gallery_date_today)
    val yesterdayLabel = stringResource(id = R.string.gallery_date_yesterday)
    val photoSections = remember(currentPhotos, locale, todayLabel, yesterdayLabel) {
        groupPhotosByDay(
            photos = currentPhotos,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            locale = locale
        )
    }
    val density = LocalDensity.current
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    val revealDistancePx = with(density) { 72.dp.toPx() }
    val maxRevealDistancePx = with(density) { 140.dp.toPx() }
    val revealProgress = (pullDistancePx / revealDistancePx).coerceIn(0f, 1f)
    val mediaCountText = stringResource(
        id = R.string.gallery_media_count,
        currentPhotoCount,
        currentVideoCount
    )
    val emptyStateText = if (isTrashTab) {
        stringResource(id = R.string.trash_empty_state)
    } else {
        stringResource(id = R.string.gallery_empty_state)
    }

    val pullToRevealConnection = remember(maxRevealDistancePx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val deltaY = available.y
                if (deltaY == 0f) return Offset.Zero

                pullDistancePx = (pullDistancePx + deltaY).coerceIn(0f, maxRevealDistancePx)
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                pullDistancePx = 0f
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(currentTab) {
        pullDistancePx = 0f
    }
    LaunchedEffect(gridState.isScrollInProgress) {
        if (!gridState.isScrollInProgress && pullDistancePx > 0f) {
            pullDistancePx = 0f
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.testTag(GALLERY_TOP_APP_BAR_TAG),
                    title = {
                        if (uiState.isSelectionMode) {
                            Text(
                                text = stringResource(
                                    id = R.string.gallery_selected_count,
                                    uiState.selectedCount
                                )
                            )
                        } else {
                            GallerySectionTabs(
                                currentTab = currentTab,
                                onTabSelected = onTabSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    actions = {
                        if (uiState.isSelectionMode) {
                            if (isTrashTab) {
                                TrashSelectionPopupMenu(
                                    onRestoreSelected = onRestoreSelected,
                                    onDeleteSelected = onDeleteSelectedFromTrash,
                                    hasSelection = uiState.selectedCount > 0
                                )
                            } else {
                                TextButton(
                                    onClick = onDeleteSelected,
                                    enabled = uiState.selectedCount > 0
                                ) {
                                    Text(text = stringResource(id = R.string.delete))
                                }
                            }
                            TextButton(onClick = onClearSelection) {
                                Text(text = stringResource(id = R.string.cancel))
                            }
                        }
                    }
                )

                if (!uiState.isSelectionMode && isTrashTab) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        TextButton(
                            onClick = onDeleteAllFromTrash,
                            enabled = uiState.trashPhotos.isNotEmpty()
                        ) {
                            Text(text = stringResource(id = R.string.delete_all))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .nestedScroll(pullToRevealConnection)
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && currentPhotos.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.errorMessage != null && currentPhotos.isEmpty() -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                currentPhotos.isEmpty() -> {
                    Text(
                        text = emptyStateText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        photoSections.forEach { section ->
                            item(
                                key = "header_${section.day.toEpochDay()}",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "day_header"
                            ) {
                                DaySectionHeader(
                                    label = section.label,
                                    showSelectAll = !isTrashTab,
                                    isSelectAllEnabled = section.photos.any { photo ->
                                        photo.id !in uiState.selectedPhotoIds
                                    },
                                    onSelectAll = {
                                        onSelectPhotos(section.photos.map { photo -> photo.id })
                                    }
                                )
                            }

                            items(
                                items = section.photos,
                                key = { it.id },
                                contentType = { "photo" }
                            ) { photo ->
                                PhotoGridItem(
                                    photo = photo,
                                    imageLoader = imageLoader,
                                    isSelected = photo.id in uiState.selectedPhotoIds,
                                    onClick = { onPhotoClick(photo) },
                                    onLongClick = { onPhotoLongClick(photo) }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && currentPhotos.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            if (revealProgress > 0f) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = revealProgress }
                ) {
                    Text(
                        text = mediaCountText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySectionTabs(
    currentTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val photosLabel = stringResource(id = R.string.tab_photos)
    val trashLabel = stringResource(id = R.string.tab_trash)
    val tabs = listOf(
        GalleryTab.PHOTOS to photosLabel,
        GalleryTab.TRASH to trashLabel
    )
    val selectedTabIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.testTag(GALLERY_TAB_ROW_TAG),
        divider = {}
    ) {
        tabs.forEachIndexed { index, (tab, label) ->
            Tab(
                modifier = Modifier.testTag(
                    if (tab == GalleryTab.PHOTOS) GALLERY_TAB_PHOTOS_TAG else GALLERY_TAB_TRASH_TAG
                ),
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(tab) },
                text = { Text(text = label) }
            )
        }
    }
}

@Composable
private fun TrashSelectionPopupMenu(
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    hasSelection: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { isExpanded = true },
            enabled = hasSelection
        ) {
            Text(text = stringResource(id = R.string.actions))
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.restore)) },
                onClick = {
                    isExpanded = false
                    onRestoreSelected()
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.delete)) },
                onClick = {
                    isExpanded = false
                    onDeleteSelected()
                }
            )
        }
    }
}

@Composable
private fun DaySectionHeader(
    label: String,
    showSelectAll: Boolean,
    isSelectAllEnabled: Boolean,
    onSelectAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showSelectAll) {
            TextButton(
                onClick = onSelectAll,
                enabled = isSelectAllEnabled
            ) {
                Text(text = stringResource(id = R.string.select_all))
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: PhotoItem,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        val itemSizePx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val request = remember(photo.id, photo.contentUri, context, itemSizePx) {
            ImageRequest.Builder(context)
                .data(photo.contentUri)
                .memoryCacheKey(photo.id.toString())
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .allowHardware(true)
                .size(itemSizePx, itemSizePx)
                .apply {
                    if (photo.mediaType == MediaType.VIDEO) {
                        videoFrameMillis(0)
                    }
                }
                .build()
        }

        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }

        if (photo.mediaType == MediaType.VIDEO) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = formatVideoDuration(photo.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun rememberMediaImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}

@Composable
private fun FullscreenMediaScreen(
    mediaItems: List<PhotoItem>,
    initialMediaUri: Uri,
    onBack: () -> Unit,
    onEditPhoto: (PhotoItem) -> Unit,
    onDelete: ((Uri) -> Unit)?,
    onCurrentMediaChanged: (PhotoItem) -> Unit
) {
    BackHandler(onBack = onBack)

    if (mediaItems.isEmpty()) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val initialPage = remember(mediaItems, initialMediaUri) {
        mediaItems.indexOfFirst { it.contentUri == initialMediaUri }
    }
    if (initialPage < 0) {
        LaunchedEffect(initialMediaUri, mediaItems.size) {
            onBack()
        }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { mediaItems.size }
    )
    LaunchedEffect(mediaItems.size) {
        val lastIndex = mediaItems.lastIndex
        if (lastIndex >= 0 && pagerState.currentPage > lastIndex) {
            pagerState.scrollToPage(lastIndex)
        }
    }
    val currentMedia = mediaItems[pagerState.currentPage.coerceIn(0, mediaItems.lastIndex)]
    val dateTimeLabel = rememberMediaDateTimeLabel(dateTakenMillis = currentMedia.dateTakenMillis)
    LaunchedEffect(currentMedia.id) {
        onCurrentMediaChanged(currentMedia)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = dateTimeLabel) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(id = R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (currentMedia.mediaType == MediaType.PHOTO) {
                        TextButton(onClick = { onEditPhoto(currentMedia) }) {
                            Text(text = stringResource(id = R.string.edit))
                        }
                        TextButton(
                            onClick = { onDelete?.invoke(currentMedia.contentUri) },
                            enabled = onDelete != null
                        ) {
                            Text(text = stringResource(id = R.string.delete))
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { onDelete?.invoke(currentMedia.contentUri) },
                            enabled = onDelete != null
                        ) {
                            Text(text = stringResource(id = R.string.delete))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.scrim)
        ) { page ->
            val item = mediaItems[page]
            when (item.mediaType) {
                MediaType.PHOTO -> FullscreenPhotoPage(photoUri = item.contentUri)
                MediaType.VIDEO -> FullscreenVideoPage(
                    videoUri = item.contentUri,
                    isCurrentPage = page == pagerState.currentPage
                )
            }
        }
    }
}

@Composable
private fun FullscreenPhotoPage(photoUri: Uri) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var scale by remember(photoUri) { mutableFloatStateOf(1f) }
    var translation by remember(photoUri) { mutableStateOf(Offset.Zero) }
    val doubleTapScale = 2.5f

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val width = widthPx.toFloat()
        val height = heightPx.toFloat()
        val request = remember(photoUri, context, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(photoUri)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .precision(Precision.EXACT)
                .crossfade(false)
                .allowHardware(true)
                .size(widthPx, heightPx)
                .build()
        }
        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val updatedScale = (scale * zoomChange).coerceIn(1f, 5f)
            val targetTranslation = if (updatedScale <= 1f) {
                Offset.Zero
            } else {
                translation + panChange
            }
            scale = updatedScale
            translation = clampTranslation(
                currentScale = updatedScale,
                currentTranslation = targetTranslation,
                width = width,
                height = height
            )
        }

        AsyncImage(
            model = request,
            contentDescription = stringResource(id = R.string.photo_content_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(photoUri, widthPx, heightPx) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                scale = 1f
                                translation = Offset.Zero
                            } else {
                                val center = Offset(width / 2f, height / 2f)
                                val delta = tapOffset - center
                                val targetScale = doubleTapScale
                                val targetTranslation = Offset(
                                    x = -delta.x * (targetScale - 1f),
                                    y = -delta.y * (targetScale - 1f)
                                )

                                scale = targetScale
                                translation = clampTranslation(
                                    currentScale = targetScale,
                                    currentTranslation = targetTranslation,
                                    width = width,
                                    height = height
                                )
                            }
                        }
                    )
                }
                .pointerInput(photoUri, scale, widthPx, heightPx) {
                    if (scale <= 1f) return@pointerInput

                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        translation = clampTranslation(
                            currentScale = scale,
                            currentTranslation = translation + Offset(dragAmount.x, dragAmount.y),
                            width = width,
                            height = height
                        )
                    }
                }
                .transformable(
                    state = transformableState,
                    canPan = { scale > 1f }
                )
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                }
        )
    }
}

private fun clampTranslation(
    currentScale: Float,
    currentTranslation: Offset,
    width: Float,
    height: Float
): Offset {
    if (currentScale <= 1f) return Offset.Zero

    val maxX = ((currentScale - 1f) * width) / 2f
    val maxY = ((currentScale - 1f) * height) / 2f

    return Offset(
        x = currentTranslation.x.coerceIn(-maxX, maxX),
        y = currentTranslation.y.coerceIn(-maxY, maxY)
    )
}

@Composable
private fun FullscreenVideoPage(
    videoUri: Uri,
    isCurrentPage: Boolean
) {
    key(videoUri) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    val controller = MediaController(context).also {
                        it.setAnchorView(this)
                    }
                    setMediaController(controller)
                    setVideoURI(videoUri)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = false
                        if (isCurrentPage) {
                            start()
                        }
                    }
                }
            },
            update = { view ->
                if (isCurrentPage) {
                    if (!view.isPlaying) {
                        view.start()
                    }
                } else if (view.isPlaying) {
                    view.pause()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun rememberMediaDateTimeLabel(dateTakenMillis: Long): String {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        if (configuration.locales.isEmpty) {
            Locale.getDefault()
        } else {
            configuration.locales[0]
        }
    }
    return remember(dateTakenMillis, locale) {
        formatMediaDateTime(
            timestampMillis = dateTakenMillis,
            locale = locale
        )
    }
}

private fun formatMediaDateTime(
    timestampMillis: Long,
    locale: Locale,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
    return Instant
        .ofEpochMilli(timestampMillis.coerceAtLeast(0L))
        .atZone(zoneId)
        .format(formatter)
}

private fun formatVideoDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        val remainingMinutes = (totalSeconds % 3_600L) / 60L
        String.format(Locale.US, "%02d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun PermissionRequiredScreen(
    showSettingsButton: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.gallery_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.permission_message),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermission) {
                    Text(text = stringResource(id = R.string.permission_grant_button))
                }
                if (showSettingsButton) {
                    Button(onClick = onOpenSettings) {
                        Text(text = stringResource(id = R.string.permission_settings_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.retry))
        }
    }
}

private fun requiredReadPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.hasGalleryReadPermission(): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> {
        hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    Build.VERSION.SDK_INT >= 33 -> hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
    else -> hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.shouldShowAnyPermissionRationale(permissions: Array<String>): Boolean {
    val activity = findActivity() ?: return false
    return permissions.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
