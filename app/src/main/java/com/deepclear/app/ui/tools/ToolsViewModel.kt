package com.deepclear.app.ui.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepclear.app.data.model.ScannedFile
import com.deepclear.app.data.scanner.BrowserCacheCleaner
import com.deepclear.app.data.scanner.BrowserCacheInfo
import com.deepclear.app.data.scanner.DuplicateFinder
import com.deepclear.app.data.scanner.DuplicateGroup
import com.deepclear.app.data.scanner.EmptyFolder
import com.deepclear.app.data.scanner.EmptyFolderScanner
import com.deepclear.app.data.scanner.LargeFileScanner
import com.deepclear.app.data.scanner.SecureShredder
import com.deepclear.app.data.scanner.TrashScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ToolsUiState(
    // Trash
    val trashFiles: List<ScannedFile> = emptyList(),
    val isTrashScanning: Boolean = false,
    val trashScanned: Boolean = false,
    val trashTotalSize: Long = 0L,

    // Duplicates
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val isDuplicateScanning: Boolean = false,
    val duplicateScanned: Boolean = false,
    val duplicatePhase: String = "",
    val duplicateWastedSize: Long = 0L,

    // Large Files
    val largeFiles: List<ScannedFile> = emptyList(),
    val isLargeFileScanning: Boolean = false,
    val largeFileScanned: Boolean = false,
    val largeFileTotalSize: Long = 0L,

    // Empty Folders
    val emptyFolders: List<EmptyFolder> = emptyList(),
    val isEmptyFolderScanning: Boolean = false,
    val emptyFolderScanned: Boolean = false,
    val emptyFolderDeletedCount: Int = 0,

    // Browser Cache
    val browserCaches: List<BrowserCacheInfo> = emptyList(),
    val isBrowserScanning: Boolean = false,
    val browserScanned: Boolean = false,
    val isBrowserClearing: Boolean = false,
    val browserClearedBytes: Long = 0L,
    val browserClearComplete: Boolean = false,
    val browserTotalCacheSize: Long = 0L,

    // Shredder
    val isShredding: Boolean = false,
    val shredProgress: String = "",
    val shredComplete: Boolean = false,
    val bytesShredded: Long = 0L,

    // Active tab
    val activeTab: Int = 0
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val trashScanner: TrashScanner,
    private val duplicateFinder: DuplicateFinder,
    private val secureShredder: SecureShredder,
    private val largeFileScanner: LargeFileScanner,
    private val emptyFolderScanner: EmptyFolderScanner,
    private val browserCacheCleaner: BrowserCacheCleaner,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    // ── Trash Scanner ──────────────────────────────────
    fun scanTrash() {
        if (_uiState.value.isTrashScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrashScanning = true, trashScanned = false)
            val trashFiles = trashScanner.scan()
            _uiState.value = _uiState.value.copy(
                trashFiles = trashFiles,
                isTrashScanning = false,
                trashScanned = true,
                trashTotalSize = trashFiles.sumOf { it.sizeBytes }
            )
        }
    }

    fun toggleTrashFileSelection(index: Int) {
        val files = _uiState.value.trashFiles.toMutableList()
        if (index in files.indices) {
            files[index] = files[index].copy(isSelected = !files[index].isSelected)
            _uiState.value = _uiState.value.copy(trashFiles = files)
        }
    }

    fun selectAllTrash(selected: Boolean) {
        val files = _uiState.value.trashFiles.map { it.copy(isSelected = selected) }
        _uiState.value = _uiState.value.copy(trashFiles = files)
    }

    // ── Duplicate Finder ──────────────────────────────
    fun scanDuplicates() {
        if (_uiState.value.isDuplicateScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDuplicateScanning = true, duplicateScanned = false)
            duplicateFinder.findDuplicates().collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        duplicateGroups = progress.groups,
                        isDuplicateScanning = false,
                        duplicateScanned = true,
                        duplicatePhase = "",
                        duplicateWastedSize = progress.groups.sumOf { it.wastedBytes }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        duplicatePhase = progress.phase
                    )
                }
            }
        }
    }

    fun toggleDuplicateKeep(groupIndex: Int, fileIndex: Int) {
        val groups = _uiState.value.duplicateGroups.toMutableList()
        if (groupIndex in groups.indices) {
            groups[groupIndex] = groups[groupIndex].copy(keepIndex = fileIndex)
            _uiState.value = _uiState.value.copy(duplicateGroups = groups)
        }
    }

    // ── Large File Radar ──────────────────────────────
    fun scanLargeFiles() {
        if (_uiState.value.isLargeFileScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLargeFileScanning = true, largeFileScanned = false)
            largeFileScanner.scan().collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        largeFiles = progress.files,
                        isLargeFileScanning = false,
                        largeFileScanned = true,
                        largeFileTotalSize = progress.files.sumOf { it.sizeBytes }
                    )
                }
            }
        }
    }

    fun toggleLargeFileSelection(index: Int) {
        val files = _uiState.value.largeFiles.toMutableList()
        if (index in files.indices) {
            files[index] = files[index].copy(isSelected = !files[index].isSelected)
            _uiState.value = _uiState.value.copy(largeFiles = files)
        }
    }

    // ── Empty Folder Sweeper ──────────────────────────
    fun scanEmptyFolders() {
        if (_uiState.value.isEmptyFolderScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEmptyFolderScanning = true, emptyFolderScanned = false)
            emptyFolderScanner.scan().collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        emptyFolders = progress.folders,
                        isEmptyFolderScanning = false,
                        emptyFolderScanned = true
                    )
                }
            }
        }
    }

    fun toggleEmptyFolderSelection(index: Int) {
        val folders = _uiState.value.emptyFolders.toMutableList()
        if (index in folders.indices) {
            folders[index] = folders[index].copy(isSelected = !folders[index].isSelected)
            _uiState.value = _uiState.value.copy(emptyFolders = folders)
        }
    }

    fun deleteEmptyFolders() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                emptyFolderScanner.deleteEmptyFolders(_uiState.value.emptyFolders)
            }
            _uiState.value = _uiState.value.copy(emptyFolderDeletedCount = count)
            scanEmptyFolders()
        }
    }

    // ── Browser Cache Cleaner ──────────────────────────
    fun scanBrowserCaches() {
        if (_uiState.value.isBrowserScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBrowserScanning = true,
                browserScanned = false,
                browserClearComplete = false
            )
            browserCacheCleaner.scanBrowserCaches().collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        browserCaches = progress.browsers,
                        isBrowserScanning = false,
                        browserScanned = true,
                        browserTotalCacheSize = progress.browsers.sumOf { it.cacheSize }
                    )
                }
            }
        }
    }

    fun toggleBrowserSelection(index: Int) {
        val caches = _uiState.value.browserCaches.toMutableList()
        if (index in caches.indices) {
            caches[index] = caches[index].copy(isSelected = !caches[index].isSelected)
            _uiState.value = _uiState.value.copy(browserCaches = caches)
        }
    }

    fun clearSelectedBrowserCaches() {
        val selected = _uiState.value.browserCaches.filter { it.isSelected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBrowserClearing = true, browserClearComplete = false)
            browserCacheCleaner.clearBrowserCaches(_uiState.value.browserCaches).collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        isBrowserClearing = false,
                        browserClearComplete = true,
                        browserClearedBytes = progress.totalClearedBytes
                    )
                    scanBrowserCaches()
                }
            }
        }
    }

    // ── Secure Shredder ─────────────────────────────
    fun shredSelectedTrash() {
        val selectedPaths = _uiState.value.trashFiles
            .filter { it.isSelected }
            .map { it.path }
        if (selectedPaths.isEmpty()) return
        shredFiles(selectedPaths)
    }

    fun shredDuplicates() {
        val filesToDelete = mutableListOf<String>()
        _uiState.value.duplicateGroups.forEach { group ->
            group.files.forEachIndexed { index, file ->
                if (index != group.keepIndex) {
                    filesToDelete.add(file.path)
                }
            }
        }
        if (filesToDelete.isEmpty()) return
        shredFiles(filesToDelete)
    }

    fun shredSelectedLargeFiles() {
        val selectedPaths = _uiState.value.largeFiles
            .filter { it.isSelected }
            .map { it.path }
        if (selectedPaths.isEmpty()) return
        shredFiles(selectedPaths)
    }

    private fun shredFiles(paths: List<String>) {
        if (_uiState.value.isShredding) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isShredding = true, shredComplete = false)
            secureShredder.shredFiles(paths).collect { progress ->
                if (progress.isComplete) {
                    _uiState.value = _uiState.value.copy(
                        isShredding = false,
                        shredComplete = true,
                        bytesShredded = progress.totalBytesShredded,
                        shredProgress = ""
                    )
                    scanTrash()
                    scanDuplicates()
                    scanLargeFiles()
                } else {
                    _uiState.value = _uiState.value.copy(
                        shredProgress = "Shredding: ${progress.currentFile} (${progress.processedFiles}/${progress.totalFiles})"
                    )
                }
            }
        }
    }
}
