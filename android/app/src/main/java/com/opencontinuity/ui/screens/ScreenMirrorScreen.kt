package com.opencontinuity.ui.screens

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.features.screenmirror.ScreenMirrorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMirrorScreen(navController: NavController) {
    val context = LocalContext.current
    var isMirroring by remember { mutableStateOf(ScreenMirrorService.isActive) }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ScreenMirrorService.ACTION_START
                putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenMirrorService.EXTRA_RESULT_DATA, result.data)
            }
            context.startForegroundService(intent)
            isMirroring = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Mirroring") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isMirroring) Icons.Default.ScreenShare else Icons.Default.StopScreenShare,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = if (isMirroring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (isMirroring) "Screen Mirroring is Active" else "Ready to Mirror",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isMirroring) "Your screen is being streamed to your PC" else "Mirror your phone screen to your PC",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        if (isMirroring) {
                            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                                action = ScreenMirrorService.ACTION_STOP
                            }
                            context.startService(intent)
                            isMirroring = false
                        } else {
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = if (isMirroring) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isMirroring) "Stop Mirroring" else "Start Mirroring")
                }
            }
        }
    }
}
