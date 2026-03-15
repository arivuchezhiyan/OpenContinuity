package com.opencontinuity.features.camera

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.R

/**
 * Foreground service for camera streaming.
 * Holds the foreground service type=camera as required by Android 14+
 */
class CameraStreamService : Service() {

    companion object {
        private const val TAG = "CameraStreamService"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.opencontinuity.camera.START"
        const val ACTION_STOP = "com.opencontinuity.camera.STOP"
    }

    private var cameraStreamManager: CameraStreamManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Camera stream service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val app = application as OpenContinuityApp
        cameraStreamManager = CameraStreamManager(this)

        Log.i(TAG, "Camera stream service started")
    }

    private fun stopStreaming() {
        cameraStreamManager?.stopStream()
        cameraStreamManager = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Camera stream service stopped")
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, OpenContinuityApp.CHANNEL_CONNECTION)
            .setContentTitle("OpenContinuity")
            .setContentText("Camera streaming active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}
