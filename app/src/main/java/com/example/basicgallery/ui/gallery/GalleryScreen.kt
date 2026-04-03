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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.basicgallery.R
import com.example.basicgallery.data.model.PhotoItem
import java.util.Locale

private data class PendingTrashRequest(
    val closeViewerAfterSuccess: Boolean
)

@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { requiredReadPermissions() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(context.hasAnyPermission(permissions)) }
    var permissionRequestStarted by rememberSaveable { mutableStateOf(false) }
    var selectedPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTrashRequest by remember { mutableStateOf<PendingTrashRequest?>(null) }
    val gridState = rememberLazyGridState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionRequestStarted = true
        hasPermission = result.values.any { it } || context.hasAnyPermission(permissions)
    }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val request = pendingTrashRequest
        pendingTrashRequest = null

        if (result.resultCode == Activity.RESULT_OK && request != null) {
            if (request.closeViewerAfterSuccess) {
                selectedPhotoUri = null
            }
            viewModel.clearSelection()
            viewModel.loadPhotos(forceRefresh = true)
        }
    }

    fun launchTrashRequest(photoUris: List<Uri>, closeViewerAfterSuccess: Boolean) {
        if (photoUris.isEmpty()) return

        val intentSender = viewModel.createTrashRequest(photoUris)
        if (intentSender == null) {
            Toast.makeText(context, R.string.trash_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        pendingTrashRequest = PendingTrashRequest(
            closeViewerAfterSuccess = closeViewerAfterSuccess
        )
        trashLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = context.hasAnyPermission(permissions)
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

    BackHandler(enabled = selectedPhotoUri == null && uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val openedPhotoUri = selectedPhotoUri
        when {
            openedPhotoUri != null -> {
                FullscreenPhotoScreen(
                    photoUri = Uri.parse(openedPhotoUri),
                    onBack = { selectedPhotoUri = null },
                    onDelete = { uri ->
                        launchTrashRequest(
                            photoUris = listOf(uri),
                            closeViewerAfterSuccess = true
                        )
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
                    gridState = gridState,
                    onPhotoClick = { photo ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(photo.id)
                        } else {
                            selectedPhotoUri = photo.contentUri.toString()
                        }
                    },
                    onPhotoLongClick = { photo ->
                        viewModel.startSelection(photo.id)
                    },
                    onDeleteSelected = {
                        val selectedUris = uiState.photos
                            .asSequence()
                            .filter { it.id in uiState.selectedPhotoIds }
                            .map { it.contentUri }
                            .toList()

                        launchTrashRequest(
                            photoUris = selectedUris,
                            closeViewerAfterSuccess = false
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
private fun GalleryScreen(
    uiState: GalleryUiState,
    gridState: LazyGridState,
    onPhotoClick: (PhotoItem) -> Unit,
    onPhotoLongClick: (PhotoItem) -> Unit,
    onDeleteSelected: () -> Unit,
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
    val todayLabel = stringResource(id = R.string.gallery_date_today)
    val yesterdayLabel = stringResource(id = R.string.gallery_date_yesterday)
    val photoSections = remember(uiState.photos, locale, todayLabel, yesterdayLabel) {
        groupPhotosByDay(
            photos = uiState.photos,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            locale = locale
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text(
                            text = stringResource(
                                id = R.string.gallery_selected_count,
                                uiState.selectedCount
                            )
                        )
                    } else {
                        Text(text = stringResource(id = R.string.gallery_title))
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        TextButton(
                            onClick = onDeleteSelected,
                            enabled = uiState.selectedCount > 0
                        ) {
                            Text(text = stringResource(id = R.string.delete))
                        }
                        TextButton(onClick = onClearSelection) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.photos.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.errorMessage != null && uiState.photos.isEmpty() -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.photos.isEmpty() -> {
                    Text(
                        text = stringResource(id = R.string.gallery_empty_state),
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
                                DaySectionHeader(label = section.label)
                            }

                            items(
                                items = section.photos,
                                key = { it.id },
                                contentType = { "photo" }
                            ) { photo ->
                                PhotoGridItem(
                                    photo = photo,
                                    isSelected = photo.id in uiState.selectedPhotoIds,
                                    onClick = { onPhotoClick(photo) },
                                    onLongClick = { onPhotoLongClick(photo) }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.photos.isNotEmpty()) {
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
private fun DaySectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp)
    )
}

@Composable
private fun PhotoGridItem(
    photo: PhotoItem,
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
                .build()
        }

        AsyncImage(
            model = request,
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
    }
}

@Composable
private fun FullscreenPhotoScreen(
    photoUri: Uri,
    onBack: () -> Unit,
    onDelete: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.photo_screen_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(id = R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { onDelete(photoUri) }) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.scrim)
        ) {
            val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
            val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
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

            AsyncImage(
                model = request,
                contentDescription = stringResource(id = R.string.photo_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
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
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.hasAnyPermission(permissions: Array<String>): Boolean {
    return permissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Context.shouldShowAnyPermissionRationale(permissions: Array<String>): Boolean {
    val activity = findActivity() ?: return false
    return permissions.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
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
