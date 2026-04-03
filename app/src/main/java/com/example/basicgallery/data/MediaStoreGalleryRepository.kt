package com.example.basicgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.basicgallery.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreGalleryRepository(
    private val contentResolver: ContentResolver
) : GalleryRepository {

    override fun createTrashRequest(photoUris: Collection<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (photoUris.isEmpty()) return null

        return MediaStore.createTrashRequest(contentResolver, photoUris, true).intentSender
    }

    override suspend fun loadPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.IS_PENDING} = 0"
        } else {
            null
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val photos = ArrayList<PhotoItem>()

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.count > 0) {
                photos.ensureCapacity(cursor.count)
            }

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val normalizedDate = if (dateTaken > 0L) dateTaken else dateAdded * 1000L

                val contentUri = ContentUris.withAppendedId(
                    collection,
                    id
                )

                photos.add(
                    PhotoItem(
                        id = id,
                        contentUri = contentUri,
                        dateTakenMillis = normalizedDate
                    )
                )
            }
        }

        photos
    }

    override suspend fun loadVideoCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.IS_PENDING} = 0"
            } else {
                null
            }

            queryItemCount(collection = collection, selection = selection)
        }.getOrDefault(0)
    }

    private fun queryItemCount(collection: Uri, selection: String?): Int {
        return contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            null,
            null
        )?.count ?: 0
    }
}
