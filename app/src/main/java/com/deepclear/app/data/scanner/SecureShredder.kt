package com.deepclear.app.data.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress update for secure shredding operations.
 */
data class ShredProgress(
    val currentFile: String = "",
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val isComplete: Boolean = false,
    val totalBytesShredded: Long = 0L
)

/**
 * Securely deletes files by overwriting their data with random bytes
 * before unlinking, making recovery by data recovery tools impossible.
 *
 * Uses a 3-pass overwrite strategy:
 *   1. Random bytes
 *   2. Complementary pattern (0xFF XOR)
 *   3. Random bytes again
 * Then truncates and deletes the file.
 */
@Singleton
class SecureShredder @Inject constructor() {

    private val secureRandom = SecureRandom()
    private val bufferSize = 8192 // 8KB chunks for overwrite

    /**
     * Securely shred a list of file paths. Emits progress as a Flow.
     */
    fun shredFiles(filePaths: List<String>): Flow<ShredProgress> = flow {
        val totalFiles = filePaths.size
        var processedFiles = 0
        var totalBytesShredded = 0L

        filePaths.forEach { path ->
            val file = File(path)
            emit(
                ShredProgress(
                    currentFile = file.name,
                    processedFiles = processedFiles,
                    totalFiles = totalFiles
                )
            )

            if (file.exists() && file.isFile && file.canWrite()) {
                try {
                    val fileSize = file.length()
                    shredFile(file)
                    totalBytesShredded += fileSize
                } catch (_: Exception) {
                    // Skip files that can't be shredded
                }
            }

            processedFiles++
        }

        emit(
            ShredProgress(
                currentFile = "",
                processedFiles = totalFiles,
                totalFiles = totalFiles,
                isComplete = true,
                totalBytesShredded = totalBytesShredded
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Performs 3-pass secure overwrite on a single file then deletes it.
     */
    private fun shredFile(file: File) {
        val fileSize = file.length()
        if (fileSize <= 0) {
            file.delete()
            return
        }

        val buffer = ByteArray(bufferSize)

        try {
            RandomAccessFile(file, "rw").use { raf ->
                // Pass 1: Random bytes
                raf.seek(0)
                overwriteWithRandom(raf, fileSize, buffer)

                // Pass 2: Complementary pattern (0xFF)
                raf.seek(0)
                overwriteWithPattern(raf, fileSize, 0xFF.toByte())

                // Pass 3: Random bytes again
                raf.seek(0)
                overwriteWithRandom(raf, fileSize, buffer)

                // Flush to disk
                raf.fd.sync()
            }

            // Truncate file to 0 bytes
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                raf.fd.sync()
            }
        } catch (_: Exception) {
            // Best effort - still try to delete
        }

        // Finally delete the file
        file.delete()
    }

    private fun overwriteWithRandom(raf: RandomAccessFile, fileSize: Long, buffer: ByteArray) {
        var remaining = fileSize
        while (remaining > 0) {
            val toWrite = minOf(remaining.toInt(), buffer.size)
            secureRandom.nextBytes(buffer)
            raf.write(buffer, 0, toWrite)
            remaining -= toWrite
        }
    }

    private fun overwriteWithPattern(raf: RandomAccessFile, fileSize: Long, pattern: Byte) {
        val buffer = ByteArray(bufferSize) { pattern }
        var remaining = fileSize
        while (remaining > 0) {
            val toWrite = minOf(remaining.toInt(), buffer.size)
            raf.write(buffer, 0, toWrite)
            remaining -= toWrite
        }
    }
}
