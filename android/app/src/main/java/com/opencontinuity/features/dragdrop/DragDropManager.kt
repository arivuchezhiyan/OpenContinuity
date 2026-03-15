package com.opencontinuity.features.dragdrop

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * DragDrop Manager (Android)
 *
 * Implements the Android side of cross-device drag-and-drop.
 *
 * Workflow (Laptop → Phone):
 *   1. Windows sends DRAG_FILE_START.
 *   2. We show a heads-up notification: "File drop from laptop — tap to accept".
 *   3. User taps → we send DRAG_FILE_DROP(accepted=true) → Windows starts the
 *      chunked file transfer via FileTransferManager.
 *
 * Workflow (Phone → Laptop):
 *   - Not yet implemented (phone UI drag-to-edge is a future milestone).
 */
class DragDropManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "DragDropManager"
        private const val NOTIF_ID_DRAG = 4001
        private const val ACTION_ACCEPT = "com.opencontinuity.dragdrop.ACCEPT"
        private const val ACTION_REJECT = "com.opencontinuity.dragdrop.REJECT"
        private const val EXTRA_DRAG_ID = "drag_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Drags awaiting user decision, keyed by dragId */
    private val pendingDrags = mutableMapOf<String, DragFileStartPayload>()

    fun start() {
        connectionManager.registerHandler(MessageType.DRAG_FILE_START) { _, message ->
            scope.launch {
                try {
                    val payload =
                        protocolJson.decodeFromJsonElement<DragFileStartPayload>(message.payload)
                    handleIncomingDrag(payload)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse DRAG_FILE_START", e)
                }
            }
        }

        connectionManager.registerHandler(MessageType.DRAG_FILE_CANCEL) { _, message ->
            scope.launch {
                val dragId = runCatching {
                    message.payload.toString().removePrefix("{\"dragId\":\"").removeSuffix("\"}")
                }.getOrNull() ?: return@launch
                cancelDrag(dragId)
            }
        }

        Log.i(TAG, "DragDrop manager started")
    }

    fun stop() {
        scope.cancel()
        notificationManager.cancel(NOTIF_ID_DRAG)
        pendingDrags.clear()
    }

    // ─────────────────────────── incoming ─────────────────────────────────

    private fun handleIncomingDrag(payload: DragFileStartPayload) {
        Log.i(TAG, "Incoming drag from Windows: ${payload.fileName} (${payload.fileSize} bytes)")
        pendingDrags[payload.dragId] = payload
        showDragNotification(payload)
    }

    private fun showDragNotification(payload: DragFileStartPayload) {
        val acceptIntent = Intent(ACTION_ACCEPT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_DRAG_ID, payload.dragId)
        }
        val rejectIntent = Intent(ACTION_REJECT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_DRAG_ID, payload.dragId)
        }

        val acceptPi = PendingIntent.getBroadcast(
            context, payload.dragId.hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectPi = PendingIntent.getBroadcast(
            context, payload.dragId.hashCode() + 1,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeLabel = formatSize(payload.fileSize)
        val notif = NotificationCompat.Builder(context, OpenContinuityApp.CHANNEL_CLIPBOARD)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("File from laptop")
            .setContentText("${payload.fileName}  •  $sizeLabel")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${payload.fileName}\n$sizeLabel\nTap Accept to save on this device.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_save, "Accept", acceptPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPi)
            .build()

        notificationManager.notify(NOTIF_ID_DRAG, notif)
    }

    /** Called by BroadcastReceiver (registered in ConnectionService) when user taps Accept */
    fun acceptDrag(dragId: String) {
        val drag = pendingDrags[dragId] ?: return
        notificationManager.cancel(NOTIF_ID_DRAG)
        pendingDrags.remove(dragId)

        scope.launch {
            sendDropAck(dragId, accepted = true)
        }
        Log.i(TAG, "User accepted drag: ${drag.fileName}")
    }

    /** Called by BroadcastReceiver when user taps Reject */
    fun rejectDrag(dragId: String) {
        pendingDrags.remove(dragId)
        notificationManager.cancel(NOTIF_ID_DRAG)

        scope.launch {
            sendDropAck(dragId, accepted = false)
        }
        Log.i(TAG, "User rejected drag")
    }

    private fun cancelDrag(dragId: String) {
        if (pendingDrags.remove(dragId) != null) {
            notificationManager.cancel(NOTIF_ID_DRAG)
            Log.i(TAG, "Drag cancelled by Windows: $dragId")
        }
    }

    private suspend fun sendDropAck(dragId: String, accepted: Boolean) {
        val ack = DragFileDropPayload(
            dragId = dragId,
            targetDeviceId = "", // Filled by ConnectionManager's device ID
            accepted = accepted
        )
        val message = ProtocolMessage(
            type = MessageType.DRAG_FILE_DROP,
            payload = protocolJson.encodeToJsonElement(ack)
        )
        connectionManager.broadcast(message)
    }

    // ──────────────────────────── utils ───────────────────────────────────

    private fun formatSize(bytes: Long): String = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "%.2f GB".format(bytes / 1_073_741_824.0)
    }
}
