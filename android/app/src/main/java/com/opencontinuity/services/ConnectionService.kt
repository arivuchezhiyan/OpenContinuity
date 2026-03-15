package com.opencontinuity.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.R
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.core.discovery.DiscoveryManager
import com.opencontinuity.core.session.SessionManager
import com.opencontinuity.features.battery.BatteryMonitor
import com.opencontinuity.features.clipboard.ClipboardSyncManager
import com.opencontinuity.features.dragdrop.DragDropManager
import com.opencontinuity.features.filetransfer.FileTransferManager
import com.opencontinuity.features.sms.SmsDataManager
import com.opencontinuity.features.touchpad.TouchpadManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service that maintains the connection and runs feature modules
 */
class ConnectionService : Service() {

    companion object {
        private const val TAG = "ConnectionService"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_MS = 1500L  // was 5000ms — reconnect faster after a drop

        const val ACTION_START = "com.opencontinuity.action.START"
        const val ACTION_STOP = "com.opencontinuity.action.STOP"
    }

    private val binder = LocalBinder()
    // IO dispatcher keeps the server coroutines alive even when Android throttles the Main thread in background
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var connectionManager: ConnectionManager
    private lateinit var discoveryManager: DiscoveryManager

    // WakeLock keeps the CPU active so the Ktor server doesn't die when the screen turns off
    private var wakeLock: PowerManager.WakeLock? = null

    // WifiLock prevents the Wi-Fi radio from entering power-save mode while we are a
    // hotspot gateway or a client on someone else's hotspot. Without this, Android
    // throttles the Wi-Fi radio when another app is in the foreground, causing packet
    // loss that breaks the WebSocket connection.
    private var wifiLock: WifiManager.WifiLock? = null

    // Feature managers
    private var clipboardSyncManager: ClipboardSyncManager? = null
    private var batteryMonitor: BatteryMonitor? = null
    private var fileTransferManager: FileTransferManager? = null
    private var smsDataManager: SmsDataManager? = null
    private var touchpadManager: TouchpadManager? = null
    private var dragDropManager: DragDropManager? = null
    private var sessionManager: SessionManager? = null

    // Guard against double initialization.
    // onTaskRemoved fires when the user swipes the app away and sends a new ACTION_START
    // to the already-running foreground service.  Without this flag, startConnection()
    // would run twice, creating duplicate ClipboardSyncManagers, duplicate poll loops,
    // and replacing the registered message handler with a new instance that has stale
    // lastClipboardHash = "" — all of which cause clipboard sync to misbehave.
    @Volatile private var connectionStarted = false
    @Volatile private var explicitStopRequested = false
    @Volatile private var cleanedUp = false
    private var watchdogJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        val app = application as OpenContinuityApp
        connectionManager = app.connectionManager
        discoveryManager = app.discoveryManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                explicitStopRequested = true
                stopService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                explicitStopRequested = false
                startForegroundService()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startForegroundService() {
        val notification = createNotification("Starting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Acquire WakeLock so CPU stays active and Ktor server survives app switching
        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenContinuity:ConnectionWakeLock"
            ).also { it.acquire() }
        }

        // Acquire WifiLock so Wi-Fi radio stays at full performance even when the
        // phone is using another app in the foreground. WIFI_MODE_FULL_HIGH_PERF
        // prevents the radio from throttling multicast/background traffic.
        if (wifiLock?.isHeld != true) {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            // WIFI_MODE_FULL_LOW_LATENCY: disables radio power-saving and minimises
            // packet-queue latency. Essential for interactive features (mouse, clipboard).
            // Automatically degrades to HIGH_PERF when screen is off or app is in background,
            // so battery is only consumed during actual interactive use.
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "OpenContinuity:WifiLock"
            ).also { it.acquire() }
        }

        // Start servers and discovery
        startConnection()
    }

    private fun startConnection() {
        if (connectionStarted) {
            Log.d(TAG, "Connection already started — skipping duplicate init")
            return
        }
        cleanedUp = false
        connectionStarted = true
        serviceScope.launch {
            // Start WebSocket server
            val serverStarted = connectionManager.startServer()
            if (!serverStarted) {
                Log.e(TAG, "Failed to start server")
                connectionStarted = false
                updateNotification("Failed to start server")
                scheduleRestart("server start failed")
                return@launch
            }

            // Start mDNS advertising
            discoveryManager.startAdvertising(
                port = ConnectionManager.DEFAULT_PORT,
                deviceName = Build.MODEL
            )

            // Start UDP broadcast beacon so the Windows app can find us on ANY network:
            // same-router WiFi, phone hotspot, laptop hotspot, USB tethering, etc.
            discoveryManager.startBeacon(
                port = ConnectionManager.DEFAULT_PORT,
                deviceName = Build.MODEL
            )

            // Initialize feature managers
            initializeFeatures()
            ensureWatchdog()

            // Monitor connection state
            connectionManager.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Disconnected -> {
                        updateNotification("Not connected")
                    }
                    is ConnectionState.Listening -> {
                        updateNotification("Waiting for connection on port ${state.port}")
                    }
                    is ConnectionState.Connected -> {
                        updateNotification("Connected to ${state.deviceName}")
                    }
                    is ConnectionState.Error -> {
                        updateNotification("Error: ${state.message}")
                    }
                }
            }
        }
    }

    private fun ensureWatchdog() {
        if (watchdogJob?.isActive == true) return

        watchdogJob = serviceScope.launch {
            while (isActive) {
                // Check every 5 s (was 15 s) — detects and restarts a dead server 3× faster
                delay(5000)

                if (!connectionStarted) continue
                if (connectionManager.isServerRunning()) continue

                Log.w(TAG, "Connection server stopped in background — restarting service stack")
                connectionStarted = false
                restartConnectionStack()
            }
        }
    }

    private suspend fun restartConnectionStack() {
        try {
            connectionManager.stopServer()
            discoveryManager.stopBeacon()
            discoveryManager.stopAdvertising()
            delay(1000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed while resetting connection stack", e)
        }

        startConnection()
    }

    private fun initializeFeatures() {
        val app = application as OpenContinuityApp

        // Session Manager
        app.sessionManager.start()

        // Initialize Clipboard Sync — inject local deviceId for echo prevention
        app.clipboardSyncManager.apply {
            localDeviceId = app.securityManager.deviceId
            start()
        }

        // Initialize Battery Monitor
        app.batteryMonitor.start()

        // Initialize File Transfer Manager
        app.fileTransferManager.start()

        // Initialize SMS Data Manager
        app.smsDataManager.start()

        // Initialize Touchpad Manager
        app.touchpadManager.start()

        // Initialize DragDrop Manager
        app.dragDropManager.start()

        Log.i(TAG, "Features initialized")
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ConnectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OpenContinuityApp.CHANNEL_CONNECTION)
            .setContentTitle("OpenContinuity")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped app away — restart the service so the server keeps running
        if (!explicitStopRequested) {
            scheduleRestart("task removed")
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleRestart(reason: String) {
        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = ACTION_START
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + RESTART_DELAY_MS

        // setExactAndAllowWhileIdle fires at the exact requested time even in Doze mode.
        // setAndAllowWhileIdle (previous) is throttled to once per minute on Android 12+ / XOS,
        // which silently delayed our 1.5 s restart by up to 58 s.
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            restartPendingIntent
        )
        Log.i(TAG, "Scheduled service restart in ${RESTART_DELAY_MS}ms ($reason)")
    }

    private fun stopService() {
        Log.i(TAG, "Stopping service")
        if (cleanedUp) {
            stopSelf()
            return
        }
        cleanedUp = true
        connectionStarted = false
        watchdogJob?.cancel()
        watchdogJob = null

        // Release WakeLock
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        // Release WifiLock
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        // Stop features
        clipboardSyncManager?.stop()
        batteryMonitor?.stop()
        fileTransferManager?.stop()
        smsDataManager?.stop()
        touchpadManager?.stop()
        dragDropManager?.stop()
        sessionManager?.stop()

        // Stop connection
        connectionManager.stopServer()
        discoveryManager.stopBeacon()
        discoveryManager.stopAdvertising()

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!cleanedUp) {
            stopService()
        }
        if (!explicitStopRequested) {
            scheduleRestart("service destroyed")
        }
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }
}
