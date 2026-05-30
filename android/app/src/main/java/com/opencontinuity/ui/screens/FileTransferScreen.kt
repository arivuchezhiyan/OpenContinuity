package com.opencontinuity.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.features.filetransfer.FileTransfer
import com.opencontinuity.features.filetransfer.FileTransferManager
import com.opencontinuity.features.filetransfer.TransferDirection
import com.opencontinuity.features.filetransfer.TransferStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fileTransferManager = OpenContinuityApp.instance.fileTransferManager

    val activeTransfers by fileTransferManager.activeTransfers.collectAsState()

    var activeSaveTransfer by remember { mutableStateOf<FileTransfer?>(null) }
    
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            activeSaveTransfer?.let { transfer ->
                scope.launch {
                    fileTransferManager.saveToUri(transfer.id, destUri)
                }
            }
        }
        activeSaveTransfer = null
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                fileTransferManager.sendFile(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        fileTransferManager.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            fileTransferManager.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Transfer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch("*/*") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Send File")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Tap the + button to send files to your PC. Files from PC will appear here automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeTransfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No transfers yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Text(
                    "Transfers",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(activeTransfers) { transfer ->
                        TransferItem(
                            transfer = transfer,
                            onSaveAsClick = {
                                activeSaveTransfer = transfer
                                saveAsLauncher.launch(transfer.fileName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransferItem(transfer: FileTransfer, onSaveAsClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (transfer.direction) {
                        TransferDirection.INCOMING -> Icons.Default.Download
                        TransferDirection.OUTGOING -> Icons.Default.Upload
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatFileSize(transfer.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = transfer.status)
            }

            if (transfer.status == TransferStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = transfer.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(transfer.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (transfer.status == TransferStatus.COMPLETED && transfer.direction == TransferDirection.INCOMING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSaveAsClick) {
                        Text("Save As...")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: TransferStatus) {
    val (text, color) = when (status) {
        TransferStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.outline
        TransferStatus.IN_PROGRESS -> "Transferring" to MaterialTheme.colorScheme.primary
        TransferStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.tertiary
        TransferStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        TransferStatus.REJECTED -> "Rejected" to MaterialTheme.colorScheme.error
        TransferStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
