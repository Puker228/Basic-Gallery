package com.example.basicgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.example.basicgallery.data.model.MediaType
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val normalizedDate = if (dateTaken > 0L) dateTaken else dateAdded * 1000L

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
}
