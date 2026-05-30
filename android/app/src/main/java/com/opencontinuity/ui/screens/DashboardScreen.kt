package com.opencontinuity.ui.screens

import android.os.Build
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.ui.navigation.Screen
import com.opencontinuity.ui.theme.*
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard Screen — Proper Ethereal Noir Glassmorphism
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(navController: NavController) {
    val connectionManager = OpenContinuityApp.instance.connectionManager
    val connectionState   by connectionManager.connectionState.collectAsState()
    val batteryMonitor    = OpenContinuityApp.instance.batteryMonitor
    val batteryStatus     by batteryMonitor.batteryStatus.collectAsState()
    val connectedClients  by connectionManager.connectedClients.collectAsState()
    val deviceName        = Build.MODEL

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11  -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else      -> "Good Night,"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EtherealBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 52.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        DashboardHeader(
            greeting     = greeting,
            deviceName   = deviceName,
            batteryLevel = batteryStatus?.level,
            isCharging   = batteryStatus?.isCharging ?: false
        )

        // ── Search Row ──────────────────────────────────────────────────────
        DashboardSearchRow()

        // ── Hero Connection Card ─────────────────────────────────────────────
        HeroConnectionCard(
            connectionState = connectionState,
            onArrowClick    = { navController.navigate(Screen.Pairing.route) },
            navController   = navController
        )

        // ── Continuity Tools ─────────────────────────────────────────────────
        EtherealSectionHeader(
            title          = "Continuity Tools",
            trailingText   = "Manage",
            onTrailingClick = { navController.navigate(Screen.Settings.route) }
        )
        ContinuityToolsGrid(navController = navController)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header with Glowing Avatar Ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    greeting: String,
    deviceName: String,
    batteryLevel: Int?,
    isCharging: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        // Left — greeting
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = greeting,
                fontSize = 15.sp,
                color = EtherealOnSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = deviceName,
                    style = EtherealDisplayStyle,
                    color = EtherealOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint     = HotPink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = "Your phone ↔ PC bridge.",
                    fontSize = 13.sp,
                    color = EtherealOnSurfaceVariant.copy(alpha = 0.6f)
                )
                if (batteryLevel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text  = "$batteryLevel%${if (isCharging) " ⚡" else ""}",
                        fontSize = 12.sp,
                        color = EtherealPrimary.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right — Glowing avatar ring (matching reference exactly)
        Box(modifier = Modifier.size(76.dp)) {
            GlowingAvatarRing(size = 76.dp) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Device",
                    tint     = EtherealPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            // Notification badge
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(EtherealPrimary, HotPink)
                        )
                    )
                    .border(2.dp, EtherealBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Row — Glass search bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardSearchRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Search field — glass crystal
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3D1828),
                            Color(0xFF1E0B18)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    // Glass rim highlight
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x33FFFFFF),
                                Color.Transparent
                            )
                        ),
                        size  = Size(size.width, 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(30.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawRoundRect(
                        color        = Color(0x33FF78B7),
                        cornerRadius = CornerRadius(30.dp.toPx()),
                        style        = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint     = EtherealOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text  = "Search anything...",
                color = EtherealOnSurfaceVariant.copy(alpha = 0.45f),
                fontSize = 15.sp
            )
        }

        // Filter button — glass square
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF3D1828), Color(0xFF1E0B18))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color        = Color(0x33FF78B7),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style        = Stroke(width = 1.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filter",
                tint     = EtherealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Connection Card — with inner atmospheric glow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroConnectionCard(
    connectionState: ConnectionState,
    onArrowClick: () -> Unit,
    navController: NavController
) {
    val isConnected = connectionState is ConnectionState.Connected
    val deviceDisplayName = when (val s = connectionState) {
        is ConnectionState.Connected -> s.deviceName
        is ConnectionState.Listening -> "Port ${s.port}"
        is ConnectionState.Error     -> "Error"
        else                          -> "Port 8765"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            // ── Warm glass fill ───────────────────────────────────────────
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF3D1828),
                        0.5f to Color(0xFF2A1020),
                        1.0f to Color(0xFF1A0A14)
                    )
                )
            )
            // ── Atmospheric inner glow (pink light from upper right) ──────
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x44FF4F96),
                            0.5f to Color(0x22FF3070),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.minDimension * 1.1f
                    )
                )
                // Secondary warm glow bottom-left
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x334B0826),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.1f, size.height * 0.9f),
                        radius = size.minDimension * 0.9f
                    )
                )
            }
            // ── Glass border ──────────────────────────────────────────────
            .drawWithContent {
                drawContent()
                val cr = 24.dp.toPx()
                // Top rim highlight
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0x55FFFFFF), Color(0x22FFFFFF), Color.Transparent)
                    ),
                    size  = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Full border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x66FF78B7),
                            0.5f to Color(0x22FF5090),
                            1.0f to Color(0x33FF78B7)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        // Content
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LaptopMac,
                contentDescription = null,
                tint     = EtherealPrimary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = if (isConnected) "Connected" else "Disconnected",
                style = EtherealHeadlineLg,
                color = Color.White
            )
            if (isConnected) {
                Text(
                    text  = "to $deviceDisplayName",
                    style = EtherealHeadlineLg.copy(fontWeight = FontWeight.Normal),
                    color = EtherealPrimary
                )
            } else {
                Text(
                    text  = deviceDisplayName,
                    style = EtherealBodyLg,
                    color = EtherealOnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Status pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A0A14).copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        color = if (isConnected) EtherealPrimary.copy(alpha = 0.4f)
                                else EtherealOutlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) ActiveGreen else EtherealOutlineVariant)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text  = if (isConnected) "ACTIVE SESSION" else "NO SESSION",
                    style = EtherealLabelCaps,
                    color = EtherealOnSurface.copy(alpha = 0.85f)
                )
            }

            // Start button
            if (connectionState is ConnectionState.Disconnected || connectionState is ConnectionState.Error) {
                Spacer(modifier = Modifier.height(6.dp))
                val context = LocalContext.current
                TextButton(onClick = {
                    val intent = android.content.Intent(context, com.opencontinuity.services.ConnectionService::class.java).apply {
                        action = com.opencontinuity.services.ConnectionService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        androidx.core.content.ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                }) {
                    Text("Start Service", color = EtherealPrimary)
                }
            }
        }

        // Arrow FAB — gradient circle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(48.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(HotPink.copy(alpha = 0.6f), Color.Transparent),
                            radius = size.minDimension * 0.8f
                        )
                    )
                }
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(colors = listOf(EtherealPrimary, HotPink))
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onArrowClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Grid — Bento cards matching the reference
// ─────────────────────────────────────────────────────────────────────────────

private data class ToolItem(
    val name: String,
    val icon: ImageVector,
    val badge: Boolean = false,
    val subtitle: String? = null,
    val colSpan: Int = 1,
    val route: String? = null
)

@Composable
private fun ContinuityToolsGrid(navController: NavController) {
    val tools = listOf(
        ToolItem("Clipboard",     Icons.Outlined.ContentPaste),
        ToolItem("Files",         Icons.Outlined.FolderCopy,    route = Screen.FileTransfer.route),
        ToolItem("Messages",      Icons.Outlined.Sms,           badge = true, route = Screen.Sms.route),
        ToolItem("Camera",        Icons.Outlined.PhotoCamera,   route = Screen.Camera.route),
        ToolItem("Screen Mirror", Icons.Outlined.Cast,          subtitle = "Ready to cast", colSpan = 2, route = Screen.ScreenMirror.route),
        ToolItem("Touchpad",      Icons.Outlined.Mouse,         route = Screen.Touchpad.route),
        ToolItem("Power",         Icons.Outlined.BatteryFull)
    )

    // Row 1 — 4 equal tiles
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.take(4).forEach { tool ->
            GlassToolTile(tool = tool, modifier = Modifier.weight(1f)) {
                tool.route?.let { navController.navigate(it) }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Row 2 — wide Screen Mirror + Touchpad + Power
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassToolTileWide(tool = tools[4], modifier = Modifier.weight(2f)) {
            tools[4].route?.let { navController.navigate(it) }
        }
        GlassToolTile(tool = tools[5], modifier = Modifier.weight(1f)) {
            tools[5].route?.let { navController.navigate(it) }
        }
        GlassToolTile(tool = tools[6], modifier = Modifier.weight(1f)) {}
    }
}

@Composable
private fun GlassToolTile(
    tool: ToolItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            // Warm glass fill
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF3A1625),
                        1.0f to Color(0xFF1C0912)
                    )
                )
            )
            // Inner pink glow
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1AFF4F96), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.2f),
                        radius = size.minDimension * 0.8f
                    )
                )
            }
            // Glass border + rim highlight
            .drawWithContent {
                drawContent()
                val cr = 20.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0x44FFFFFF), Color.Transparent)
                    ),
                    size  = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x44FF78B7), Color(0x15FF5090)),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(imageVector = tool.icon, contentDescription = tool.name, tint = EtherealPrimary, modifier = Modifier.size(28.dp))
                if (tool.badge) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(HotPink)
                            .border(1.dp, Color(0xFF1C0912), CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text   = tool.name,
                style  = EtherealLabelCaps,
                color  = EtherealOnSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines  = 2
            )
        }
    }
}

@Composable
private fun GlassToolTileWide(
    tool: ToolItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(2f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF3A1625),
                        1.0f to Color(0xFF1C0912)
                    )
                )
            )
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1AFF4F96), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.3f),
                        radius = size.minDimension * 1.1f
                    )
                )
            }
            .drawWithContent {
                drawContent()
                val cr = 20.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0x44FFFFFF), Color.Transparent)
                    ),
                    size  = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x44FF78B7), Color(0x15FF5090)),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(imageVector = tool.icon, contentDescription = tool.name, tint = EtherealPrimary, modifier = Modifier.size(30.dp))
            Column {
                Text(text = tool.name, color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (tool.subtitle != null) {
                    Text(text = tool.subtitle, style = EtherealBodyMd, color = EtherealOnSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Keep legacy composables for backward compatibility
// ─────────────────────────────────────────────────────────────────────────────

data class FeatureItem(
    val name: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: FeatureItem, onClick: (() -> Unit)?) {
    CrystalCard(
        modifier = Modifier.padding(4.dp).aspectRatio(1f).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.name,
                modifier = Modifier.size(30.dp),
                tint = if (feature.enabled) EtherealPrimary else EtherealOnSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = feature.name,
                style = EtherealLabelCaps,
                textAlign = TextAlign.Center,
                color = if (feature.enabled) EtherealOnSurface else EtherealOnSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
