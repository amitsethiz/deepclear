package com.deepclear.app.data.repository

import android.os.Environment
import android.os.StatFs
import com.deepclear.app.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that provides device storage information using StatFs.
 */
@Singleton
class StorageRepository @Inject constructor() {

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.availableBytes
        val usedBytes = totalBytes - freeBytes

        StorageInfo(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes
        )
    }
}
