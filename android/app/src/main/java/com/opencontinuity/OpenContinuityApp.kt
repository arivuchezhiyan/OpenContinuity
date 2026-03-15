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
import com.opencontinuity.features.sms.SmsDataManager
import com.opencontinuity.features.touchpad.TouchpadManager
import com.opencontinuity.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * OpenContinuity Application class
 * Initializes core managers and notification channels
 */
class OpenContinuityApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var securityManager: SecurityManager
        private set

    lateinit var connectionManager: ConnectionManager
        private set

    lateinit var discoveryManager: DiscoveryManager
        private set

    // Feature Managers (Lazy initialized)
    val clipboardSyncManager by lazy { ClipboardSyncManager(this, connectionManager) }
    val batteryMonitor by lazy { BatteryMonitor(this, connectionManager) }
    val fileTransferManager by lazy { FileTransferManager(this, connectionManager) }
    val smsDataManager by lazy { SmsDataManager(this, connectionManager) }
    val touchpadManager by lazy { TouchpadManager(this, connectionManager) }
    val dragDropManager by lazy { DragDropManager(this, connectionManager) }
    val sessionManager by lazy { SessionManager(connectionManager) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val processName = getProcessName(this)
        val isConnectionProcess = processName?.endsWith(":connection") == true
        val isMainProcess = processName == packageName

        Log.i("OpenContinuityApp", "Initializing process: $processName (isMain=$isMainProcess, isConnection=$isConnectionProcess)")

        // Initialize core managers
        securityManager = SecurityManager(this)
        connectionManager = ConnectionManager(this, securityManager)
        discoveryManager = DiscoveryManager(this)

        // Only the connection process should start the discovery beacon by default
        // if we were to auto-start it. But here we just init the objects.
        
        // Create notification channels (safe to do in both, but main is enough)
        if (isMainProcess || isConnectionProcess) {
            createNotificationChannels()
        }
    }

    private fun getProcessName(context: android.content.Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val am = context.getSystemService(Application.ACTIVITY_SERVICE) as android.app.ActivityManager
        val processes = am.runningAppProcesses
        if (processes != null) {
            for (process in processes) {
                if (process.pid == android.os.Process.myPid()) {
                    return process.processName
                }
            }
        }
        return null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Connection Service Channel
            val connectionChannel = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows connection status"
                setShowBadge(false)
            }

            // File Transfer Channel
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFER,
                "File Transfer",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "File transfer progress notifications"
            }

            // Notifications Sync Channel
            val notificationSyncChannel = NotificationChannel(
                CHANNEL_NOTIFICATION_SYNC,
                "Notification Sync",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Synced notifications from PC"
            }

            // Clipboard Sync Channel — high importance so banner appears even when screen is on
            val clipboardChannel = NotificationChannel(
                CHANNEL_CLIPBOARD,
                "Clipboard Sync",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows clipboard content synced from another device"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(connectionChannel, transferChannel, notificationSyncChannel, clipboardChannel)
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
