package com.example.basicgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.IntentSender
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoAdjustments
import com.example.basicgallery.data.model.PhotoCrop
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MediaStoreGalleryRepository(
    private val contentResolver: ContentResolver
) : GalleryRepository {

    override fun createTrashRequest(photoUris: Collection<Uri>, moveToTrash: Boolean): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (photoUris.isEmpty()) return null

        return MediaStore.createTrashRequest(contentResolver, photoUris, moveToTrash).intentSender
    }

    override fun createDeleteRequest(photoUris: Collection<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (photoUris.isEmpty()) return null

        return MediaStore.createDeleteRequest(contentResolver, photoUris).intentSender
    }

    override suspend fun loadPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        queryMedia(onlyTrashed = false)
    }

    override suspend fun loadTrashPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        queryMedia(onlyTrashed = true)
    }

    override suspend fun loadVideoCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            queryVideoCount(onlyTrashed = false)
        }.getOrDefault(0)
    }

    override suspend fun loadTrashVideoCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            queryVideoCount(onlyTrashed = true)
        }.getOrDefault(0)
    }

    override suspend fun saveEditedPhoto(
        sourcePhoto: PhotoItem,
        adjustments: PhotoAdjustments,
        crop: PhotoCrop
    ): Uri = withContext(Dispatchers.IO) {
        val normalizedCrop = crop.normalized()
        val sourceBitmap = decodeBitmap(sourcePhoto.contentUri, normalizedCrop)
        var processedBitmap: Bitmap? = null
        try {
            processedBitmap = PhotoEditingProcessor.applyAdjustments(sourceBitmap, adjustments)
            val sourceMetadata = querySourceImageMetadata(sourcePhoto.contentUri)
            saveEditedBitmapToMediaStore(
                bitmap = processedBitmap,
                sourceMetadata = sourceMetadata,
                dateTakenMillis = sourcePhoto.dateTakenMillis
            )
        } finally {
            processedBitmap?.takeIf { it !== sourceBitmap }?.recycle()
            sourceBitmap.recycle()
        }
    }

    private fun queryMedia(onlyTrashed: Boolean): List<PhotoItem> {
        return (queryImages(onlyTrashed = onlyTrashed) + queryVideos(onlyTrashed = onlyTrashed))
            .sortedByDescending { it.dateTakenMillis }
    }

    private fun queryImages(onlyTrashed: Boolean): List<PhotoItem> {
        if (onlyTrashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return emptyList()
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentResolver.query(
                collection,
                projection,
                buildQueryArgs(
                    onlyTrashed = onlyTrashed,
                    pendingColumn = MediaStore.Images.Media.IS_PENDING,
                    sortOrder = sortOrder
                ),
                null
            )
        } else {
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.IS_PENDING} = 0"
            } else {
                null
            }
            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )
        }

        val photos = ArrayList<PhotoItem>()

        cursor?.use { cursor ->
            if (cursor.count > 0) {
                photos.ensureCapacity(cursor.count)
            }

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val displayName = cursor.getString(displayNameColumn)
                val sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val normalizedDate = EditedPhotoNameCodec.resolveDateTakenMillis(
                    dateTakenMillis = dateTaken,
                    dateAddedSeconds = dateAdded,
                    displayName = displayName
                )

                val contentUri = ContentUris.withAppendedId(
                    collection,
                    id
                )

                photos.add(
                    PhotoItem(
                        id = id,
                        contentUri = contentUri,
                        dateTakenMillis = normalizedDate,
                        sizeBytes = sizeBytes,
                        mediaType = MediaType.PHOTO
                    )
                )
            }
        }

        return photos
    }

    private fun queryVideos(onlyTrashed: Boolean): List<PhotoItem> {
        if (onlyTrashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return emptyList()
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.MediaColumns.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentResolver.query(
                collection,
                projection,
                buildQueryArgs(
                    onlyTrashed = onlyTrashed,
                    pendingColumn = MediaStore.Video.Media.IS_PENDING,
                    sortOrder = sortOrder
                ),
                null
            )
        } else {
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.IS_PENDING} = 0"
            } else {
                null
            }
            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )
        }

        val videos = ArrayList<PhotoItem>()

        cursor?.use { cursor ->
            if (cursor.count > 0) {
                videos.ensureCapacity(cursor.count)
            }

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val durationMillis = cursor.getLong(durationColumn).coerceAtLeast(0L)
                val sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val normalizedDate = if (dateTaken > 0L) dateTaken else dateAdded * 1000L
                val stableId = -(rawId + 1L)

                val contentUri = ContentUris.withAppendedId(
                    collection,
                    rawId
                )

                videos.add(
                    PhotoItem(
                        id = stableId,
                        contentUri = contentUri,
                        dateTakenMillis = normalizedDate,
                        sizeBytes = sizeBytes,
                        durationMillis = durationMillis,
                        mediaType = MediaType.VIDEO
                    )
                )
            }
        }

        return videos
    }

    private fun queryVideoCount(onlyTrashed: Boolean): Int {
        if (onlyTrashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                buildQueryArgs(
                    onlyTrashed = onlyTrashed,
                    pendingColumn = MediaStore.Video.Media.IS_PENDING
                ),
                null
            )
        } else {
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.IS_PENDING} = 0"
            } else {
                null
            }
            contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                null,
                null
            )
        }

        return cursor?.use { it.count } ?: 0
    }

    private fun saveEditedBitmapToMediaStore(
        bitmap: Bitmap,
        sourceMetadata: SourceImageMetadata,
        dateTakenMillis: Long
    ): Uri {
        val outputMimeType = normalizeOutputMimeType(sourceMetadata.mimeType)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val insertValues = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                EditedPhotoNameCodec.buildDisplayName(
                    originalName = sourceMetadata.displayName,
                    mimeType = outputMimeType,
                    sourceDateTakenMillis = dateTakenMillis
                )
            )
            put(MediaStore.MediaColumns.MIME_TYPE, outputMimeType)
            putCaptureTimestampColumns(dateTakenMillis)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    sourceMetadata.relativePath?.takeIf { it.isNotBlank() } ?: DEFAULT_RELATIVE_PATH
                )
            }
        }

        val insertedUri = contentResolver.insert(collection, insertValues)
            ?: throw IllegalStateException("Unable to create a media entry for edited photo.")
        var committed = false
        try {
            contentResolver.openOutputStream(insertedUri)?.use { outputStream ->
                val compressed = bitmap.compress(compressFormatForMimeType(outputMimeType), JPEG_QUALITY, outputStream)
                if (!compressed) {
                    throw IllegalStateException("Unable to encode edited photo.")
                }
            } ?: throw IllegalStateException("Unable to open output stream for edited photo.")
            writeCaptureExifTimestamp(
                targetUri = insertedUri,
                outputMimeType = outputMimeType,
                dateTakenMillis = dateTakenMillis
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                    putCaptureTimestampColumns(dateTakenMillis)
                }
                contentResolver.update(insertedUri, publishValues, null, null)
            }
            committed = true
            return insertedUri
        } finally {
            if (!committed) {
                contentResolver.delete(insertedUri, null, null)
            }
        }
    }

    private fun decodeBitmap(
        uri: Uri,
        crop: PhotoCrop = PhotoCrop()
    ): Bitmap {
        val normalizedCrop = crop.normalized()
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val targetSampleSize = calculateTargetSampleSize(
                width = width,
                height = height,
                maxPixelCount = MAX_EDIT_DECODE_PIXEL_COUNT
            )
            if (targetSampleSize > 1) {
                decoder.setTargetSampleSize(targetSampleSize)
            }
            if (!normalizedCrop.isFullImage()) {
                val left = (normalizedCrop.left * width).roundToInt().coerceIn(0, width - 1)
                val top = (normalizedCrop.top * height).roundToInt().coerceIn(0, height - 1)
                val right = (normalizedCrop.right * width).roundToInt().coerceIn(left + 1, width)
                val bottom = (normalizedCrop.bottom * height).roundToInt().coerceIn(top + 1, height)
                decoder.setCrop(Rect(left, top, right, bottom))
            }
        }
    }

    private fun calculateTargetSampleSize(
        width: Int,
        height: Int,
        maxPixelCount: Long
    ): Int {
        if (maxPixelCount <= 0L) return 1
        var sampleSize = 1
        while ((width.toLong() / sampleSize) * (height.toLong() / sampleSize) > maxPixelCount) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun querySourceImageMetadata(uri: Uri): SourceImageMetadata {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use SourceImageMetadata()
            }
            SourceImageMetadata(
                displayName = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME),
                mimeType = cursor.getStringOrNull(MediaStore.MediaColumns.MIME_TYPE),
                relativePath = cursor.getStringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
            )
        } ?: SourceImageMetadata()
    }

    private fun normalizeOutputMimeType(mimeType: String?): String {
        return when (mimeType?.lowercase(Locale.US)) {
            MIME_TYPE_PNG -> MIME_TYPE_PNG
            MIME_TYPE_WEBP -> MIME_TYPE_WEBP
            else -> MIME_TYPE_JPEG
        }
    }

    @Suppress("DEPRECATION")
    private fun compressFormatForMimeType(mimeType: String): Bitmap.CompressFormat {
        return when (mimeType) {
            MIME_TYPE_PNG -> Bitmap.CompressFormat.PNG
            MIME_TYPE_WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun ContentValues.putCaptureTimestampColumns(dateTakenMillis: Long) {
        if (dateTakenMillis <= 0L) return
        val dateSeconds = (dateTakenMillis / 1_000L).coerceAtLeast(0L)
        put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMillis)
        put(MediaStore.MediaColumns.DATE_ADDED, dateSeconds)
        put(MediaStore.MediaColumns.DATE_MODIFIED, dateSeconds)
    }

    private fun writeCaptureExifTimestamp(
        targetUri: Uri,
        outputMimeType: String,
        dateTakenMillis: Long
    ) {
        if (dateTakenMillis <= 0L) return
        if (outputMimeType != MIME_TYPE_JPEG && outputMimeType != MIME_TYPE_WEBP) return

        runCatching {
            contentResolver.openFileDescriptor(targetUri, "rw")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                val exifDateTime = EXIF_DATE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(dateTakenMillis).atZone(ZoneId.systemDefault())
                )
                exif.setAttribute(ExifInterface.TAG_DATETIME, exifDateTime)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateTime)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifDateTime)
                exif.saveAttributes()
            }
        }
    }

    private fun buildQueryArgs(
        onlyTrashed: Boolean,
        pendingColumn: String,
        sortOrder: String? = null
    ): Bundle {
        return Bundle().apply {
            val selection = "$pendingColumn = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = ?"
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(if (onlyTrashed) "1" else "0")
            )
            sortOrder?.let { putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it) }
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
    }

    private data class SourceImageMetadata(
        val displayName: String? = null,
        val mimeType: String? = null,
        val relativePath: String? = null
    )

    private companion object {
        const val JPEG_QUALITY = 95
        const val MAX_EDIT_DECODE_PIXEL_COUNT = 12_000_000L
        const val MIME_TYPE_JPEG = "image/jpeg"
        const val MIME_TYPE_PNG = "image/png"
        const val MIME_TYPE_WEBP = "image/webp"
        val EXIF_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
        val DEFAULT_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/Basic Gallery"
    }
}
