package com.opencontinuity.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.ClipboardContentType
import com.opencontinuity.core.protocol.ClipboardSyncPayload
import com.opencontinuity.core.protocol.MessageType
import com.opencontinuity.core.protocol.ProtocolMessage
import com.opencontinuity.core.protocol.protocolJson
import com.opencontinuity.features.clipboard.ClipboardCaptureActivity
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement
import com.opencontinuity.services.ConnectionService
import com.opencontinuity.ui.navigation.OpenContinuityNavigation
import com.opencontinuity.ui.theme.OpenContinuityTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Log.w("MainActivity", "Not all permissions granted: $permissions")
        }
        // Always attempt to start the service so the basic connection can run
        startConnectionService()
    }

    override fun onResume() {
        super.onResume()
        // Strategy C: apply clipboard content that arrived from the laptop while the phone
        // was backgrounded and Strategy A (activity launch) or Strategy B (direct set) could
        // not write the clipboard because the OS/OEM blocked it.  An Activity in onResume()
        // is always in a foreground window — setPrimaryClip() succeeds on every Android OEM
        // including Infinix XOS, MIUI, Samsung One UI, etc.
        val pendingText = ClipboardCaptureActivity.pendingReceiveText
        val pendingHtml = ClipboardCaptureActivity.pendingReceiveHtml
        if (pendingText != null || pendingHtml != null) {
            try {
                val clipMgr = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = if (pendingHtml != null && pendingText != null) {
                    ClipData.newHtmlText("OpenContinuity", pendingText, pendingHtml)
                } else {
                    ClipData.newPlainText("OpenContinuity", pendingText ?: "")
                }
                clipMgr.setPrimaryClip(clip)
                Log.d("MainActivity", "Applied pending clipboard from laptop (Strategy C)")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to apply pending clipboard", e)
            } finally {
                ClipboardCaptureActivity.pendingReceiveText = null
                ClipboardCaptureActivity.pendingReceiveHtml = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenContinuityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OpenContinuityNavigation()
                }
            }
        }

        handleIntent(intent)
        requestPermissions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    handleSendText(intent)
                } else {
                    handleSendFile(intent)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultipleFiles(intent)
            }
        }
    }

    private fun handleSendText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            // In a real implementation, we could send this as a text file or to clipboard
            // For now, let's treat it as a clipboard sync
            Log.d("MainActivity", "Received shared text: $text")
            val app = application as OpenContinuityApp
            val message = ProtocolMessage(
                type = MessageType.CLIPBOARD_SYNC,
                payload = protocolJson.encodeToJsonElement(
                    ClipboardSyncPayload(
                        textContent = text,
                        contentType = ClipboardContentType.TEXT
                    )
                )
            )
            app.applicationScope.launch {
                app.connectionManager.broadcast(message)
            }
        }
    }

    private fun handleSendFile(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
            val app = application as OpenContinuityApp
            app.applicationScope.launch {
                app.fileTransferManager.sendFile(uri)
            }
        }
    }

    private fun handleSendMultipleFiles(intent: Intent) {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
            val app = application as OpenContinuityApp
            app.applicationScope.launch {
                uris.forEach { uri ->
                    app.fileTransferManager.sendFile(uri)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startConnectionService() {
        val intent = Intent(this, ConnectionService::class.java).apply {
            action = ConnectionService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    fun openNotificationListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
