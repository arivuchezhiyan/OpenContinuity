package com.opencontinuity.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.features.unlock.UnlockHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val connectionState by OpenContinuityApp.instance.connectionManager.connectionState.collectAsState()

    var autoStartEnabled by remember { mutableStateOf(true) }
    var clipboardSyncEnabled by remember { mutableStateOf(true) }
    var notificationSyncEnabled by remember { mutableStateOf(true) }
    var smsSyncEnabled by remember { mutableStateOf(true) }
    var screenshotSyncEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            // General Settings
            SettingsSection(title = "General") {
                SwitchSettingItem(
                    title = "Auto-start on boot",
                    subtitle = "Start the connection service when device boots",
                    icon = Icons.Default.PowerSettingsNew,
                    checked = autoStartEnabled,
                    onCheckedChange = { autoStartEnabled = it }
                )
            }

            // Feature Settings
            SettingsSection(title = "Features") {
                SwitchSettingItem(
                    title = "Clipboard Sync",
                    subtitle = "Sync clipboard between devices",
                    icon = Icons.Default.ContentPaste,
                    checked = clipboardSyncEnabled,
                    onCheckedChange = { clipboardSyncEnabled = it }
                )

                SwitchSettingItem(
                    title = "Notification Sync",
                    subtitle = "Send notifications to PC",
                    icon = Icons.Default.Notifications,
                    checked = notificationSyncEnabled,
                    onCheckedChange = { notificationSyncEnabled = it }
                )

                SwitchSettingItem(
                    title = "SMS Sync",
                    subtitle = "Send and receive SMS from PC",
                    icon = Icons.Default.Sms,
                    checked = smsSyncEnabled,
                    onCheckedChange = { smsSyncEnabled = it }
                )

                SwitchSettingItem(
                    title = "Screenshot Sync",
                    subtitle = "Auto-upload screenshots to PC",
                    icon = Icons.Default.Screenshot,
                    checked = screenshotSyncEnabled,
                    onCheckedChange = { screenshotSyncEnabled = it }
                )
            }

            // PC wake (preview)
            if (connectionState is ConnectionState.Connected) {
                SettingsSection(title = "Windows PC") {
                    ClickableSettingItem(
                        title = "Wake PC display",
                        subtitle = "Sends a signal to turn on your PC screen (lock PIN still required)",
                        icon = Icons.Default.Computer,
                        onClick = { UnlockHelper.requestPcWake("settings") }
                    )
                }
            }

            // Permissions
            SettingsSection(title = "Permissions") {
                ClickableSettingItem(
                    title = "Notification Access",
                    subtitle = "Required for notification sync",
                    icon = Icons.Default.Notifications,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )

                ClickableSettingItem(
                    title = "Accessibility Service",
                    subtitle = "Required for remote control",
                    icon = Icons.Default.Accessibility,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                ClickableSettingItem(
                    title = "Battery Optimization",
                    subtitle = "Disable for reliable background operation",
                    icon = Icons.Default.BatteryFull,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
            }

            // About
            SettingsSection(title = "About") {
                ClickableSettingItem(
                    title = "Version",
                    subtitle = "1.0.0",
                    icon = Icons.Default.Info,
                    onClick = { }
                )

                ClickableSettingItem(
                    title = "Open Source Licenses",
                    subtitle = "View third-party licenses",
                    icon = Icons.Default.Description,
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickableSettingItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
