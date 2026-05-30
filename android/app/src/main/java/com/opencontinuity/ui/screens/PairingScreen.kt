package com.opencontinuity.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────────
// Pairing Screen — Ethereal Noir
// All business logic unchanged; only UI/UX restyled.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(navController: NavController) {
    val context = LocalContext.current

    // ── Business logic (unchanged) ───────────────────────────────────────────
    val localIp = remember {
        try { OpenContinuityApp.instance.discoveryManager.getLocalIpAddress() }
        catch (e: Exception) { Log.e("PairingScreen", "getLocalIpAddress failed", e); null }
    }
    val publicKey = remember {
        try { OpenContinuityApp.instance.securityManager.getPublicKeyBase64() }
        catch (e: Exception) { Log.e("PairingScreen", "getPublicKeyBase64 failed", e); "" }
    }
    val pairingCode = remember {
        try { OpenContinuityApp.instance.securityManager.generatePairingCode() }
        catch (e: Exception) { Log.e("PairingScreen", "generatePairingCode failed", e); "000000" }
    }

    val qrData = remember(localIp, publicKey, pairingCode) {
        try {
            buildJsonObject {
                put("host",        localIp ?: "0.0.0.0")
                put("port",        ConnectionManager.DEFAULT_PORT)
                put("httpPort",    ConnectionManager.HTTP_PORT)
                put("publicKey",   publicKey)
                put("pairingCode", pairingCode)
                put("deviceName",  android.os.Build.MODEL)
            }.toString()
        } catch (e: Exception) {
            Log.e("PairingScreen", "QR data build failed", e)
            "{}"
        }
    }

    var qrBitmap         by remember { mutableStateOf<Bitmap?>(null) }
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var scanErrorMessage  by remember { mutableStateOf<String?>(null) }
    var showManualDialog  by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scanErrorMessage = "Camera permission denied. Please allow camera access in Settings."
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { previewBitmap ->
        if (previewBitmap == null) return@rememberLauncherForActivityResult
        scanQrFromBitmap(
            bitmap   = previewBitmap,
            onSuccess = { scannedText ->
                val parsed = parsePairingQr(scannedText)
                if (parsed == null) {
                    scanErrorMessage = "QR detected, but it is not a valid OpenContinuity pairing code."
                } else {
                    scanErrorMessage  = null
                    scanResultMessage = buildString {
                        appendLine("Device: ${parsed.deviceName ?: "Unknown"}")
                        appendLine("Host: ${parsed.host}")
                        appendLine("Port: ${parsed.port}")
                        append("Ready to connect!")
                    }
                }
            },
            onError = { error -> scanErrorMessage = error }
        )
    }

    LaunchedEffect(qrData) {
        qrBitmap = generateQRCode(qrData, 300)
    }
    // ─────────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EtherealBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        PairingTopBar(onBack = { navController.popBackStack() })

        // ── Body ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pair with Windows PC",
                    style = EtherealHeadlineLg,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan this code with your desktop app to\nestablish a secure connection.",
                    style = EtherealBodyMd,
                    color = EtherealOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // QR Card
            CrystalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // QR bitmap inside a white-bordered box
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.95f),
                                        Color.White
                                    )
                                )
                            )
                            .border(
                                width = 3.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x884B0826),
                                        Color.White.copy(alpha = 0.9f),
                                        Color(0x884B0826)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(190.dp)
                            )
                        } ?: CircularProgressIndicator(
                            color = HotPink,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Encrypted badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EtherealPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "End-to-End Encrypted",
                            style = EtherealBodyMd,
                            color = EtherealPrimary
                        )
                    }
                }
            }

            // IP + Port row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoCard(
                    label = "IP Address",
                    value = localIp ?: "Not available",
                    modifier = Modifier.weight(1f)
                )
                InfoCard(
                    label = "Port",
                    value = "${ConnectionManager.DEFAULT_PORT}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Pairing code card
            CrystalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pairing Code",
                            style = EtherealBodyMd,
                            color = EtherealOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pairingCode.chunked(3).joinToString(" - "),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EtherealSurfaceContainerHighest)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy pairing code",
                            tint = EtherealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Error message
            scanErrorMessage?.let { err ->
                CrystalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = EtherealError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = err,
                            style = EtherealBodyMd,
                            color = EtherealError
                        )
                    }
                }
            }

            // Scan Windows QR — primary pink button
            GlowingPinkButton(
                text = "Scan Windows QR",
                leadingIcon = Icons.Default.QrCodeScanner,
                onClick = {
                    scanErrorMessage = null
                    val hasCam = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasCam) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        try { scanLauncher.launch(null) }
                        catch (e: Exception) {
                            Log.e("PairingScreen", "Failed to launch scanner", e)
                            scanErrorMessage = "Unable to open camera: ${e.message ?: "unknown error"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Manual Connection — outlined dark button
            EtherealOutlinedButton(
                text = "Manual Connection",
                leadingIcon = Icons.Default.Edit,
                onClick = { showManualDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // ── Dialogs (logic unchanged) ────────────────────────────────────────────
    if (scanResultMessage != null) {
        AlertDialog(
            onDismissRequest = { scanResultMessage = null },
            title = { Text("Pairing QR Scanned", color = EtherealOnSurface) },
            text  = { Text(scanResultMessage!!, color = EtherealOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { scanResultMessage = null }) {
                    Text("OK", color = EtherealPrimary)
                }
            },
            containerColor = EtherealSurfaceContainer,
            shape = RoundedCornerShape(24.dp)
        )
    }
    if (showManualDialog) {
        ManualConnectionDialog(onDismiss = { showManualDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PairingTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back arrow
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EtherealSurfaceContainerHighest.copy(alpha = 0.5f))
                .border(1.dp, EtherealOutlineVariant.copy(alpha = 0.3f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = EtherealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "OpenContinuity",
            style = EtherealHeadlineMd,
            color = EtherealPrimary
        )

        Spacer(modifier = Modifier.weight(1f))

        // Settings icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EtherealSurfaceContainerHighest.copy(alpha = 0.5f))
                .border(1.dp, EtherealOutlineVariant.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = EtherealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Card (IP / Port)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    CrystalCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = EtherealBodyMd,
                color = EtherealOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Manual Connection Dialog (unchanged logic, restyled)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ManualConnectionDialog(onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Connection", color = EtherealOnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter the Windows PC's IP address:", color = EtherealOnSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("e.g. 192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EtherealPrimary,
                        unfocusedBorderColor = EtherealOutlineVariant,
                        focusedLabelColor = EtherealPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EtherealPrimary,
                        unfocusedBorderColor = EtherealOutlineVariant,
                        focusedLabelColor = EtherealPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Connect", color = EtherealPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = EtherealOnSurfaceVariant)
            }
        },
        containerColor = EtherealSurfaceContainer,
        shape = RoundedCornerShape(24.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Data classes & helper functions (UNCHANGED — same logic as before)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class PairingQrData(
    val host: String,
    val port: Int,
    val httpPort: Int? = null,
    val publicKey: String? = null,
    val pairingCode: String? = null,
    val deviceName: String? = null
)

private fun parsePairingQr(raw: String): PairingQrData? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString<PairingQrData>(raw)
    } catch (_: Exception) { null }
}

private fun scanQrFromBitmap(
    bitmap: Bitmap,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = BarcodeScanning.getClient(options)
    val image   = InputImage.fromBitmap(bitmap, 0)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull()?.rawValue
            if (value.isNullOrBlank()) {
                onError("No QR code found. Try scanning again in better lighting.")
            } else {
                onSuccess(value)
            }
        }
        .addOnFailureListener { e ->
            onError("QR scan failed: ${e.message ?: "unknown error"}")
        }
        .addOnCompleteListener { scanner.close() }
}

private fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer    = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap    = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AColor.BLACK else AColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}
