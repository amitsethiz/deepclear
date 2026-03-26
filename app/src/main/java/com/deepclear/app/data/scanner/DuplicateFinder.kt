package com.deepclear.app.data.scanner

import android.os.Environment
import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A group of duplicate files sharing the same content hash.
 */
data class DuplicateGroup(
    val hash: String,
    val files: List<ScannedFile>,
    val fileSize: Long,
    val keepIndex: Int = 0 // Index of the file to keep (default: first one)
) {
    val wastedBytes: Long
        get() = fileSize * (files.size - 1).coerceAtLeast(0)
}

/**
 * Progress update during duplicate scanning.
 */
data class DuplicateScanProgress(
    val phase: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val duplicateGroupsFound: Int = 0,
    val isComplete: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList()
)

/**
 * Finds duplicate files using a 2-phase approach:
 * 1. Group files by size (fast pre-filter)
 * 2. Compare SHA-256 hashes within same-size groups (accurate)
 *
 * Only files > 1KB are considered to avoid noise from tiny files.
 */
@Singleton
class DuplicateFinder @Inject constructor() {

    private val minFileSize = 1024L // Ignore files < 1KB
    private val hashBufferSize = 8192

    /**
     * Scan for duplicate files across common storage directories.
     */
    fun findDuplicates(): Flow<DuplicateScanProgress> = flow {
        emit(DuplicateScanProgress(phase = "Collecting files..."))

        // Collect all files
        val allFiles = collectFiles()
        val totalFiles = allFiles.size

        emit(
            DuplicateScanProgress(
                phase = "Grouping by size...",
                totalFiles = totalFiles
            )
        )

        // Phase 1: Group by file size
        val sizeGroups = allFiles.groupBy { it.length() }
            .filter { it.value.size >= 2 } // Only groups with potential duplicates

        val potentialDuplicateFiles = sizeGroups.values.flatten()

        emit(
            DuplicateScanProgress(
                phase = "Computing hashes...",
                totalFiles = potentialDuplicateFiles.size
            )
        )

        // Phase 2: Hash comparison within size groups
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var filesProcessed = 0

        sizeGroups.forEach { (size, files) ->
            val hashGroups = mutableMapOf<String, MutableList<File>>()

            files.forEach { file ->
                try {
                    val hash = computeSha256(file)
                    hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                } catch (_: Exception) {
                    // Skip unreadable files
                }

                filesProcessed++
                if (filesProcessed % 50 == 0) {
                    emit(
                        DuplicateScanProgress(
                            phase = "Hashing files...",
                            filesProcessed = filesProcessed,
                            totalFiles = potentialDuplicateFiles.size,
                            duplicateGroupsFound = duplicateGroups.size
                        )
                    )
                }
            }

            // Keep only actual duplicates (2+ files with same hash)
            hashGroups.filter { it.value.size >= 2 }.forEach { (hash, dupeFiles) ->
                duplicateGroups.add(
                    DuplicateGroup(
                        hash = hash.take(16), // Truncate for display
                        files = dupeFiles.map { file ->
                            ScannedFile(
                                path = file.absolutePath,
                                name = file.name,
                                sizeBytes = file.length(),
                                category = JunkCategory.DUPLICATES,
                                lastModified = file.lastModified(),
                                isSelected = false
                            )
                        },
                        fileSize = size
                    )
                )
            }
        }

        emit(
            DuplicateScanProgress(
                phase = "Complete",
                filesProcessed = potentialDuplicateFiles.size,
                totalFiles = potentialDuplicateFiles.size,
                duplicateGroupsFound = duplicateGroups.size,
                isComplete = true,
                groups = duplicateGroups.sortedByDescending { it.wastedBytes }
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Collect all files from common storage directories.
     */
    private fun collectFiles(): List<File> {
        val files = mutableListOf<File>()
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        )

        searchDirs.forEach { dir ->
            if (dir.exists() && dir.canRead()) {
                try {
                    dir.walkTopDown().maxDepth(5).forEach { file ->
                        if (file.isFile && file.canRead() && file.length() >= minFileSize) {
                            files.add(file)
                        }
                    }
                } catch (_: Exception) {
                    // Skip
                }
            }
        }

        return files
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(hashBufferSize)

        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
