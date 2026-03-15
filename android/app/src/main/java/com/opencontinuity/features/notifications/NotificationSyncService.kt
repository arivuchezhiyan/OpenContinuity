package com.opencontinuity.features.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.encodeToJsonElement
import java.io.ByteArrayOutputStream

/**
 * Notification Listener Service - captures and forwards notifications to PC
 */
class NotificationSyncService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationSyncService"
        private const val MAX_ICON_SIZE = 96
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionManager: ConnectionManager? = null

    // Packages to exclude from sync
    private val excludedPackages = setOf(
        "com.opencontinuity", // Self
        "com.android.systemui",
        "com.android.providers.downloads"
    )

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Notification listener service created")

        val app = application as? OpenContinuityApp
        connectionManager = app?.connectionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldSyncNotification(sbn)) return

        scope.launch {
            try {
                val payload = extractNotificationPayload(sbn)
                if (payload != null) {
                    val message = ProtocolMessage(
                        type = MessageType.NOTIFICATION_POST,
                        payload = protocolJson.encodeToJsonElement(payload)
                    )

                    connectionManager?.broadcast(message)
                    Log.d(TAG, "Notification sent: ${payload.appName} - ${payload.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scope.launch {
            try {
                val dismissPayload = mapOf(
                    "notificationId" to sbn.key,
                    "packageName" to sbn.packageName
                )

                val message = ProtocolMessage(
                    type = MessageType.NOTIFICATION_DISMISS,
                    payload = protocolJson.encodeToJsonElement(dismissPayload)
                )

                connectionManager?.broadcast(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification dismiss", e)
            }
        }
    }

    private fun shouldSyncNotification(sbn: StatusBarNotification): Boolean {
        // Exclude certain packages
        if (sbn.packageName in excludedPackages) return false

        // Exclude ongoing notifications (like music players)
        if (sbn.isOngoing) return false

        // Exclude low-priority notifications
        val notification = sbn.notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notification.channelId == "foreground_service") return false
        }

        return true
    }

    private fun extractNotificationPayload(sbn: StatusBarNotification): NotificationPayload? {
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Skip empty notifications
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val appName = getAppName(sbn.packageName)

        // Extract icon
        val iconBase64 = extractIcon(sbn)

        // Extract actions
        val actions = notification.actions?.mapIndexed { index, action ->
            NotificationAction(
                actionId = "$index",
                title = action.title?.toString() ?: "Action $index"
            )
        } ?: emptyList()

        return NotificationPayload(
            notificationId = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            iconBase64 = iconBase64,
            timestamp = sbn.postTime,
            actions = actions
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun extractIcon(sbn: StatusBarNotification): String? {
        return try {
            val notification = sbn.notification

            // Try to get the small icon
            val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notification.smallIcon?.loadDrawable(this)
            } else {
                null
            }

            // Fallback to app icon
            val drawable = icon ?: packageManager.getApplicationIcon(sbn.packageName)

            val bitmap = drawableToBitmap(drawable)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MAX_ICON_SIZE, MAX_ICON_SIZE, true)

            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract notification icon", e)
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.i(TAG, "Notification listener service destroyed")
    }
}
