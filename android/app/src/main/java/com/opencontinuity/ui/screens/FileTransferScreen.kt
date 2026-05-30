package com.opencontinuity.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.features.filetransfer.FileTransfer
import com.opencontinuity.features.filetransfer.FileTransferManager
import com.opencontinuity.features.filetransfer.TransferDirection
import com.opencontinuity.features.filetransfer.TransferStatus
import com.opencontinuity.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// File Transfer Screen — Ethereal Noir
// All business logic unchanged; UI/UX fully restyled.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Business logic (unchanged) ───────────────────────────────────────────
    val fileTransferManager = OpenContinuityApp.instance.fileTransferManager
    val activeTransfers     by fileTransferManager.activeTransfers.collectAsState()
    var activeSaveTransfer  by remember { mutableStateOf<FileTransfer?>(null) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            activeSaveTransfer?.let { transfer ->
                scope.launch { fileTransferManager.saveToUri(transfer.id, destUri) }
            }
        }
        activeSaveTransfer = null
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { scope.launch { fileTransferManager.sendFile(it) } }
    }

    LaunchedEffect(Unit) { fileTransferManager.start() }
    DisposableEffect(Unit) { onDispose { fileTransferManager.stop() } }
    // ─────────────────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EtherealBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            FileTransferTopBar()

            // ── Page Title ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text  = "File Transfer",
                    style = EtherealHeadlineLg,
                    color = Color.White
                )
                Text(
                    text  = if (activeTransfers.isNotEmpty())
                        "${activeTransfers.count { it.status == TransferStatus.IN_PROGRESS }} Active Transfers"
                    else "No Active Transfers",
                    style = EtherealBodyMd,
                    color = EtherealOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Transfer List ────────────────────────────────────────────────
            if (activeTransfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(EtherealSurfaceContainerHighest.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = EtherealPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text  = "No transfers yet",
                            style = EtherealBodyLg,
                            color = EtherealOnSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "Tap + to send a file to your PC",
                            style = EtherealBodyMd,
                            color = EtherealOnSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeTransfers) { transfer ->
                        EtherealTransferItem(
                            transfer     = transfer,
                            onSaveAsClick = {
                                activeSaveTransfer = transfer
                                saveAsLauncher.launch(transfer.fileName)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // ── Floating Pink FAB ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(EtherealPrimary, HotPink)
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { filePickerLauncher.launch("*/*") }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Send File",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileTransferTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EtherealSurfaceContainer)
                .border(1.dp, EtherealPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = EtherealPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text  = "OpenContinuity",
            style = EtherealHeadlineMd,
            color = EtherealPrimary
        )

        Spacer(modifier = Modifier.weight(1f))

        // Spacer for symmetry
        Spacer(modifier = Modifier.size(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transfer Item — Ethereal Noir Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EtherealTransferItem(
    transfer: FileTransfer,
    onSaveAsClick: () -> Unit = {}
) {
    CrystalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EtherealSurfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (transfer.direction) {
                        TransferDirection.INCOMING -> Icons.Outlined.Description
                        TransferDirection.OUTGOING -> Icons.Outlined.Upload
                    },
                    contentDescription = null,
                    tint = EtherealPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File name + size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = transfer.fileName,
                    style    = EtherealBodyLg.copy(fontWeight = FontWeight.SemiBold),
                    color    = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = buildString {
                        append(formatFileSize(transfer.fileSize))
                        append(" • ")
                        append(when (transfer.status) {
                            TransferStatus.IN_PROGRESS -> "${(transfer.progress * 100).toInt()}% • transferring"
                            TransferStatus.PENDING     -> "Pending"
                            TransferStatus.COMPLETED   -> "Done"
                            TransferStatus.FAILED      -> "Failed"
                            TransferStatus.REJECTED    -> "Rejected"
                            TransferStatus.CANCELLED   -> "Cancelled"
                        })
                    },
                    style = EtherealBodyMd,
                    color = EtherealOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Circular progress / status indicator
            when (transfer.status) {
                TransferStatus.IN_PROGRESS -> {
                    CircularProgressRing(progress = transfer.progress)
                }
                TransferStatus.COMPLETED -> {
                    if (transfer.direction == TransferDirection.INCOMING) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EtherealSurfaceContainerHighest)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSaveAsClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = "Save file",
                                tint = EtherealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = ActiveGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                TransferStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EtherealSurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Pending",
                            tint = EtherealOnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                TransferStatus.CANCELLED, TransferStatus.FAILED, TransferStatus.REJECTED -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = EtherealError,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Circular progress ring (matching the reference design)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircularProgressRing(progress: Float) {
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val sweepAngle = 360f * progress
            // Track
            drawArc(
                color      = EtherealSurfaceContainerHighest,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                style      = stroke
            )
            // Progress
            drawArc(
                brush      = Brush.sweepGradient(
                    colors = listOf(HotPink, EtherealPrimary)
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter  = false,
                style      = stroke
            )
        }
        Text(
            text  = "${(progress * 100).toInt()}%",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy composables for backward compat
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TransferItem(transfer: FileTransfer, onSaveAsClick: () -> Unit = {}) {
    EtherealTransferItem(transfer = transfer, onSaveAsClick = onSaveAsClick)
}

@Composable
fun StatusChip(status: TransferStatus) {
    val (text, color) = when (status) {
        TransferStatus.PENDING     -> "Pending"     to EtherealOutline
        TransferStatus.IN_PROGRESS -> "Transferring" to EtherealPrimary
        TransferStatus.COMPLETED   -> "Completed"   to ActiveGreen
        TransferStatus.FAILED      -> "Failed"      to EtherealError
        TransferStatus.REJECTED    -> "Rejected"    to EtherealError
        TransferStatus.CANCELLED   -> "Cancelled"   to EtherealOutline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text     = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style    = EtherealLabelCaps,
            color    = color
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000         -> "%.2f KB".format(bytes / 1_000.0)
        else                    -> "$bytes B"
    }
}
