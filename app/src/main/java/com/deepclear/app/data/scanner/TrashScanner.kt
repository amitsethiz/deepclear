package com.deepclear.app.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScannedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans for files in trash/recycle bin locations:
 * 1. MediaStore trashed items (Android 11+)
 * 2. Hidden .trash / .Trash folders
 * 3. Recently deleted folders from popular apps
 */
@Singleton
class TrashScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Common hidden trash folder names
    private val trashFolderNames = setOf(
        ".trash", ".Trash", ".Trashes",
        ".recycle", ".Recycle",
        ".deleted", ".Deleted",
        "trash", "Trash",
        ".thumbnails"
    )

    suspend fun scan(): List<ScannedFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScannedFile>()

        // 1. Scan MediaStore trashed items (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            results.addAll(scanMediaStoreTrash())
        }

        // 2. Scan hidden .trash folders on external storage
        results.addAll(scanHiddenTrashFolders())

        results
    }

    /**
     * Query MediaStore for items marked as trashed (IS_TRASHED = 1).
     * Available on Android 11 (API 30) and above.
     */
    private fun scanMediaStoreTrash(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return results

        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        )

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )

        collections.forEach { uri ->
            try {
                val queryArgs = android.os.Bundle().apply {
                    putString(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.MediaColumns.IS_TRASHED} = 1"
                    )
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                }

                context.contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                    while (cursor.moveToNext()) {
                        val size = cursor.getLong(sizeCol)
                        if (size > 0) {
                            results.add(
                                ScannedFile(
                                    path = cursor.getString(dataCol) ?: "mediastore://${cursor.getLong(idCol)}",
                                    name = cursor.getString(nameCol) ?: "Unknown",
                                    sizeBytes = size,
                                    category = JunkCategory.TRASH,
                                    lastModified = cursor.getLong(dateCol) * 1000L,
                                    isSelected = true
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip on error (permission, unsupported, etc.)
            }
        }

        return results
    }

    /**
     * Scan for hidden .trash / .Trash / .Trashes folders in external storage.
     */
    private fun scanHiddenTrashFolders(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val externalStorage = Environment.getExternalStorageDirectory()

        if (!externalStorage.exists() || !externalStorage.canRead()) return results

        try {
            // Scan root-level trash folders
            externalStorage.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name in trashFolderNames) {
                    scanTrashDirectory(file, results)
                }
            }

            // Scan inside common directories (DCIM, Download, Pictures, etc.)
            val commonDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            )

            commonDirs.forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    dir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.name in trashFolderNames) {
                            scanTrashDirectory(subDir, results)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Skip if no permission
        }

        return results
    }

    private fun scanTrashDirectory(dir: File, results: MutableList<ScannedFile>) {
        try {
            dir.walkTopDown().maxDepth(5).forEach { file ->
                if (file.isFile && file.canRead() && file.length() > 0) {
                    results.add(
                        ScannedFile(
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            category = JunkCategory.TRASH,
                            lastModified = file.lastModified(),
                            isSelected = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Skip
        }
    }
}
