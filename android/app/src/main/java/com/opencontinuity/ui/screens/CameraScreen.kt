package com.opencontinuity.ui.screens

import androidx.compose.foundation.layout.*
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
import com.opencontinuity.features.camera.CameraStreamManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current

    val cameraManager = remember { CameraStreamManager(context) }
    val isStreaming by cameraManager.isStreaming.collectAsState()
    val currentResolution by cameraManager.currentResolution.collectAsState()
    val cameras = remember { cameraManager.getAvailableCameras() }

    var selectedCamera by remember { mutableStateOf(cameras.firstOrNull()) }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopStream()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera Webcam") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isStreaming)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isStreaming) "Streaming" else "Not Streaming",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isStreaming)
                                "${currentResolution.width}x${currentResolution.height}"
                            else
                                "Camera ready to stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Camera Selection
            Text(
                "Select Camera",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            cameras.forEach { camera ->
                Card(
                    onClick = { selectedCamera = camera },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedCamera == camera)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (camera.isFront)
                                Icons.Default.CameraFront
                            else
                                Icons.Default.CameraRear,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(camera.name)
                        Spacer(modifier = Modifier.weight(1f))
                        if (selectedCamera == camera) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resolution Selection
            Text(
                "Resolution",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraStreamManager.SUPPORTED_RESOLUTIONS.forEach { resolution ->
                    FilterChip(
                        selected = currentResolution == resolution,
                        onClick = { cameraManager.setResolution(resolution) },
                        label = { Text("${resolution.width}x${resolution.height}") }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start/Stop Button
            Button(
                onClick = {
                    if (isStreaming) {
                        cameraManager.stopStream()
                    } else {
                        selectedCamera?.let { cam ->
                            cameraManager.startStream(cam.id)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "The camera feed will be available as a virtual webcam on your PC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
