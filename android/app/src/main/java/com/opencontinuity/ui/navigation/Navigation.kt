package com.opencontinuity.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opencontinuity.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Pairing : Screen("pairing", "Pairing", Icons.Default.QrCode)
    object FileTransfer : Screen("file_transfer", "Files", Icons.Default.Folder)
    object Touchpad : Screen("touchpad", "Touchpad", Icons.Default.TouchApp)
    object Camera : Screen("camera", "Camera", Icons.Default.CameraAlt)
    object ScreenMirror : Screen("screen_mirror", "Mirror", Icons.Default.ScreenShare)
    object Sms : Screen("sms", "SMS", Icons.Default.Sms)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenContinuityNavigation() {
    val navController = rememberNavController()

    val screens = listOf(
        Screen.Dashboard,
        Screen.Pairing,
        Screen.FileTransfer,
        Screen.Touchpad,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Pairing.route) { PairingScreen(navController) }
            composable(Screen.FileTransfer.route) { FileTransferScreen(navController) }
            composable(Screen.Touchpad.route) { TouchpadScreen(navController) }
            composable(Screen.Camera.route) { CameraScreen(navController) }
            composable(Screen.ScreenMirror.route) { ScreenMirrorScreen(navController) }
            composable(Screen.Sms.route) { SmsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
        }
    }
}
