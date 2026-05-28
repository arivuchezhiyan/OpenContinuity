package com.opencontinuity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.opencontinuity.core.security.SecurityManager
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.discovery.DiscoveryManager
import com.opencontinuity.features.battery.BatteryMonitor
import com.opencontinuity.features.clipboard.ClipboardSyncManager
import com.opencontinuity.features.dragdrop.DragDropManager
import com.opencontinuity.features.filetransfer.FileTransferManager
import com.opencontinuity.features.screenshot.ScreenshotSyncManager
import com.opencontinuity.features.notes.NoteSyncManager
import com.opencontinuity.features.sms.SmsDataManager
import com.opencontinuity.features.touchpad.TouchpadManager
import com.opencontinuity.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OpenContinuityApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var securityManager: SecurityManager
        private set

    lateinit var connectionManager: ConnectionManager
        private set

    lateinit var discoveryManager: DiscoveryManager
        private set

    val clipboardSyncManager by lazy { ClipboardSyncManager(this, connectionManager) }
    val batteryMonitor by lazy { BatteryMonitor(this, connectionManager) }
    val fileTransferManager by lazy { FileTransferManager(this, connectionManager) }
    val smsDataManager by lazy { SmsDataManager(this, connectionManager) }
    val touchpadManager by lazy { TouchpadManager(this, connectionManager) }
    val dragDropManager by lazy { DragDropManager(this, connectionManager) }
    val screenshotSyncManager by lazy { ScreenshotSyncManager(this, connectionManager) }
    val noteSyncManager by lazy { NoteSyncManager(connectionManager) }
    val sessionManager by lazy { SessionManager(connectionManager) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val processName = getProcessName(this)
        val isMainProcess = processName == packageName
        val isConnectionProcess = processName?.endsWith(":connection") == true

        Log.i("OpenContinuityApp", "Process: $processName (main=$isMainProcess)")

        securityManager = SecurityManager(this)
        connectionManager = ConnectionManager(this, securityManager)
        discoveryManager = DiscoveryManager(this)

        if (isMainProcess || isConnectionProcess) {
            createNotificationChannels()
        }
    }

    private fun getProcessName(context: android.content.Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val am = context.getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses
            ?.firstOrNull { it.pid == android.os.Process.myPid() }
            ?.processName
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_CONNECTION,
                        "Connection Service",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows connection status"
                        setShowBadge(false)
                    },
                    NotificationChannel(
                        CHANNEL_TRANSFER,
                        "File Transfer",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "File transfer progress" },
                    NotificationChannel(
                        CHANNEL_NOTIFICATION_SYNC,
                        "Notification Sync",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Synced notifications from PC" },
                    NotificationChannel(
                        CHANNEL_CLIPBOARD,
                        "Clipboard Sync",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Clipboard content synced from another device"
                        setShowBadge(false)
                    }
                )
            )
        }
    }

    companion object {
        const val CHANNEL_CONNECTION = "connection_service"
        const val CHANNEL_TRANSFER = "file_transfer"
        const val CHANNEL_NOTIFICATION_SYNC = "notification_sync"
        const val CHANNEL_CLIPBOARD = "clipboard_sync"

        lateinit var instance: OpenContinuityApp
            private set
    }
}
