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
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.core.discovery.DiscoveryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that maintains the connection and runs feature modules.
 */
class ConnectionService : Service() {

    companion object {
        private const val TAG = "ConnectionService"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_MS = 500L // Fast restart to prevent dropped UI sessions
        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        const val ACTION_START = "com.opencontinuity.action.START"
        const val ACTION_STOP = "com.opencontinuity.action.STOP"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var connectionManager: ConnectionManager
    private lateinit var discoveryManager: DiscoveryManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val connectionStarted = AtomicBoolean(false)

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
        val notification = createNotification("Starting…")

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

        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenContinuity:ConnectionWakeLock"
            ).also { it.acquire(WAKELOCK_TIMEOUT_MS) }
        }

        if (wifiLock?.isHeld != true) {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "OpenContinuity:WifiLock"
            ).also { it.acquire() }
        }

        startConnection()
    }

    private fun startConnection() {
        if (!connectionStarted.compareAndSet(false, true)) {
            Log.d(TAG, "Connection already started — skipping duplicate init")
            return
        }
        cleanedUp = false

        serviceScope.launch {
            val serverStarted = connectionManager.startServer()
            if (!serverStarted) {
                Log.e(TAG, "Failed to start server")
                connectionStarted.set(false)
                updateNotification("Failed to start server")
                scheduleRestart("server start failed")
                return@launch
            }

            discoveryManager.startAdvertising(
                port = ConnectionManager.DEFAULT_PORT,
                deviceName = Build.MODEL
            )
            discoveryManager.startBeacon(
                port = ConnectionManager.DEFAULT_PORT,
                deviceName = Build.MODEL
            )

            initializeFeatures()
            ensureWatchdog()

            connectionManager.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Disconnected -> updateNotification("Not connected")
                    is ConnectionState.Listening -> updateNotification("Waiting on port ${state.port}")
                    is ConnectionState.Connected -> updateNotification("Connected to ${state.deviceName}")
                    is ConnectionState.Error -> updateNotification("Error: ${state.message}")
                }
            }
        }
    }

    private fun ensureWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            var lastConnectedMs = System.currentTimeMillis()
            while (isActive) {
                delay(5_000)
                if (!connectionStarted.get()) continue

                // 1) If the server process/engine died, restart.
                if (!connectionManager.isServerRunning()) {
                    Log.w(TAG, "Server died in background — restarting stack")
                    connectionStarted.set(false)
                    restartConnectionStack()
                    lastConnectedMs = System.currentTimeMillis()
                    continue
                }

                // 2) If the server is alive but there are no active clients for a while,
                // the WebSocket connection likely got dropped while backgrounded.
                val connectedClientsEmpty = connectionManager.connectedClients.value.isEmpty()
                val elapsedSinceLastConnect = System.currentTimeMillis() - lastConnectedMs
                if (!connectedClientsEmpty) {
                    lastConnectedMs = System.currentTimeMillis()
                    continue
                }

                if (elapsedSinceLastConnect > 45_000L) {
                    Log.w(TAG, "No active clients for ${elapsedSinceLastConnect}ms — restarting stack")
                    connectionStarted.set(false)
                    restartConnectionStack()
                    lastConnectedMs = System.currentTimeMillis()
                }
            }
        }
    }


    private suspend fun restartConnectionStack() {
        try {
            stopFeatures()
            connectionManager.stopServer()
            discoveryManager.stopBeacon()
            discoveryManager.stopAdvertising()
            delay(1_000)
        } catch (e: Exception) {
            Log.w(TAG, "Error while resetting connection stack", e)
        }
        startConnection()
    }

    private fun initializeFeatures() {
        val app = application as OpenContinuityApp
        app.sessionManager.start()
        app.clipboardSyncManager.apply {
            localDeviceId = app.securityManager.deviceId
            start()
        }
        app.batteryMonitor.start()
        app.fileTransferManager.start()
        app.smsDataManager.start()
        app.touchpadManager.start()
        app.dragDropManager.start()
        app.screenshotSyncManager.start()
        app.noteSyncManager.start()
        Log.i(TAG, "Features initialized")
    }

    private fun stopFeatures() {
        val app = application as OpenContinuityApp
        try { app.clipboardSyncManager.stop() } catch (e: Exception) { Log.w(TAG, "clipboardStop", e) }
        try { app.batteryMonitor.stop() } catch (e: Exception) { Log.w(TAG, "batteryStop", e) }
        try { app.fileTransferManager.stop() } catch (e: Exception) { Log.w(TAG, "fileStop", e) }
        try { app.smsDataManager.stop() } catch (e: Exception) { Log.w(TAG, "smsStop", e) }
        try { app.touchpadManager.stop() } catch (e: Exception) { Log.w(TAG, "touchpadStop", e) }
        try { app.dragDropManager.stop() } catch (e: Exception) { Log.w(TAG, "dragStop", e) }
        try { app.screenshotSyncManager.stop() } catch (e: Exception) { Log.w(TAG, "screenshotStop", e) }
        try { app.noteSyncManager.stop() } catch (e: Exception) { Log.w(TAG, "noteStop", e) }
        try { app.sessionManager.stop() } catch (e: Exception) { Log.w(TAG, "sessionStop", e) }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ConnectionService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, OpenContinuityApp.CHANNEL_CONNECTION)
            .setContentTitle("OpenContinuity")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(contentText))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!explicitStopRequested) scheduleRestart("task removed")
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleRestart(reason: String) {
        val restartPi = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, ServiceRestartReceiver::class.java).apply { action = ACTION_START },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + RESTART_DELAY_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restartPi)
            Log.i(TAG, "Scheduled inexact restart ($reason)")
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restartPi)
            Log.i(TAG, "Scheduled exact restart in ${RESTART_DELAY_MS}ms ($reason)")
        }
    }

    private fun stopService() {
        Log.i(TAG, "Stopping service")
        if (cleanedUp) {
            stopSelf()
            return
        }
        cleanedUp = true
        connectionStarted.set(false)
        watchdogJob?.cancel()
        watchdogJob = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        stopFeatures()
        connectionManager.stopServer()
        discoveryManager.stopBeacon()
        discoveryManager.stopAdvertising()

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!cleanedUp) stopService()
        if (!explicitStopRequested) scheduleRestart("service destroyed")
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }
}
