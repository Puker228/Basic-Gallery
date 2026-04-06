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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.IntOffset
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
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.example.basicgallery.R
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val GALLERY_TOP_APP_BAR_TAG = "gallery_top_app_bar"
internal const val GALLERY_TAB_ROW_TAG = "gallery_tab_row"
internal const val GALLERY_TAB_PHOTOS_TAG = "gallery_tab_photos"
internal const val GALLERY_TAB_TRASH_TAG = "gallery_tab_trash"
internal const val GALLERY_CONTENT_PAGER_TAG = "gallery_content_pager"
internal const val GALLERY_GRID_TAG = "gallery_grid"
internal const val GALLERY_SCROLLBAR_TAG = "gallery_scrollbar"
internal const val GALLERY_SCROLLBAR_HINT_TAG = "gallery_scrollbar_hint"
internal const val FULLSCREEN_TOP_APP_BAR_TAG = "fullscreen_top_app_bar"
internal const val FULLSCREEN_BOTTOM_BAR_TAG = "fullscreen_bottom_bar"

private val SelectionCheckboxBlue = Color(0xFF0C84FF)
private val SelectionActionBackground = Color(0xFFF5F5F5)
private val SelectionActionTextColor = Color(0xFF333333)

internal enum class GalleryTab {
    PHOTOS,
    TRASH
}

private val GalleryTab.pageIndex: Int
    get() = when (this) {
        GalleryTab.PHOTOS -> 0
        GalleryTab.TRASH -> 1
    }

private fun tabForPage(pageIndex: Int): GalleryTab {
    return if (pageIndex == GalleryTab.TRASH.pageIndex) {
        GalleryTab.TRASH
    } else {
        GalleryTab.PHOTOS
    }
}

private data class PendingMediaRequest(
    val closeViewerAfterSuccess: Boolean
)

internal data class GridScrollbarMetrics(
    val thumbOffsetFraction: Float,
    val thumbHeightFraction: Float
)

internal data class GridTimelineSectionAnchor(
    val day: LocalDate,
    val startIndex: Int,
    val endIndexExclusive: Int
)

internal data class GridTimelineYearMarker(
    val year: Int,
    val offsetFraction: Float
)

internal fun calculateGridScrollbarMetrics(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    visibleItemsCount: Int,
    minThumbHeightFraction: Float = 0.12f
): GridScrollbarMetrics? {
    if (totalItemsCount <= 0 || visibleItemsCount <= 0 || visibleItemsCount >= totalItemsCount) {
        return null
    }

    val clampedMinFraction = minThumbHeightFraction.coerceIn(0f, 1f)
    val thumbHeightFraction = (visibleItemsCount.toFloat() / totalItemsCount.toFloat())
        .coerceIn(clampedMinFraction, 1f)
    val maxFirstIndex = (totalItemsCount - visibleItemsCount).coerceAtLeast(1)
    val thumbOffsetFraction = (firstVisibleItemIndex.toFloat() / maxFirstIndex.toFloat())
        .coerceIn(0f, 1f)

    return GridScrollbarMetrics(
        thumbOffsetFraction = thumbOffsetFraction,
        thumbHeightFraction = thumbHeightFraction
    )
}

internal fun buildTimelineSectionAnchors(
    sections: List<PhotoDaySection>
): List<GridTimelineSectionAnchor> {
    if (sections.isEmpty()) return emptyList()

    val anchors = ArrayList<GridTimelineSectionAnchor>(sections.size)
    var currentIndex = 0

    sections.forEach { section ->
        val sectionSize = (section.photos.size + 1).coerceAtLeast(1)
        val startIndex = currentIndex
        val endIndexExclusive = startIndex + sectionSize

        anchors += GridTimelineSectionAnchor(
            day = section.day,
            startIndex = startIndex,
            endIndexExclusive = endIndexExclusive
        )
        currentIndex = endIndexExclusive
    }

    return anchors
}

internal fun gridIndexForScrollbarFraction(
    fraction: Float,
    totalItemsCount: Int
): Int {
    if (totalItemsCount <= 1) return 0

    val maxIndex = totalItemsCount - 1
    return (fraction.coerceIn(0f, 1f) * maxIndex.toFloat())
        .roundToInt()
        .coerceIn(0, maxIndex)
}

internal fun findTimelineDayForGridIndex(
    itemIndex: Int,
    timelineSectionAnchors: List<GridTimelineSectionAnchor>
): LocalDate? {
    if (timelineSectionAnchors.isEmpty()) return null

    val lastIndex = timelineSectionAnchors.last().endIndexExclusive - 1
    if (lastIndex < 0) return null
    val clampedIndex = itemIndex.coerceIn(0, lastIndex)

    var left = 0
    var right = timelineSectionAnchors.lastIndex

    while (left <= right) {
        val mid = (left + right).ushr(1)
        val anchor = timelineSectionAnchors[mid]
        when {
            clampedIndex < anchor.startIndex -> right = mid - 1
            clampedIndex >= anchor.endIndexExclusive -> left = mid + 1
            else -> return anchor.day
        }
    }

    return timelineSectionAnchors.lastOrNull { clampedIndex >= it.startIndex }?.day
}

internal fun calculateTimelineYearMarkers(
    timelineSectionAnchors: List<GridTimelineSectionAnchor>,
    totalItemsCount: Int
): List<GridTimelineYearMarker> {
    if (timelineSectionAnchors.isEmpty() || totalItemsCount <= 1) return emptyList()

    val yearToStartIndex = LinkedHashMap<Int, Int>()
    timelineSectionAnchors.forEach { anchor ->
        yearToStartIndex.putIfAbsent(anchor.day.year, anchor.startIndex)
    }

    val maxIndex = (totalItemsCount - 1).coerceAtLeast(1).toFloat()
    return yearToStartIndex.map { (year, startIndex) ->
        GridTimelineYearMarker(
            year = year,
            offsetFraction = (startIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        )
    }
}

internal fun formatTimelineMonthYearLabel(
    day: LocalDate,
    locale: Locale
): String {
    val rawLabel = day
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replace(".", "")

    return rawLabel.replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase(locale)
        } else {
            firstChar.toString()
        }
    }
}

internal fun createFullscreenDeleteHandler(
    openedFromTrash: Boolean,
    launchMoveToTrashRequest: (photoUris: List<Uri>, closeViewerAfterSuccess: Boolean) -> Unit,
    launchDeleteRequest: (photoUris: List<Uri>, closeViewerAfterSuccess: Boolean) -> Unit
): (Uri) -> Unit {
    return if (openedFromTrash) {
        { uri ->
            launchDeleteRequest(
                listOf(uri),
                true
            )
        }
    } else {
        { uri ->
            launchMoveToTrashRequest(
                listOf(uri),
                true
            )
        }
    }
}

@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { requiredReadPermissions() }
    val mediaImageLoader = rememberMediaImageLoader()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(mediaImageLoader) {
        onDispose {
            mediaImageLoader.shutdown()
        }
    }

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

    fun launchDeleteRequest(photoUris: List<Uri>, closeViewerAfterSuccess: Boolean = false) {
        if (photoUris.isEmpty()) return

        launchMediaRequest(
            intentSender = viewModel.createDeleteRequest(photoUris),
            unsupportedMessageRes = R.string.delete_not_supported,
            closeViewerAfterSuccess = closeViewerAfterSuccess
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
                    onSavePhoto = { sourcePhoto, adjustments, crop ->
                        viewModel.saveEditedPhoto(sourcePhoto, adjustments, crop)
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
                val onEditPhotoRequest: ((PhotoItem) -> Unit)? = if (selectedMediaOpenedFromTrash) {
                    null
                } else {
                    { media ->
                        editorPhotoUri = media.contentUri.toString()
                        editorPhotoId = media.id
                        editorPhotoDateTakenMillis = media.dateTakenMillis
                    }
                }
                val onDeleteRequest = createFullscreenDeleteHandler(
                    openedFromTrash = selectedMediaOpenedFromTrash,
                    launchMoveToTrashRequest = ::launchMoveToTrashRequest,
                    launchDeleteRequest = ::launchDeleteRequest
                )
                val mediaUri = Uri.parse(openedMediaUri)

                FullscreenMediaScreen(
                    mediaItems = currentMedia,
                    initialMediaUri = mediaUri,
                    onBack = { selectedMediaUri = null },
                    onEditPhoto = onEditPhotoRequest,
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
                    photosGridState = photosGridState,
                    trashGridState = trashGridState,
                    onTabSelected = { tab ->
                        if (currentTabName != tab.name) {
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
                    onDeselectPhotos = { photoIds ->
                        viewModel.deselectPhotos(photoIds)
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
    photosGridState: LazyGridState,
    trashGridState: LazyGridState,
    onTabSelected: (GalleryTab) -> Unit,
    onPhotoClick: (PhotoItem) -> Unit,
    onPhotoLongClick: (PhotoItem) -> Unit,
    onSelectPhotos: (Collection<Long>) -> Unit,
    onDeselectPhotos: (Collection<Long>) -> Unit,
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
    val currentGridState = if (isTrashTab) trashGridState else photosGridState

    val todayLabel = stringResource(id = R.string.gallery_date_today)
    val yesterdayLabel = stringResource(id = R.string.gallery_date_yesterday)
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
    val pagerState = rememberPagerState(
        initialPage = currentTab.pageIndex,
        pageCount = { GalleryTab.entries.size }
    )
    val latestCurrentTab by rememberUpdatedState(currentTab)
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    LaunchedEffect(currentTab) {
        val targetPage = currentTab.pageIndex
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                val tab = tabForPage(page)
                if (tab != latestCurrentTab) {
                    latestOnTabSelected(tab)
                }
            }
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
    LaunchedEffect(currentGridState.isScrollInProgress) {
        if (!currentGridState.isScrollInProgress && pullDistancePx > 0f) {
            pullDistancePx = 0f
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (uiState.isSelectionMode) {
                    CenterAlignedTopAppBar(
                        modifier = Modifier.testTag(GALLERY_TOP_APP_BAR_TAG),
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                CloseSelectionIcon()
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(
                                    id = R.string.gallery_selected_count,
                                    uiState.selectedCount
                                )
                            )
                        },
                        actions = {
                            if (isTrashTab) {
                                TrashSelectionPopupMenu(
                                    onRestoreSelected = onRestoreSelected,
                                    onDeleteSelected = onDeleteSelectedFromTrash,
                                    hasSelection = uiState.selectedCount > 0
                                )
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier.testTag(GALLERY_TOP_APP_BAR_TAG),
                        title = {
                            GallerySectionTabs(
                                currentTab = currentTab,
                                onTabSelected = onTabSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }

            }
        },
        bottomBar = {
            when {
                uiState.isSelectionMode && !isTrashTab -> {
                    GalleryBottomActionBar(
                        onAction = onDeleteSelected,
                        enabled = uiState.selectedCount > 0,
                        label = stringResource(id = R.string.delete)
                    )
                }

                !uiState.isSelectionMode && isTrashTab -> {
                    GalleryBottomActionBar(
                        onAction = onDeleteAllFromTrash,
                        enabled = uiState.trashPhotos.isNotEmpty(),
                        label = stringResource(id = R.string.delete)
                    )
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
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !uiState.isSelectionMode,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(GALLERY_CONTENT_PAGER_TAG)
            ) { page ->
                val pageTab = tabForPage(page)
                val isTrashPage = pageTab == GalleryTab.TRASH
                val pagePhotos = if (isTrashPage) uiState.trashPhotos else uiState.photos
                val pageGridState = if (isTrashPage) trashGridState else photosGridState
                val pagePhotoSections = remember(pagePhotos, locale, todayLabel, yesterdayLabel) {
                    groupPhotosByDay(
                        photos = pagePhotos,
                        todayLabel = todayLabel,
                        yesterdayLabel = yesterdayLabel,
                        locale = locale
                    )
                }
                val timelineSectionAnchors = remember(pagePhotoSections) {
                    buildTimelineSectionAnchors(pagePhotoSections)
                }
                val emptyStateText = if (isTrashPage) {
                    stringResource(id = R.string.trash_empty_state)
                } else {
                    stringResource(id = R.string.gallery_empty_state)
                }

                val scrollbarMetrics by remember(pageGridState) {
                    derivedStateOf {
                        val layoutInfo = pageGridState.layoutInfo
                        val firstVisibleIndex = layoutInfo.visibleItemsInfo
                            .minOfOrNull { item -> item.index }
                            ?: return@derivedStateOf null
                        calculateGridScrollbarMetrics(
                            totalItemsCount = layoutInfo.totalItemsCount,
                            firstVisibleItemIndex = firstVisibleIndex,
                            visibleItemsCount = layoutInfo.visibleItemsInfo.size
                        )
                    }
                }
                val showScrollbar = scrollbarMetrics != null

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading && pagePhotos.isEmpty() -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        uiState.errorMessage != null && pagePhotos.isEmpty() -> {
                            ErrorState(
                                message = uiState.errorMessage,
                                onRetry = onRetry,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        pagePhotos.isEmpty() -> {
                            Text(
                                text = emptyStateText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    state = pageGridState,
                                    contentPadding = PaddingValues(2.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag(GALLERY_GRID_TAG)
                                ) {
                                    pagePhotoSections.forEach { section ->
                                        item(
                                            key = "header_${section.day.toEpochDay()}",
                                            span = { GridItemSpan(maxLineSpan) },
                                            contentType = "day_header"
                                        ) {
                                            val sectionPhotoIds = section.photos.map { photo -> photo.id }
                                            val isSectionFullySelected = sectionPhotoIds.isNotEmpty() &&
                                                sectionPhotoIds.all { photoId ->
                                                    photoId in uiState.selectedPhotoIds
                                                }
                                            DaySectionHeader(
                                                label = section.label,
                                                showSelectionAction = !isTrashPage,
                                                isSectionFullySelected = isSectionFullySelected,
                                                onToggleSectionSelection = {
                                                    if (isSectionFullySelected) {
                                                        onDeselectPhotos(sectionPhotoIds)
                                                    } else {
                                                        onSelectPhotos(sectionPhotoIds)
                                                    }
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
                                                isSelectionMode = uiState.isSelectionMode,
                                                isSelected = photo.id in uiState.selectedPhotoIds,
                                                onClick = { onPhotoClick(photo) },
                                                onLongClick = { onPhotoLongClick(photo) }
                                            )
                                        }
                                    }
                                }

                                if (showScrollbar && scrollbarMetrics != null) {
                                    GalleryGridScrollbar(
                                        gridState = pageGridState,
                                        metrics = scrollbarMetrics,
                                        timelineSectionAnchors = timelineSectionAnchors,
                                        locale = locale,
                                        isListScrolling = pageGridState.isScrollInProgress,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(vertical = 6.dp, horizontal = 4.dp)
                                            .testTag(GALLERY_SCROLLBAR_TAG)
                                    )
                                }
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
private fun GalleryGridScrollbar(
    gridState: LazyGridState,
    metrics: GridScrollbarMetrics?,
    timelineSectionAnchors: List<GridTimelineSectionAnchor>,
    locale: Locale,
    isListScrolling: Boolean,
    modifier: Modifier = Modifier
) {
    if (metrics == null) return

    val layoutInfo = gridState.layoutInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount <= 0) return

    val defaultIndex = layoutInfo.visibleItemsInfo
        .minOfOrNull { item -> item.index }
        ?: 0
    val scope = rememberCoroutineScope()

    var isScrubbing by remember(gridState) { mutableStateOf(false) }
    var scrubFraction by remember(gridState) { mutableFloatStateOf(0f) }
    var lastRequestedIndex by remember(gridState) { mutableIntStateOf(-1) }

    val activeThumbOffsetFraction = if (isScrubbing) {
        scrubFraction
    } else {
        metrics.thumbOffsetFraction
    }

    val activeItemIndex = if (isScrubbing) {
        gridIndexForScrollbarFraction(scrubFraction, totalItemsCount)
    } else {
        defaultIndex
    }

    val monthYearLabel = remember(activeItemIndex, timelineSectionAnchors, locale) {
        findTimelineDayForGridIndex(
            itemIndex = activeItemIndex,
            timelineSectionAnchors = timelineSectionAnchors
        )?.let { day ->
            formatTimelineMonthYearLabel(
                day = day,
                locale = locale
            )
        }
    }
    val yearMarkers = remember(timelineSectionAnchors, totalItemsCount) {
        calculateTimelineYearMarkers(
            timelineSectionAnchors = timelineSectionAnchors,
            totalItemsCount = totalItemsCount
        )
    }

    fun updateScrubFraction(rawFraction: Float) {
        val clampedFraction = rawFraction.coerceIn(0f, 1f)
        scrubFraction = clampedFraction

        val targetIndex = gridIndexForScrollbarFraction(
            fraction = clampedFraction,
            totalItemsCount = totalItemsCount
        )
        if (targetIndex == lastRequestedIndex) return

        lastRequestedIndex = targetIndex
        scope.launch {
            gridState.scrollToItem(targetIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(0.92f)
            .width(108.dp)
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val thumbHeightPx = (trackHeightPx * metrics.thumbHeightFraction).coerceIn(0f, trackHeightPx)
        val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbTopPx = (activeThumbOffsetFraction * maxThumbOffsetPx).coerceIn(0f, maxThumbOffsetPx)
        val thumbCenterPx = thumbTopPx + thumbHeightPx / 2f
        val bubbleHeightPx = with(density) { 32.dp.toPx() }
        val bubbleTopPx = (thumbCenterPx - bubbleHeightPx / 2f)
            .coerceIn(0f, (trackHeightPx - bubbleHeightPx).coerceAtLeast(0f))
        val markerHeightPx = with(density) { 16.dp.toPx() }

        val isActive = isScrubbing || isListScrolling
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isActive) 0.2f else 0.11f)
        val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isActive) 0.74f else 0.52f)
        val markerDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

        if (isScrubbing) {
            yearMarkers.forEach { marker ->
                val markerCenterPx = marker.offsetFraction * trackHeightPx
                val markerOffsetY = (markerCenterPx - markerHeightPx / 2f)
                    .coerceIn(0f, (trackHeightPx - markerHeightPx).coerceAtLeast(0f))
                    .roundToInt()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(x = 0, y = markerOffsetY) }
                ) {
                    Canvas(modifier = Modifier.width(4.dp).height(4.dp)) {
                        drawCircle(
                            color = markerDotColor,
                            radius = size.minDimension / 2f
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = marker.year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isScrubbing && !monthYearLabel.isNullOrEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -with(density) { 34.dp.roundToPx() },
                            y = bubbleTopPx.roundToInt()
                        )
                    }
                    .testTag(GALLERY_SCROLLBAR_HINT_TAG)
            ) {
                Text(
                    text = monthYearLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(totalItemsCount) {
                    detectTapGestures { tapOffset ->
                        isScrubbing = true
                        val fraction = tapOffset.y / size.height.toFloat().coerceAtLeast(1f)
                        updateScrubFraction(fraction)
                        isScrubbing = false
                        lastRequestedIndex = -1
                    }
                }
                .pointerInput(totalItemsCount) {
                    detectDragGestures(
                        onDragStart = { dragStart ->
                            isScrubbing = true
                            val fraction = dragStart.y / size.height.toFloat().coerceAtLeast(1f)
                            updateScrubFraction(fraction)
                        },
                        onDragEnd = {
                            isScrubbing = false
                            lastRequestedIndex = -1
                        },
                        onDragCancel = {
                            isScrubbing = false
                            lastRequestedIndex = -1
                        }
                    ) { change, _ ->
                        change.consume()
                        val fraction = change.position.y / size.height.toFloat().coerceAtLeast(1f)
                        updateScrubFraction(fraction)
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(4.dp)
            ) {
                if (size.height <= 0f || size.width <= 0f) return@Canvas

                val cornerRadius = CornerRadius(x = size.width / 2f, y = size.width / 2f)
                drawRoundRect(
                    color = trackColor,
                    size = size,
                    cornerRadius = cornerRadius
                )

                val thumbHeight = (size.height * metrics.thumbHeightFraction).coerceIn(0f, size.height)
                val maxThumbOffset = (size.height - thumbHeight).coerceAtLeast(0f)
                val thumbTop = (activeThumbOffsetFraction * maxThumbOffset).coerceIn(0f, maxThumbOffset)

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(x = 0f, y = thumbTop),
                    size = Size(width = size.width, height = thumbHeight),
                    cornerRadius = cornerRadius
                )
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
private fun GalleryBottomActionBar(
    onAction: () -> Unit,
    enabled: Boolean,
    label: String
) {
    Surface(
        color = Color.White,
        tonalElevation = 3.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Button(
                onClick = onAction,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SelectionActionBackground,
                    contentColor = SelectionActionTextColor
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp)
            ) {
                Text(text = label)
            }
        }
    }
}

@Composable
private fun CloseSelectionIcon(modifier: Modifier = Modifier) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = modifier
            .width(18.dp)
            .height(18.dp)
    ) {
        val strokeWidth = size.minDimension * 0.14f
        drawLine(
            color = iconColor,
            start = Offset(x = size.width * 0.2f, y = size.height * 0.2f),
            end = Offset(x = size.width * 0.8f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = iconColor,
            start = Offset(x = size.width * 0.8f, y = size.height * 0.2f),
            end = Offset(x = size.width * 0.2f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(22.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    SelectionCheckboxBlue
                } else {
                    Color.White.copy(alpha = 0.96f)
                }
            )
            .then(
                if (isSelected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFBDBDBD),
                        shape = CircleShape
                    )
                }
            )
    ) {
        if (isSelected) {
            Canvas(
                modifier = Modifier
                    .width(11.dp)
                    .height(11.dp)
            ) {
                val strokeWidth = size.minDimension * 0.2f
                drawLine(
                    color = Color.White,
                    start = Offset(x = size.width * 0.1f, y = size.height * 0.55f),
                    end = Offset(x = size.width * 0.42f, y = size.height * 0.86f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(x = size.width * 0.42f, y = size.height * 0.86f),
                    end = Offset(x = size.width * 0.9f, y = size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun DaySectionHeader(
    label: String,
    showSelectionAction: Boolean,
    isSectionFullySelected: Boolean,
    onToggleSectionSelection: () -> Unit
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
        if (showSelectionAction) {
            Button(
                onClick = onToggleSectionSelection,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SelectionActionBackground,
                    contentColor = SelectionActionTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSectionFullySelected) {
                        stringResource(id = R.string.cancel_selection)
                    } else {
                        stringResource(id = R.string.select_all)
                    }
                )
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: PhotoItem,
    imageLoader: ImageLoader,
    isSelectionMode: Boolean,
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
                .diskCachePolicy(CachePolicy.DISABLED)
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
                    .background(SelectionCheckboxBlue.copy(alpha = 0.28f))
            )
        }

        if (isSelectionMode) {
            SelectionCheckbox(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
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
    val appContext = context.applicationContext
    return remember(appContext) {
        ImageLoader.Builder(appContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil_media_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}

@Composable
internal fun FullscreenMediaScreen(
    mediaItems: List<PhotoItem>,
    initialMediaUri: Uri,
    onBack: () -> Unit,
    onEditPhoto: ((PhotoItem) -> Unit)?,
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
    var areBarsVisible by rememberSaveable { mutableStateOf(true) }
    val toggleBarsVisibility = {
        areBarsVisible = !areBarsVisible
    }

    Scaffold(
        topBar = {
            if (areBarsVisible) {
                TopAppBar(
                    modifier = Modifier.testTag(FULLSCREEN_TOP_APP_BAR_TAG),
                    title = { Text(text = dateTimeLabel) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text(text = stringResource(id = R.string.back))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (areBarsVisible) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag(FULLSCREEN_BOTTOM_BAR_TAG)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (currentMedia.mediaType == MediaType.PHOTO) {
                            if (onEditPhoto != null) {
                                TextButton(onClick = { onEditPhoto(currentMedia) }) {
                                    Text(text = stringResource(id = R.string.edit))
                                }
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
                MediaType.PHOTO -> FullscreenPhotoPage(
                    photoUri = item.contentUri,
                    onTap = toggleBarsVisibility
                )
                MediaType.VIDEO -> FullscreenVideoPage(
                    videoUri = item.contentUri,
                    isCurrentPage = page == pagerState.currentPage,
                    onTap = toggleBarsVisibility
                )
            }
        }
    }
}

@Composable
private fun FullscreenPhotoPage(
    photoUri: Uri,
    onTap: () -> Unit
) {
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
                .diskCachePolicy(CachePolicy.DISABLED)
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
                        onTap = { onTap() },
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
    isCurrentPage: Boolean,
    onTap: () -> Unit
) {
    var videoView: VideoView? by remember(videoUri) { mutableStateOf(null) }

    DisposableEffect(videoUri) {
        onDispose {
            videoView?.setOnPreparedListener(null)
            videoView?.setOnClickListener(null)
            videoView?.stopPlayback()
            videoView = null
        }
    }

    key(videoUri) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    videoView = this
                    val controller = MediaController(context).also {
                        it.setAnchorView(this)
                    }
                    setMediaController(controller)
                    setVideoURI(videoUri)
                    setOnClickListener {
                        onTap()
                    }
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
