package com.opencontinuity.features.clipboard

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Clipboard Sync Manager - synchronizes clipboard between Android and Windows
 *
 * Background clipboard reading (Android 10+):
 *   getPrimaryClip() throws SecurityException in background → we show a "tap to share"
 *   notification. When the user taps it, a BroadcastReceiver reads and sends the clipboard
 *   while the app briefly has a window of clipboard access granted by the notification action.
 *
 * Background clipboard writing (Android all versions):
 *   setPrimaryClip() works from background. We also post a heads-up notification so the
 *   user sees the freshly arrived content without having to open the app.
 */
class ClipboardSyncManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "ClipboardSyncManager"
        private const val DEBOUNCE_MS = 300L
        private const val MAX_IMAGE_SIZE = 1024 * 1024 // 1MB
        private const val NOTIF_ID_RECEIVED = 3001
        const val NOTIF_ID_PENDING  = 3002          // used by ClipboardCaptureActivity
        private const val PENDING_NOTIF_COOLDOWN_MS = 4000L // don't re-show within 4s

        /**
         * Set to true by the clipboard-changed listener so the poll loop knows a copy
         * happened while we were backgrounded (can't read clipboard, but can show notif).
         * Reset to false by ClipboardCaptureActivity after the clipboard is sent.
         */
        @Volatile var clipboardMayHaveChanged = false
    }

    /** Local device ID for echo prevention — set by ConnectionService before start() */
    var localDeviceId: String = ""

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var scope: CoroutineScope? = null

    private var lastClipboardHash: String = ""
    private var debounceJob: Job? = null
    private var lastPendingNotifMs = 0L  // rate-limit the "share to laptop" notification

    // When clipboard changes and we are backgrounded, the listener sets the flag.
    // The poll loop then shows the notification exactly once (not every second).
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardMayHaveChanged = true  // mark — may not be able to read yet
        onLocalClipboardChanged()       // also attempt immediate send (works in foreground)
    }

    fun start() {
        // Use Dispatchers.IO — never throttled in background by Android OEM battery savers.
        // Dispatchers.Main CAN be throttled when the app has no visible Activity, which would
        // silently swallow incoming clipboard messages (they'd enqueue and never execute).
        // All clipboard operations (setPrimaryClip, primaryClip, NotificationManager.notify)
        // are Binder IPC calls and are thread-safe from any thread — no Main required.
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Listen for local clipboard changes (foreground fast-path).
        // The listener system-callback is always delivered on the Main thread (via ClipboardManager's
        // internal Handler), so registration from any thread is safe.
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        // Poll every 1.5 s — OnPrimaryClipChangedListener does NOT fire in background on
        // Android 10+.  processAndSendClipboard() is a no-op when nothing changed (hash guard).
        // When SecurityException occurs we only show the notification when the listener has
        // marked a change AND the cooldown has elapsed.
        scope?.launch {
            while (true) {
                delay(1500)
                processAndSendClipboard()
            }
        }

        // When a new client connects, immediately push the current clipboard to them
        scope?.launch {
            connectionManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    delay(500) // wait for handshake to fully settle
                    processAndSendClipboard()
                    // Clear any stale pending-send notification
                    notificationManager.cancel(NOTIF_ID_PENDING)
                }
            }
        }

        // Register handler for incoming clipboard sync
        connectionManager.registerHandler(MessageType.CLIPBOARD_SYNC) { _, message ->
            scope?.launch { handleIncomingClipboard(message) }
        }

        Log.i(TAG, "Clipboard sync started")
    }

    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        notificationManager.cancel(NOTIF_ID_RECEIVED)
        notificationManager.cancel(NOTIF_ID_PENDING)
        scope?.cancel()
        scope = null
        Log.i(TAG, "Clipboard sync stopped")
    }

    private fun onLocalClipboardChanged() {
        // Foreground fast-path: immediate send on listener callback
        debounceJob?.cancel()
        debounceJob = scope?.launch {
            delay(DEBOUNCE_MS)
            processAndSendClipboard()
        }
    }

    /**
     * Try to read and send the clipboard.
     * On Android 10+ in background, getPrimaryClip() throws SecurityException.
     * In that case, show a notification with a one-tap "Share with laptop" action.
     * [fromNotification] = true means we were called from the notification receiver
     * that carries a short foreground window allowing clipboard access.
     */
    private suspend fun processAndSendClipboard() {
        try {
            // Android 10+ throws SecurityException when a background app reads the clipboard
            // if it is not the IME or does not have accessibility focus.  Guard explicitly so
            // the exception never escapes into the calling coroutine and crashes the poll loop.
            val clip = try {
                clipboardManager.primaryClip
            } catch (se: SecurityException) {
                // Android 10+: can't read clipboard from background.
                // Only show the notification when the listener has flagged a change,
                // and only once per PENDING_NOTIF_COOLDOWN_MS to avoid spamming.
                val now = System.currentTimeMillis()
                if (clipboardMayHaveChanged && (now - lastPendingNotifMs) > PENDING_NOTIF_COOLDOWN_MS) {
                    lastPendingNotifMs = now
                    showPendingSendNotification()
                }
                Log.d(TAG, "Clipboard read blocked by OS (background restriction)")
                return
            } ?: return
            if (clip.itemCount == 0) return

            // Successfully read clipboard — clear the pending-change flag
            clipboardMayHaveChanged = false

            val item = clip.getItemAt(0)
            val payload = extractClipboardContent(item, clip.description.label?.toString())

            if (payload != null) {
                // Check for duplicates
                val hash = calculateHash(payload)
                if (hash == lastClipboardHash) {
                    return
                }
                lastClipboardHash = hash

                // Tag outgoing message with local deviceId so other devices
                // (and we ourselves on reconnect) can detect and suppress echoes
                val taggedPayload = payload.copy(
                    deviceId = localDeviceId,
                    contentHash = hash
                )

                val message = ProtocolMessage(
                    type = MessageType.CLIPBOARD_SYNC,
                    payload = protocolJson.encodeToJsonElement(taggedPayload)
                )

                connectionManager.broadcast(message)
                Log.d(TAG, "Clipboard sent: ${payload.contentType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process clipboard", e)
        }
    }

    private fun extractClipboardContent(item: ClipData.Item, label: String?): ClipboardSyncPayload? {
        // Try text content
        item.text?.let { text ->
            return ClipboardSyncPayload(
                contentType = ClipboardContentType.TEXT,
                textContent = text.toString()
            )
        }

        // Try HTML content
        item.htmlText?.let { html ->
            return ClipboardSyncPayload(
                contentType = ClipboardContentType.HTML,
                htmlContent = html,
                textContent = item.text?.toString()
            )
        }

        // Try URI (could be an image)
        item.uri?.let { uri ->
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap != null) {
                val base64 = encodeBitmapToBase64(bitmap)
                if (base64 != null) {
                    return ClipboardSyncPayload(
                        contentType = ClipboardContentType.IMAGE,
                        imageBase64 = base64,
                        imageMimeType = "image/png"
                    )
                }
            }
        }

        return null
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val stream = ByteArrayOutputStream()

            // Scale down if too large
            val scaledBitmap = if (bitmap.byteCount > MAX_IMAGE_SIZE) {
                val scale = Math.sqrt(MAX_IMAGE_SIZE.toDouble() / bitmap.byteCount)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode bitmap", e)
            null
        }
    }

    private suspend fun handleIncomingClipboard(message: ProtocolMessage) {
        try {
            val payload = protocolJson.decodeFromJsonElement<ClipboardSyncPayload>(message.payload)

            // Echo prevention: discard messages we sent ourselves
            if (!payload.deviceId.isNullOrEmpty() && payload.deviceId == localDeviceId) {
                Log.d(TAG, "Clipboard echo suppressed (same deviceId)")
                return
            }

            // Set the hash BEFORE updating the clipboard so the change listener and
            // the poll loop both see a matching hash and skip the echo send.
            lastClipboardHash = calculateHash(payload)

            when (payload.contentType) {
                ClipboardContentType.TEXT -> {
                    payload.textContent?.let { text ->
                        // Strategy A: launch a transparent Activity that has a foreground window.
                        // On OEM devices (Infinix XOS, MIUI, Samsung One UI, etc.) that silently
                        // block setPrimaryClip() from a background foreground-service, an Activity
                        // call always succeeds.  Falls through to Strategy B if the activity
                        // cannot be started (screen off, BackgroundActivityStartException);
                        // pendingReceiveText stays set so Strategy C (onResume) can apply it.
                        ClipboardCaptureActivity.pendingReceiveText = text
                        ClipboardCaptureActivity.pendingReceiveHtml = null
                        try {
                            val receiveIntent = Intent(context, ClipboardCaptureActivity::class.java).apply {
                                putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_RECEIVE)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(receiveIntent)
                            Log.d(TAG, "Launched ClipboardCaptureActivity for RECEIVE (Strategy A)")
                        } catch (e: Exception) {
                            Log.d(TAG, "Strategy A skipped (${e.javaClass.simpleName}) — using direct set")
                        }
                        // Strategy B: direct setPrimaryClip from foreground service.
                        // Works on stock Android and OEMs that permit foreground-service clipboard writes.
                        setClipboardDirectly(text, null)
                        showReceivedNotification(text.take(200), showApplyAction = true)
                    }
                }
                ClipboardContentType.HTML -> {
                    val text = payload.textContent ?: ""
                    val html = payload.htmlContent ?: text
                    // Strategy A
                    ClipboardCaptureActivity.pendingReceiveText = text
                    ClipboardCaptureActivity.pendingReceiveHtml = html
                    try {
                        val receiveIntent = Intent(context, ClipboardCaptureActivity::class.java).apply {
                            putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_RECEIVE)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(receiveIntent)
                        Log.d(TAG, "Launched ClipboardCaptureActivity for RECEIVE (Strategy A)")
                    } catch (e: Exception) {
                        Log.d(TAG, "Strategy A skipped (${e.javaClass.simpleName}) — using direct set")
                    }
                    // Strategy B
                    setClipboardDirectly(text, html)
                    showReceivedNotification(text.ifEmpty { html }.take(200), showApplyAction = true)
                }
                ClipboardContentType.IMAGE -> {
                    payload.imageBase64?.let { base64 ->
                        val bytes = Base64.decode(base64, Base64.NO_WRAP)
                        Log.d(TAG, "Received image clipboard (${bytes.size} bytes)")
                        showReceivedNotification("\uD83D\uDDBC Image copied from laptop")
                    }
                }
            }
            Log.d(TAG, "Clipboard received: ${payload.contentType}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle incoming clipboard", e)
        }
    }

    /**
     * Write clipboard content directly from the foreground service.
     *
     * This works reliably because:
     * 1. ConnectionService is a FOREGROUND service with an active notification.
     *    Android (including MIUI) allows foreground services to call setPrimaryClip().
     * 2. We run on Dispatchers.IO, which is never throttled — the MainDispatcher
     *    throttling was the original cause of "clipboard only updates when you open the app".
     * 3. No Activity launch needed — avoids BackgroundActivityStartException when screen is off,
     *    avoids disrupting the WebSocket message loop, avoids task-stack side effects.
     */
    private fun setClipboardDirectly(text: String?, html: String?) {
        try {
            val clip = if (html != null && text != null) {
                ClipData.newHtmlText("OpenContinuity", text, html)
            } else {
                ClipData.newPlainText("OpenContinuity", text ?: "")
            }
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "Clipboard set directly: ${(text ?: html ?: "").take(60)}")
        } catch (e: Exception) {
            Log.e(TAG, "setPrimaryClip failed", e)
        }
    }

    /**
     * Heads-up notification when clipboard arrives from laptop.
     * Allows the user to see/use the content without opening the app.
     *
     * [showApplyAction] adds an "Apply" action button that launches
     * ClipboardCaptureActivity(MODE_RECEIVE) — a transparent Activity that calls
     * setPrimaryClip() while in the foreground window (works on all OEMs).
     * This lets the user paste the clipboard by tapping one button on the notification shade
     * without having to open the full app.
     */
    private fun showReceivedNotification(preview: String, showApplyAction: Boolean = false) {
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, OpenContinuityApp.CHANNEL_CLIPBOARD)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Clipboard from laptop")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
        if (showApplyAction) {
            val applyIntent = Intent(context, ClipboardCaptureActivity::class.java).apply {
                putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_RECEIVE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val applyPi = PendingIntent.getActivity(
                context, 1, applyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_set_as, "Apply", applyPi)
        }
        notificationManager.notify(NOTIF_ID_RECEIVED, builder.build())
    }

    /**
     * Shown when Android blocks clipboard read because we are in the background (Android 10+).
     *
     * Tapping "Share now" launches ClipboardCaptureActivity, a transparent Activity.
     * Because it is an Activity (foreground window), Android 10+ ALLOWS clipboard access.
     * The Activity reads the clipboard, broadcasts it to the laptop, then finishes instantly.
     *
     * Previous approach used a BroadcastReceiver PendingIntent — that DOES NOT work because
     * a BroadcastReceiver still runs in background context and clipboard access is still blocked.
     */
    private fun showPendingSendNotification() {
        if (connectionManager.connectionState.value !is ConnectionState.Connected) return

        // Launch a transparent Activity so we get a foreground window = clipboard access
        val captureIntent = Intent(context, ClipboardCaptureActivity::class.java).apply {
            putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_SEND)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val capturePi = PendingIntent.getActivity(
            context, 0, captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, OpenContinuityApp.CHANNEL_CLIPBOARD)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Clipboard changed")
            .setContentText("Tap to sync to your laptop")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(capturePi)
            .addAction(android.R.drawable.ic_menu_share, "Share now", capturePi)
            .build()
        notificationManager.notify(NOTIF_ID_PENDING, notif)
    }

    private fun calculateHash(payload: ClipboardSyncPayload): String {
        val content = when (payload.contentType) {
            ClipboardContentType.TEXT -> payload.textContent
            ClipboardContentType.HTML -> payload.htmlContent
            ClipboardContentType.IMAGE -> payload.imageBase64?.take(100)
        }
        return content?.hashCode()?.toString() ?: ""
    }
}
