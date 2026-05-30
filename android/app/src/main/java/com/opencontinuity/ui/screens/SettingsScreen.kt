package com.opencontinuity.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen — Ethereal Noir
// All business logic unchanged; UI/UX fully restyled.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    // ── State (unchanged) ────────────────────────────────────────────────────
    var autoStartEnabled        by remember { mutableStateOf(true) }
    var clipboardSyncEnabled    by remember { mutableStateOf(true) }
    var notificationSyncEnabled by remember { mutableStateOf(true) }
    var smsSyncEnabled          by remember { mutableStateOf(true) }
    var screenshotSyncEnabled   by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EtherealBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        SettingsTopBar()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // ── Page Title ───────────────────────────────────────────────────
            Column {
                Text(
                    text  = "Settings & Sync",
                    style = EtherealHeadlineLg,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "Manage your connection features and global preferences.",
                    style = EtherealBodyMd,
                    color = EtherealOnSurfaceVariant
                )
            }

            // ── GENERAL ──────────────────────────────────────────────────────
            SettingsSectionCard(label = "GENERAL") {
                SettingsToggleRow(
                    title           = "Auto-start on boot",
                    subtitle        = "Launch Continuity seamlessly.",
                    icon            = Icons.Default.PowerSettingsNew,
                    checked         = autoStartEnabled,
                    onCheckedChange = { autoStartEnabled = it }
                )
            }

            // ── SYNC FEATURES ─────────────────────────────────────────────────
            SettingsSectionCard(label = "SYNC FEATURES") {
                SettingsToggleRow(
                    title           = "Clipboard Sync",
                    subtitle        = "Share clipboard across devices.",
                    icon            = Icons.Default.ContentPaste,
                    checked         = clipboardSyncEnabled,
                    onCheckedChange = { clipboardSyncEnabled = it }
                )
                EtherealDivider()
                SettingsToggleRow(
                    title           = "Notification Sync",
                    subtitle        = "Mirror notifications locally.",
                    icon            = Icons.Default.Notifications,
                    checked         = notificationSyncEnabled,
                    onCheckedChange = { notificationSyncEnabled = it }
                )
                EtherealDivider()
                SettingsToggleRow(
                    title           = "SMS Sync",
                    subtitle        = "Send/Receive texts on desktop.",
                    icon            = Icons.Default.Sms,
                    checked         = smsSyncEnabled,
                    onCheckedChange = { smsSyncEnabled = it }
                )
                EtherealDivider()
                SettingsToggleRow(
                    title           = "Screenshot Sync",
                    subtitle        = "Auto-push screenshots.",
                    icon            = Icons.Default.Screenshot,
                    checked         = screenshotSyncEnabled,
                    onCheckedChange = { screenshotSyncEnabled = it }
                )
            }

            // ── PERMISSIONS ───────────────────────────────────────────────────
            SettingsSectionCard(label = "PERMISSIONS") {
                EtherealClickableRow(
                    title   = "Notification Access",
                    subtitle = "Required for notification sync",
                    icon    = Icons.Default.Notifications,
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
                EtherealDivider()
                EtherealClickableRow(
                    title   = "Accessibility Service",
                    subtitle = "Required for remote control",
                    icon    = Icons.Default.Accessibility,
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                EtherealDivider()
                EtherealClickableRow(
                    title   = "Battery Optimization",
                    subtitle = "Disable for reliable background operation",
                    icon    = Icons.Default.BatteryFull,
                    onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                )
            }

            // ── ABOUT ─────────────────────────────────────────────────────────
            SettingsSectionCard(label = "ABOUT") {
                EtherealClickableRow(
                    title   = "Version",
                    subtitle = "1.0.0",
                    icon    = Icons.Default.Info,
                    onClick = {}
                )
                EtherealDivider()
                EtherealClickableRow(
                    title   = "Open Source Licenses",
                    subtitle = "View third-party licenses",
                    icon    = Icons.Default.Description,
                    onClick = {}
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
// Section Card (private — glass card wrapping toggle/clickable rows)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text  = label,
            style = EtherealLabelCaps,
            color = EtherealPrimary
        )
        CrystalCard(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Clickable Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EtherealClickableRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EtherealSurfaceContainer.copy(alpha = 0.5f))
                .border(1.dp, EtherealOutlineVariant.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EtherealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = EtherealBodyLg.copy(fontWeight = FontWeight.Medium),
                color = EtherealOnSurface
            )
            Text(
                text  = subtitle,
                style = EtherealBodyMd,
                color = EtherealOnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = EtherealOnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy public composables — kept for backward compatibility with any callers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text  = title,
            style = EtherealLabelCaps,
            color = EtherealPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        Divider(
            modifier = Modifier.padding(vertical = 8.dp),
            color    = EtherealOutlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsToggleRow(
        title           = title,
        subtitle        = subtitle,
        icon            = icon,
        checked         = checked,
        onCheckedChange = onCheckedChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickableSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    EtherealClickableRow(
        title   = title,
        subtitle = subtitle,
        icon    = icon,
        onClick = onClick
    )
}
