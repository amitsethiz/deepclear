package com.deepclear.app.data.scanner

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EmptyFolder(
    val path: String,
    val name: String,
    val isSelected: Boolean = true
)

data class EmptyFolderScanProgress(
    val dirsScanned: Int = 0,
    val emptyFoldersFound: Int = 0,
    val isComplete: Boolean = false,
    val folders: List<EmptyFolder> = emptyList()
)

/**
 * Recursively detects empty directories across user-accessible storage.
 * A directory is "empty" if it contains no files (nested empty dirs count as empty).
 *
 * Safety: skips system directories, Android/data, Android/obb.
 */
@Singleton
class EmptyFolderScanner @Inject constructor() {

    private val protectedDirNames = setOf(
        "Android", "android", "data", "obb",
        "DCIM", "Camera", "Download", "Downloads"
    )

    fun scan(): Flow<EmptyFolderScanProgress> = flow {
        val emptyFolders = mutableListOf<EmptyFolder>()
        var dirsScanned = 0

        emit(EmptyFolderScanProgress())

        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage.exists() && externalStorage.canRead()) {
            scanDirectory(externalStorage, emptyFolders, 0, 6) { scanned ->
                dirsScanned = scanned
            }
        }

        emit(
            EmptyFolderScanProgress(
                dirsScanned = dirsScanned,
                emptyFoldersFound = emptyFolders.size,
                isComplete = true,
                folders = emptyFolders.sortedBy { it.path }
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Recursively scan for empty directories.
     * Returns true if the directory is empty (contains only empty subdirectories).
     */
    private fun scanDirectory(
        dir: File,
        results: MutableList<EmptyFolder>,
        depth: Int,
        maxDepth: Int,
        onScanned: (Int) -> Unit
    ): Boolean {
        if (depth > maxDepth) return false
        if (!dir.isDirectory || !dir.canRead()) return false

        // Skip protected directories at root level
        if (depth <= 1 && dir.name in protectedDirNames) return false

        try {
            val children = dir.listFiles() ?: return true // null = empty or no access

            if (children.isEmpty()) {
                // Truly empty directory
                results.add(
                    EmptyFolder(
                        path = dir.absolutePath,
                        name = dir.name
                    )
                )
                onScanned(results.size)
                return true
            }

            // Check if all children are directories and all are empty
            val hasFiles = children.any { it.isFile }
            if (hasFiles) return false

            // All children are directories — check if they're all empty
            var allChildrenEmpty = true
            children.forEach { child ->
                if (child.isDirectory) {
                    val childEmpty = scanDirectory(child, results, depth + 1, maxDepth, onScanned)
                    if (!childEmpty) allChildrenEmpty = false
                }
            }

            if (allChildrenEmpty) {
                results.add(
                    EmptyFolder(
                        path = dir.absolutePath,
                        name = dir.name
                    )
                )
                onScanned(results.size)
            }

            return allChildrenEmpty
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Delete selected empty folders safely.
     * Returns count of successfully deleted folders.
     */
    fun deleteEmptyFolders(folders: List<EmptyFolder>): Int {
        var deleted = 0
        // Sort by path length descending so we delete deepest first
        folders.filter { it.isSelected }
            .sortedByDescending { it.path.length }
            .forEach { folder ->
                try {
                    val dir = File(folder.path)
                    if (dir.exists() && dir.isDirectory) {
                        // Double-check it's still empty
                        val children = dir.listFiles()
                        if (children == null || children.isEmpty()) {
                            if (dir.delete()) deleted++
                        }
                    }
                } catch (_: Exception) {
                    // Skip
                }
            }
        return deleted
    }
}
