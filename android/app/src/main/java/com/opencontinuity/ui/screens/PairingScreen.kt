package com.opencontinuity.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(navController: NavController) {
    val context = LocalContext.current

    // All manager access wrapped in try/catch to prevent crash if app state is bad
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

    // FIX: Use buildJsonObject instead of mapOf() which creates Map<String,Any>
    // (kotlinx.serialization cannot serialize Any and crashes at runtime)
    val qrData = remember(localIp, publicKey, pairingCode) {
        try {
            buildJsonObject {
                put("host", localIp ?: "0.0.0.0")
                put("port", ConnectionManager.DEFAULT_PORT)
                put("httpPort", ConnectionManager.HTTP_PORT)
                put("publicKey", publicKey)
                put("pairingCode", pairingCode)
                put("deviceName", android.os.Build.MODEL)
            }.toString()
        } catch (e: Exception) {
            Log.e("PairingScreen", "QR data build failed", e)
            "{}"
        }
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scanErrorMessage = "Camera permission denied. Please allow camera access in Settings."
        }
        // If granted, user can tap scan again — don't auto-launch to avoid double-launch issues
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { previewBitmap ->
        if (previewBitmap == null) {
            // User cancelled — not an error
            return@rememberLauncherForActivityResult
        }
        scanQrFromBitmap(
            bitmap = previewBitmap,
            onSuccess = { scannedText ->
                val parsed = parsePairingQr(scannedText)
                if (parsed == null) {
                    scanErrorMessage = "QR detected, but it is not a valid OpenContinuity pairing code."
                } else {
                    scanErrorMessage = null
                    scanResultMessage = buildString {
                        appendLine("Device: ${parsed.deviceName ?: "Unknown"}")
                        appendLine("Host: ${parsed.host}")
                        appendLine("Port: ${parsed.port}")
                        append("Ready to connect!")
                    }
                }
            },
            onError = { error ->
                scanErrorMessage = error
            }
        )
    }

    LaunchedEffect(qrData) {
        qrBitmap = generateQRCode(qrData, 300)
    }

    LaunchedEffect(pairingCode) {
        try {
            OpenContinuityApp.instance.connectionManager.setActivePairingCode(pairingCode)
        } catch (e: Exception) {
            Log.e("PairingScreen", "setActivePairingCode failed", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Pairing") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pair with Windows PC",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Show this QR code to the Windows app, or use the scan button to scan the PC's QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code
            Card(
                modifier = Modifier.size(280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(250.dp)
                        )
                    } ?: CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("IP Address", style = MaterialTheme.typography.labelMedium)
                        Text(localIp ?: "Not available", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Port", style = MaterialTheme.typography.labelMedium)
                        Text("${ConnectionManager.DEFAULT_PORT}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pairing Code", style = MaterialTheme.typography.labelMedium)
                        Text(
                            pairingCode.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scan QR button — checks camera permission first
            Button(
                onClick = {
                    scanErrorMessage = null
                    val hasCam = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasCam) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        try {
                            scanLauncher.launch(null)
                        } catch (e: Exception) {
                            Log.e("PairingScreen", "Failed to launch scanner", e)
                            scanErrorMessage = "Unable to open camera: ${e.message ?: "unknown error"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Windows QR")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Manual Connection
            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manual Connection")
            }

            // Error display
            scanErrorMessage?.let { errorText ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Scan result dialog
        if (scanResultMessage != null) {
            AlertDialog(
                onDismissRequest = { scanResultMessage = null },
                title = { Text("Pairing QR Scanned") },
                text = { Text(scanResultMessage!!) },
                confirmButton = {
                    TextButton(onClick = { scanResultMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }

        // Manual connection dialog
        if (showManualDialog) {
            ManualConnectionDialog(onDismiss = { showManualDialog = false })
        }
    }
}

@Composable
private fun ManualConnectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val localIp = remember {
        try { OpenContinuityApp.instance.discoveryManager.getLocalIpAddress() }
        catch (_: Exception) { null }
    }
    val port = ConnectionManager.DEFAULT_PORT.toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect from Windows") },
        text = {
            Column {
                Text(
                    "OpenContinuity on Windows connects to this phone. Enter these details in the Windows app (Pairing → Manual):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("IP: ${localIp ?: "Unavailable"}", style = MaterialTheme.typography.bodyLarge)
                Text("Port: $port", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Keep the connection notification visible and disable battery optimization for best results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = android.content.Intent(context, com.opencontinuity.services.ConnectionService::class.java).apply {
                    action = com.opencontinuity.services.ConnectionService.ACTION_START
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                onDismiss()
            }) { Text("Start service") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

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
    } catch (_: Exception) {
        null
    }
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
    val image = InputImage.fromBitmap(bitmap, 0)

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
        .addOnCompleteListener {
            scanner.close()
        }
}

private fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
