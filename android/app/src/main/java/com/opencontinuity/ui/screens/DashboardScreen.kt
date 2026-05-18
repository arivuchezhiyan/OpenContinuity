package com.opencontinuity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val connectionManager = OpenContinuityApp.instance.connectionManager
    val connectionState by connectionManager.connectionState.collectAsState()
    val batteryMonitor = OpenContinuityApp.instance.batteryMonitor
    val batteryStatus by batteryMonitor.batteryStatus.collectAsState()
    val connectedClients by connectionManager.connectedClients.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "OpenContinuity",
                style = MaterialTheme.typography.headlineLarge
            )
            
            // Dashboard Battery Info
            val currentBatteryStatus = batteryStatus
            if (currentBatteryStatus != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (currentBatteryStatus.level > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentBatteryStatus.level}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (currentBatteryStatus.isCharging) {
                            Text(
                                text = " ⚡",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                    is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        is ConnectionState.Connected -> Icons.Default.Link
                        is ConnectionState.Listening -> Icons.Default.Wifi
                        is ConnectionState.Error -> Icons.Default.Error
                        else -> Icons.Default.LinkOff
                    },
                    contentDescription = "Connection Status",
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected -> "Connected"
                            is ConnectionState.Listening -> "Waiting for connection"
                            is ConnectionState.Error -> "Error"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when (val state = connectionState) {
                            is ConnectionState.Connected -> state.deviceName
                            is ConnectionState.Listening -> "Port ${state.port}"
                            is ConnectionState.Error -> state.message
                            else -> "Service not running"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (connectionState is ConnectionState.Disconnected || connectionState is ConnectionState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Button(
                            onClick = { 
                                val intent = android.content.Intent(context, com.opencontinuity.services.ConnectionService::class.java).apply {
                                    action = com.opencontinuity.services.ConnectionService.ACTION_START
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                                } else {
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Start Service")
                        }
                    }
                }
            }
        }

        // Connected Clients
        if (connectedClients.isNotEmpty()) {
            Text(
                text = "Connected Devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            connectedClients.forEach { client ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Device"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = client.deviceName)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feature Grid
        Text(
            text = "Features",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        val features = listOf(
            FeatureItem("Clipboard", Icons.Default.ContentPaste, true),
            FeatureItem("File Transfer", Icons.Default.Folder, true) { navController.navigate(Screen.FileTransfer.route) },
            FeatureItem("Notifications", Icons.Default.Notifications, true),
            FeatureItem("SMS", Icons.Default.Sms, true) { navController.navigate(Screen.Sms.route) },
            FeatureItem("Camera", Icons.Default.CameraAlt, true) { navController.navigate(Screen.Camera.route) },
            FeatureItem("Screen Mirror", Icons.Default.ScreenShare, true) { navController.navigate(Screen.ScreenMirror.route) },
            FeatureItem("Battery", Icons.Default.BatteryFull, true),
            FeatureItem("Touchpad", Icons.Default.TouchApp, true) { navController.navigate(Screen.Touchpad.route) },
            FeatureItem("Remote Control", Icons.Default.Mouse, true),
            FeatureItem("PC Unlock", Icons.Default.Lock, false),
            FeatureItem("Screenshots", Icons.Default.Screenshot, true),
            FeatureItem("Settings", Icons.Default.Settings, true) { navController.navigate(Screen.Settings.route) }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(features) { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = feature.onClick
                )
            }
        }
    }
}

data class FeatureItem(
    val name: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: FeatureItem, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (feature.enabled) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.name,
                modifier = Modifier.size(32.dp),
                tint = if (feature.enabled) 
                    MaterialTheme.colorScheme.onSecondaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = if (feature.enabled) 
                    MaterialTheme.colorScheme.onSecondaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
