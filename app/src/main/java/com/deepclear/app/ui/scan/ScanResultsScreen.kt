package com.deepclear.app.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScanCategory
import com.deepclear.app.data.model.ScannedFile
import com.deepclear.app.ui.theme.Teal
import com.deepclear.app.util.FileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-start scan when arriving
    LaunchedEffect(Unit) {
        viewModel.checkPermission()
        if (!uiState.scanComplete && !uiState.isScanning) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Results",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (uiState.scanComplete && uiState.scanResult.selectedJunkSize > 0) {
                DeleteBottomBar(
                    selectedSize = uiState.scanResult.selectedJunkSize,
                    isDeleting = uiState.isDeleting,
                    onClick = { viewModel.showDeleteConfirmation() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isScanning -> {
                    ScanningView(
                        progress = uiState.scanProgress,
                        filesFound = uiState.filesFound,
                        totalSize = uiState.totalSizeFound
                    )
                }
                uiState.scanComplete && uiState.scanResult.categories.isEmpty() -> {
                    EmptyResultView()
                }
                uiState.scanComplete -> {
                    ScanResultsList(
                        categories = uiState.scanResult.categories,
                        onToggleExpanded = { viewModel.toggleCategoryExpanded(it) },
                        onToggleCategorySelection = { viewModel.toggleCategorySelection(it) },
                        onToggleFileSelection = { catIdx, fileIdx ->
                            viewModel.toggleFileSelection(catIdx, fileIdx)
                        }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            selectedSize = uiState.scanResult.selectedJunkSize,
            selectedCount = uiState.scanResult.categories.sumOf { it.selectedCount },
            onConfirm = { viewModel.deleteSelectedFiles() },
            onDismiss = { viewModel.dismissDeleteConfirmation() }
        )
    }
}

@Composable
private fun ScanningView(
    progress: String,
    filesFound: Int,
    totalSize: Long
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Teal,
            strokeWidth = 4.dp,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = progress,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$filesFound files found • ${FileSize.format(totalSize)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyResultView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your device is clean!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No junk files were found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScanResultsList(
    categories: List<ScanCategory>,
    onToggleExpanded: (Int) -> Unit,
    onToggleCategorySelection: (Int) -> Unit,
    onToggleFileSelection: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        itemsIndexed(categories) { categoryIndex, category ->
            CategoryCard(
                category = category,
                onToggleExpanded = { onToggleExpanded(categoryIndex) },
                onToggleSelection = { onToggleCategorySelection(categoryIndex) },
                onToggleFileSelection = { fileIndex ->
                    onToggleFileSelection(categoryIndex, fileIndex)
                }
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) } // Space for bottom bar
    }
}

@Composable
private fun CategoryCard(
    category: ScanCategory,
    onToggleExpanded: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleFileSelection: (Int) -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (category.isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "expandRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Category Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = category.isAllSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Teal,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Icon(
                imageVector = getCategoryIcon(category.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${category.fileCount} files • ${FileSize.format(category.totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (category.isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expandable file list
        AnimatedVisibility(
            visible = category.isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                category.files.forEachIndexed { fileIndex, file ->
                    FileItem(
                        file = file,
                        onToggleSelection = { onToggleFileSelection(fileIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: ScannedFile,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = file.isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Teal,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = FileSize.format(file.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteBottomBar(
    selectedSize: Long,
    isDeleting: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            enabled = !isDeleting
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onError,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deleting...")
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Selected (${FileSize.format(selectedSize)})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    selectedSize: Long,
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Files?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("Are you sure you want to delete $selectedCount files (${FileSize.format(selectedSize)})? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getCategoryIcon(category: JunkCategory): ImageVector {
    return when (category) {
        JunkCategory.APP_CACHE -> Icons.Default.Folder
        JunkCategory.TEMP_FILES -> Icons.Default.InsertDriveFile
        JunkCategory.RESIDUAL_APKS -> Icons.Default.InsertDriveFile
        JunkCategory.PHOTOS -> Icons.Default.Photo
        JunkCategory.VIDEOS -> Icons.Default.VideoFile
        JunkCategory.AUDIO -> Icons.Default.MusicNote
        JunkCategory.DOCUMENTS -> Icons.Default.InsertDriveFile
        JunkCategory.TRASH -> Icons.Default.Delete
        JunkCategory.DUPLICATES -> Icons.Default.InsertDriveFile
        JunkCategory.LARGE_FILES -> Icons.Default.InsertDriveFile
        JunkCategory.EMPTY_FOLDERS -> Icons.Default.Folder
    }
}
