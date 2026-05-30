package com.opencontinuity.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opencontinuity.ui.screens.*
import com.opencontinuity.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Screen definitions
// ─────────────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard",    "Dashboard",  Icons.Default.Dashboard)
    object Pairing      : Screen("pairing",      "Pairing",    Icons.Default.QrCode)
    object FileTransfer : Screen("file_transfer","Files",      Icons.Default.Folder)
    object Touchpad     : Screen("touchpad",     "Touchpad",   Icons.Default.TouchApp)
    object Camera       : Screen("camera",       "Camera",     Icons.Default.CameraAlt)
    object ScreenMirror : Screen("screen_mirror","Mirror",     Icons.Default.ScreenShare)
    object Sms          : Screen("sms",          "SMS",        Icons.Default.Sms)
    object NoteTaker    : Screen("note_taker",   "Notes",      Icons.Default.Edit)
    object Settings     : Screen("settings",     "Settings",   Icons.Default.Settings)
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Nav Tab data
// ─────────────────────────────────────────────────────────────────────────────

private data class NavTab(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val isCenterFab: Boolean = false
)

private val navTabs = listOf(
    NavTab("Home",    Icons.Outlined.Home,       Screen.Dashboard.route),
    NavTab("Devices", Icons.Outlined.Router,     Screen.Pairing.route),
    NavTab("Connect", Icons.Default.Add,          Screen.Pairing.route, isCenterFab = true),
    NavTab("Stats",   Icons.Outlined.BarChart,   Screen.FileTransfer.route),
    NavTab("Profile", Icons.Outlined.Person,     Screen.Settings.route)
)

// ─────────────────────────────────────────────────────────────────────────────
// Root Navigation
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenContinuityNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Which routes show the bottom nav
    val topLevelRoutes = setOf(
        Screen.Dashboard.route,
        Screen.Pairing.route,
        Screen.FileTransfer.route,
        Screen.Settings.route
    )
    val showBottomNav = currentRoute in topLevelRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Ambient background glow ──────────────────────────────────────────
        AmbientBackground()

        // ── Nav host ─────────────────────────────────────────────────────────
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomNav) 100.dp else 0.dp)
        ) {
            composable(Screen.Dashboard.route)    { DashboardScreen(navController) }
            composable(Screen.Pairing.route)      { PairingScreen(navController) }
            composable(Screen.FileTransfer.route) { FileTransferScreen(navController) }
            composable(Screen.Touchpad.route)     { TouchpadScreen(navController) }
            composable(Screen.Camera.route)       { CameraScreen(navController) }
            composable(Screen.ScreenMirror.route) { ScreenMirrorScreen(navController) }
            composable(Screen.Sms.route)          { SmsScreen(navController) }
            composable(Screen.NoteTaker.route)    { NoteTakerScreen(navController) }
            composable(Screen.Settings.route)     { SettingsScreen(navController) }
        }

        // ── Floating Pill Bottom Nav ─────────────────────────────────────────
        if (showBottomNav) {
            FloatingPillNavBar(
                navController = navController,
                currentRoute = currentRoute,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating Pill Nav Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FloatingPillNavBar(
    navController: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            // Warm glass fill — matches card style
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF3A1625),
                        1.0f to Color(0xFF1C0912)
                    )
                )
            )
            .drawWithContent {
                drawContent()
                val cr = 36.dp.toPx()
                // Top glass-rim highlight
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0x44FFFFFF), Color(0x22FFFFFF), Color.Transparent)
                    ),
                    size         = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(cr),
                    style        = Stroke(width = 1.5.dp.toPx())
                )
                // Pink glass border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x55FF78B7),
                            0.5f to Color(0x22FF5090),
                            1.0f to Color(0x33FF78B7)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cr),
                    style        = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navTabs.forEach { tab ->
                val isActive = when {
                    tab.isCenterFab -> false
                    tab.route == Screen.Dashboard.route  -> currentRoute == Screen.Dashboard.route
                    tab.route == Screen.Pairing.route    -> currentRoute == Screen.Pairing.route
                    tab.route == Screen.FileTransfer.route -> currentRoute == Screen.FileTransfer.route
                    tab.route == Screen.Settings.route   -> currentRoute == Screen.Settings.route
                    else -> currentRoute == tab.route
                }

                if (tab.isCenterFab) {
                    CenterFabTab(
                        tab = tab,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                } else {
                    NavTabItem(
                        tab = tab,
                        isActive = isActive,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTab,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) HotPink.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(300),
        label = "navTabBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) EtherealPrimary else EtherealOnSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "navTabIcon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isActive) EtherealPrimary else EtherealOnSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "navTabLabel"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .then(
                if (isActive) Modifier.border(
                    width = 1.dp,
                    color = EtherealPrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                ) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun CenterFabTab(
    tab: NavTab,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .offset(y = (-8).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            EtherealSurfaceContainerHighest,
                            EtherealSurfaceContainerHigh
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = EtherealPrimary.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = EtherealPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = tab.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = EtherealOnSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 0.3.sp,
            modifier = Modifier.offset(y = (-6).dp)
        )
    }
}
