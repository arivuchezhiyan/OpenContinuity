package com.opencontinuity.features.screenmirror

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencontinuity.OpenContinuityApp

/**
 * Foreground service for screen mirroring.
 * Holds the foreground service type=mediaProjection as required by Android 14+
 */
class ScreenMirrorService : Service() {

    companion object {
        private const val TAG = "ScreenMirrorService"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.opencontinuity.screenmirror.START"
        const val ACTION_STOP = "com.opencontinuity.screenmirror.STOP"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        var isActive = false
            private set
    }

    private var screenMirrorManager: ScreenMirrorManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Screen mirror service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMirroring()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != -1 && resultData != null) {
                    startForegroundMirroring(resultCode, resultData)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundMirroring(resultCode: Int, resultData: Intent) {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        screenMirrorManager = ScreenMirrorManager(this)
        screenMirrorManager?.startMirroring(resultCode, resultData)
        isActive = true

        Log.i(TAG, "Screen mirror service started")
    }

    private fun stopMirroring() {
        screenMirrorManager?.stopMirroring()
        screenMirrorManager = null
        isActive = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Screen mirror service stopped")
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, OpenContinuityApp.CHANNEL_CONNECTION)
            .setContentTitle("OpenContinuity")
            .setContentText("Screen mirroring active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMirroring()
    }
}
