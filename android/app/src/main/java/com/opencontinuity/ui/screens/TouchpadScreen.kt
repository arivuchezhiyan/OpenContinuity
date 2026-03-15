package com.opencontinuity.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.features.touchpad.TouchpadManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TouchpadScreen(navController: NavController) {
    val context = LocalContext.current
    val connectionManager = OpenContinuityApp.instance.connectionManager

    val touchpadManager = remember {
        TouchpadManager(context, connectionManager)
    }

    LaunchedEffect(Unit) {
        touchpadManager.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            touchpadManager.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Touchpad") },
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
        ) {
            // Instructions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Touch Gestures",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Single finger: Move cursor", style = MaterialTheme.typography.bodySmall)
                    Text("• Tap: Left click", style = MaterialTheme.typography.bodySmall)
                    Text("• Long press: Right click", style = MaterialTheme.typography.bodySmall)
                    Text("• Two finger tap: Right click", style = MaterialTheme.typography.bodySmall)
                    Text("• Two finger swipe: Scroll", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Touchpad Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.shapes.large
                    )
                    .pointerInteropFilter { event ->
                        touchpadManager.processTouchEvent(event)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Touch here to control cursor",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Mouse buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        touchpadManager.processTouchEvent(
                            MotionEvent.obtain(
                                System.currentTimeMillis(),
                                System.currentTimeMillis(),
                                MotionEvent.ACTION_DOWN,
                                0f, 0f, 0
                            )
                        )
                        touchpadManager.processTouchEvent(
                            MotionEvent.obtain(
                                System.currentTimeMillis(),
                                System.currentTimeMillis() + 50,
                                MotionEvent.ACTION_UP,
                                0f, 0f, 0
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Text("Left Click")
                }

                Button(
                    onClick = {
                        touchpadManager.processTouchEvent(
                            MotionEvent.obtain(
                                System.currentTimeMillis(),
                                System.currentTimeMillis(),
                                MotionEvent.ACTION_DOWN,
                                0f, 0f, 0
                            )
                        )
                        Thread.sleep(600)
                        touchpadManager.processTouchEvent(
                            MotionEvent.obtain(
                                System.currentTimeMillis(),
                                System.currentTimeMillis() + 600,
                                MotionEvent.ACTION_UP,
                                0f, 0f, 0
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Right Click")
                }
            }
        }
    }
}
