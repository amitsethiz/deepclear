package com.deepclear.app.ui.tools

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepclear.app.data.model.ScannedFile
import com.deepclear.app.data.scanner.BrowserCacheInfo
import com.deepclear.app.data.scanner.DuplicateGroup
import com.deepclear.app.data.scanner.EmptyFolder
import com.deepclear.app.ui.theme.Teal
import com.deepclear.app.util.FileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Trash", "Duplicates", "Large Files", "Empty Dirs", "Browsers")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.activeTab]),
                        color = Teal
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.activeTab == index,
                        onClick = { viewModel.setActiveTab(index) },
                        text = { Text(title, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            when (uiState.activeTab) {
                0 -> TrashTab(uiState, viewModel)
                1 -> DuplicatesTab(uiState, viewModel)
                2 -> LargeFilesTab(uiState, viewModel)
                3 -> EmptyFoldersTab(uiState, viewModel)
                4 -> BrowserCacheTab(uiState, viewModel)
            }
        }
    }
}

// ═══════════════ TRASH TAB ═══════════════

@Composable
private fun TrashTab(uiState: ToolsUiState, viewModel: ToolsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.trashScanned && !uiState.isTrashScanning) {
            item {
                ScanActionCard(
                    icon = Icons.Default.RestoreFromTrash,
                    title = "Find Trash Files",
                    description = "Locate deleted files still consuming space",
                    onClick = { viewModel.scanTrash() }
                )
            }
        }
        if (uiState.isTrashScanning) {
            item { ScanningIndicator("Scanning trash locations...") }
        }
        if (uiState.isShredding) {
            item { ScanningIndicator(uiState.shredProgress.ifEmpty { "Securely shredding..." }) }
        }
        if (uiState.shredComplete) {
            item { ResultBanner("${FileSize.format(uiState.bytesShredded)} securely shredded!") }
        }
        if (uiState.trashScanned && uiState.trashFiles.isEmpty()) {
            item { EmptyState("No trash files found", "Your device is clean!") }
        }
        if (uiState.trashFiles.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.trashFiles.size} files • ${FileSize.format(uiState.trashTotalSize)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        Text("Select All", style = MaterialTheme.typography.labelMedium, color = Teal,
                            modifier = Modifier.clickable { viewModel.selectAllTrash(true) })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("None", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { viewModel.selectAllTrash(false) })
                    }
                }
            }
            itemsIndexed(uiState.trashFiles) { index, file ->
                FileItem(file) { viewModel.toggleTrashFileSelection(index) }
            }
            val selectedCount = uiState.trashFiles.count { it.isSelected }
            if (selectedCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ShredButton("Secure Shred ($selectedCount files)", !uiState.isShredding) {
                        viewModel.shredSelectedTrash()
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════ DUPLICATES TAB ═══════════════

@Composable
private fun DuplicatesTab(uiState: ToolsUiState, viewModel: ToolsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.duplicateScanned && !uiState.isDuplicateScanning) {
            item {
                ScanActionCard(
                    icon = Icons.Default.ContentCopy,
                    title = "Find Duplicates",
                    description = "Identify identical files using SHA-256 hashing",
                    onClick = { viewModel.scanDuplicates() }
                )
            }
        }
        if (uiState.isDuplicateScanning) {
            item { ScanningIndicator(uiState.duplicatePhase.ifEmpty { "Analyzing files..." }) }
        }
        if (uiState.duplicateScanned && uiState.duplicateGroups.isEmpty()) {
            item { EmptyState("No duplicates found", "All your files are unique!") }
        }
        if (uiState.duplicateGroups.isNotEmpty()) {
            item {
                Text(
                    text = "${uiState.duplicateGroups.size} groups • ${FileSize.format(uiState.duplicateWastedSize)} wasted",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
            }
            itemsIndexed(uiState.duplicateGroups) { groupIndex, group ->
                DuplicateGroupCard(group) { fileIndex ->
                    viewModel.toggleDuplicateKeep(groupIndex, fileIndex)
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ShredButton("Shred Duplicates (keep marked)", !uiState.isShredding) {
                    viewModel.shredDuplicates()
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════ LARGE FILES TAB ═══════════════

@Composable
private fun LargeFilesTab(uiState: ToolsUiState, viewModel: ToolsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.largeFileScanned && !uiState.isLargeFileScanning) {
            item {
                ScanActionCard(
                    icon = Icons.Default.SdStorage,
                    title = "Find Large Files",
                    description = "Locate files larger than 50 MB",
                    onClick = { viewModel.scanLargeFiles() }
                )
            }
        }
        if (uiState.isLargeFileScanning) {
            item { ScanningIndicator("Scanning for large files...") }
        }
        if (uiState.largeFileScanned && uiState.largeFiles.isEmpty()) {
            item { EmptyState("No large files found", "No files over 50 MB detected!") }
        }
        if (uiState.largeFiles.isNotEmpty()) {
            item {
                Text(
                    text = "${uiState.largeFiles.size} files • ${FileSize.format(uiState.largeFileTotalSize)} total",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
            }
            itemsIndexed(uiState.largeFiles) { index, file ->
                FileItem(file) { viewModel.toggleLargeFileSelection(index) }
            }
            val selectedCount = uiState.largeFiles.count { it.isSelected }
            if (selectedCount > 0) {
                item {
                    val selectedSize = uiState.largeFiles.filter { it.isSelected }.sumOf { it.sizeBytes }
                    Spacer(modifier = Modifier.height(8.dp))
                    ShredButton("Shred $selectedCount files (${FileSize.format(selectedSize)})", !uiState.isShredding) {
                        viewModel.shredSelectedLargeFiles()
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════ EMPTY FOLDERS TAB ═══════════════

@Composable
private fun EmptyFoldersTab(uiState: ToolsUiState, viewModel: ToolsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.emptyFolderScanned && !uiState.isEmptyFolderScanning) {
            item {
                ScanActionCard(
                    icon = Icons.Default.FolderOpen,
                    title = "Find Empty Folders",
                    description = "Detect empty directories wasting space",
                    onClick = { viewModel.scanEmptyFolders() }
                )
            }
        }
        if (uiState.isEmptyFolderScanning) {
            item { ScanningIndicator("Scanning directories...") }
        }
        if (uiState.emptyFolderScanned && uiState.emptyFolders.isEmpty()) {
            item { EmptyState("No empty folders found", "All directories contain files!") }
        }
        if (uiState.emptyFolderDeletedCount > 0) {
            item { ResultBanner("${uiState.emptyFolderDeletedCount} empty folders removed!") }
        }
        if (uiState.emptyFolders.isNotEmpty()) {
            item {
                Text(
                    text = "${uiState.emptyFolders.size} empty folders found",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
            }
            itemsIndexed(uiState.emptyFolders) { index, folder ->
                EmptyFolderItem(folder) { viewModel.toggleEmptyFolderSelection(index) }
            }
            val selectedCount = uiState.emptyFolders.count { it.isSelected }
            if (selectedCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.deleteEmptyFolders() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove $selectedCount folders", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════ BROWSER CACHE TAB ═══════════════

@Composable
private fun BrowserCacheTab(uiState: ToolsUiState, viewModel: ToolsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!uiState.browserScanned && !uiState.isBrowserScanning) {
            item {
                ScanActionCard(
                    icon = Icons.Default.Language,
                    title = "Scan Browser Cache",
                    description = "Detect cache from Chrome, Firefox, Edge, Brave & more",
                    onClick = { viewModel.scanBrowserCaches() }
                )
            }
        }
        if (uiState.isBrowserScanning) {
            item { ScanningIndicator("Detecting installed browsers...") }
        }
        if (uiState.isBrowserClearing) {
            item { ScanningIndicator("Clearing browser cache...") }
        }
        if (uiState.browserClearComplete) {
            item { ResultBanner("${FileSize.format(uiState.browserClearedBytes)} browser cache cleared!") }
        }
        if (uiState.browserScanned && uiState.browserCaches.isEmpty()) {
            item { EmptyState("No browser cache found", "Browsers are squeaky clean!") }
        }
        if (uiState.browserCaches.isNotEmpty()) {
            item {
                Text(
                    text = "${uiState.browserCaches.size} browsers • ${FileSize.format(uiState.browserTotalCacheSize)} cache",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
            }
            itemsIndexed(uiState.browserCaches) { index, browser ->
                BrowserCacheItem(browser) { viewModel.toggleBrowserSelection(index) }
            }
            val selectedCount = uiState.browserCaches.count { it.isSelected }
            if (selectedCount > 0) {
                item {
                    val selectedSize = uiState.browserCaches.filter { it.isSelected }.sumOf { it.cacheSize }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.clearSelectedBrowserCaches() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        enabled = !uiState.isBrowserClearing
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear $selectedCount browsers (${FileSize.format(selectedSize)})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════ SHARED COMPONENTS ═══════════════

@Composable
private fun ScanActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Teal, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScanningIndicator(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Teal, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = Teal, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShredButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        enabled = enabled
    ) {
        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FileItem(file: ScannedFile, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = file.isSelected, onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Teal))
            Spacer(modifier = Modifier.width(8.dp))
            Text(file.name, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(FileSize.format(file.sizeBytes), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyFolderItem(folder: EmptyFolder, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = folder.isSelected, onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Teal))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(folder.path, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BrowserCacheItem(browser: BrowserCacheInfo, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = browser.isSelected, onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Teal))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(browser.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(browser.packageName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(FileSize.format(browser.cacheSize), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, onToggleKeep: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(200)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${group.files.size} copies • ${FileSize.format(group.fileSize)} each",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Text("Wastes ${FileSize.format(group.wastedBytes)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            group.files.forEachIndexed { fileIndex, file ->
                val isKept = fileIndex == group.keepIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isKept) Teal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background)
                        .clickable { onToggleKeep(fileIndex) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isKept) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        if (isKept) "Keep" else "Delete",
                        tint = if (isKept) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file.path.substringBeforeLast("/"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        if (isKept) "KEEP" else "DELETE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isKept) Teal else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
