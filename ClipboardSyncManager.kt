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
 * Clipboard Sync Manager — synchronises clipboard between Android and Windows.
 *
 * KEY FIXES vs original:
 *  1. calculateHash() now uses SHA-256 instead of String.hashCode() (32-bit,
 *     high collision rate on short strings).
 *  2. handleIncomingClipboard() no longer double-calls setPrimaryClip():
 *     it uses Strategy B (direct set) only, and Strategy A (Activity) only when
 *     Strategy B fails — eliminating the duplicate clipboard-listener triggers.
 *  3. start() is idempotent: calling it a second time (e.g. from watchdog restart)
 *     stops the previous scope first so we never accumulate duplicate listeners.
 *  4. Exported lastClipboardHash as a @Volatile companion-object field so
 *     ClipboardCaptureActivity can update it after a send (prevents re-send).
 *  5. Exported localDeviceId via companion object so ClipboardCaptureActivity
 *     can tag outgoing messages with the correct deviceId (echo suppression).
 */
class ClipboardSyncManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "ClipboardSyncManager"
        private const val DEBOUNCE_MS = 300L
        private const val MAX_IMAGE_SIZE = 1024 * 1024 // 1 MB (uncompressed)
        private const val NOTIF_ID_RECEIVED = 3001
        const val  NOTIF_ID_PENDING         = 3002
        private const val PENDING_NOTIF_COOLDOWN_MS = 4_000L

        /**
         * Flags that the clipboard changed while we were backgrounded.
         * Reset by ClipboardCaptureActivity after a successful send.
         */
        @Volatile var clipboardMayHaveChanged = false

        /**
         * ── FIX 4: shared hash so ClipboardCaptureActivity can update it.
         * Without this the poll loop re-detects the same content 1.5 s after
         * the Activity sends it and sends it again.
         */
        @Volatile var lastSentHash: String = ""

        /**
         * ── FIX 5: shared deviceId so ClipboardCaptureActivity can tag
         * outgoing messages for echo suppression.
         */
        @Volatile var sharedDeviceId: String = ""
    }

    /** Local device ID — set by ConnectionService before start() */
    var localDeviceId: String = ""
        set(value) {
            field = value
            sharedDeviceId = value   // keep companion in sync
        }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── FIX 3: track own scope so start() can safely cancel a previous one
    private var scope: CoroutineScope? = null

    // Instance copy of the hash, kept in sync with companion
    private var lastClipboardHash: String
        get()      = lastSentHash
        set(value) { lastSentHash = value }

    private var debounceJob: Job? = null
    private var lastPendingNotifMs = 0L

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardMayHaveChanged = true
        onLocalClipboardChanged()
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** ── FIX 3: idempotent start() — cancels any previous scope first */
    fun start() {
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Remove any stale listener before adding a fresh one
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        // Background poll loop: OnPrimaryClipChangedListener doesn't fire in background on 10+
        scope!!.launch {
            while (true) {
                delay(1500)
                processAndSendClipboard()
            }
        }

        // Push clipboard to every newly connected client
        scope!!.launch {
            connectionManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    delay(500)
                    processAndSendClipboard()
                    notificationManager.cancel(NOTIF_ID_PENDING)
                }
            }
        }

        // Receive handler
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

    // ─────────────────────────────────────────────────────────────────────────

    private fun onLocalClipboardChanged() {
        debounceJob?.cancel()
        debounceJob = scope?.launch {
            delay(DEBOUNCE_MS)
            processAndSendClipboard()
        }
    }

    private suspend fun processAndSendClipboard() {
        try {
            val clip = try {
                clipboardManager.primaryClip
            } catch (se: SecurityException) {
                val now = System.currentTimeMillis()
                if (clipboardMayHaveChanged && (now - lastPendingNotifMs) > PENDING_NOTIF_COOLDOWN_MS) {
                    lastPendingNotifMs = now
                    showPendingSendNotification()
                }
                Log.d(TAG, "Clipboard read blocked (background restriction)")
                return
            } ?: return
            if (clip.itemCount == 0) return

            clipboardMayHaveChanged = false

            val item    = clip.getItemAt(0)
            val payload = extractClipboardContent(item, clip.description.label?.toString()) ?: return

            // ── FIX 1: SHA-256 hash — no 32-bit collisions on short strings
            val hash = calculateHash(payload)
            if (hash == lastClipboardHash) return
            lastClipboardHash = hash

            val tagged = payload.copy(deviceId = localDeviceId, contentHash = hash)
            connectionManager.broadcast(ProtocolMessage(
                type    = MessageType.CLIPBOARD_SYNC,
                payload = protocolJson.encodeToJsonElement(tagged)
            ))
            Log.d(TAG, "Clipboard sent: ${payload.contentType}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process clipboard", e)
        }
    }

    private fun extractClipboardContent(item: ClipData.Item, label: String?): ClipboardSyncPayload? {
        // HTML first (richer), then plain text, then URI image
        item.htmlText?.let { html ->
            return ClipboardSyncPayload(
                contentType  = ClipboardContentType.HTML,
                htmlContent  = html,
                textContent  = item.text?.toString()
            )
        }
        item.text?.let { text ->
            return ClipboardSyncPayload(contentType = ClipboardContentType.TEXT, textContent = text.toString())
        }
        item.uri?.let { uri ->
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap != null) {
                val b64 = encodeBitmapToBase64(bitmap)
                if (b64 != null) {
                    return ClipboardSyncPayload(
                        contentType   = ClipboardContentType.IMAGE,
                        imageBase64   = b64,
                        imageMimeType = "image/png"
                    )
                }
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Incoming clipboard from laptop
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun handleIncomingClipboard(message: ProtocolMessage) {
        try {
            val payload = protocolJson.decodeFromJsonElement<ClipboardSyncPayload>(message.payload)

            // Echo prevention
            if (!payload.deviceId.isNullOrEmpty() && payload.deviceId == localDeviceId) {
                Log.d(TAG, "Clipboard echo suppressed")
                return
            }

            // Pre-set hash BEFORE writing clipboard so the listener sees the same hash
            // and skips the echo-send loop.
            lastClipboardHash = calculateHash(payload)

            when (payload.contentType) {
                ClipboardContentType.TEXT -> {
                    val text = payload.textContent ?: return

                    // ── FIX 2: try Strategy B (direct set from foreground service) first.
                    // If it succeeds on this device, we're done — no Activity needed.
                    // Only fall through to Strategy A when the OEM silently blocks it.
                    val directOk = setClipboardDirectly(text, null)
                    if (!directOk) {
                        // Strategy A: transparent Activity for OEMs that block foreground-service writes
                        ClipboardCaptureActivity.pendingReceiveText = text
                        ClipboardCaptureActivity.pendingReceiveHtml = null
                        launchCaptureActivity(ClipboardCaptureActivity.MODE_RECEIVE)
                    }
                    showReceivedNotification(text.take(200), showApplyAction = !directOk)
                }
                ClipboardContentType.HTML -> {
                    val text = payload.textContent ?: ""
                    val html = payload.htmlContent ?: text
                    val directOk = setClipboardDirectly(text, html)
                    if (!directOk) {
                        ClipboardCaptureActivity.pendingReceiveText = text
                        ClipboardCaptureActivity.pendingReceiveHtml = html
                        launchCaptureActivity(ClipboardCaptureActivity.MODE_RECEIVE)
                    }
                    showReceivedNotification(text.ifEmpty { html }.take(200), showApplyAction = !directOk)
                }
                ClipboardContentType.IMAGE -> {
                    payload.imageBase64?.let { b64 ->
                        val bytes = Base64.decode(b64, Base64.NO_WRAP)
                        Log.d(TAG, "Received image clipboard (${bytes.size} bytes)")
                        showReceivedNotification("🖼 Image copied from laptop")
                    }
                }
            }
            Log.d(TAG, "Clipboard received: ${payload.contentType}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle incoming clipboard", e)
        }
    }

    /**
     * ── FIX 2: returns true if setPrimaryClip() succeeded, false if silently blocked.
     *
     * On stock Android and most OEMs, a foreground service CAN call setPrimaryClip().
     * On MIUI / POCO / some Infinix XOS builds it silently ignores the call.
     * We detect silence by reading back the clipboard immediately after writing.
     */
    private fun setClipboardDirectly(text: String?, html: String?): Boolean {
        return try {
            val clip = if (html != null && text != null)
                ClipData.newHtmlText("OpenContinuity", text, html)
            else
                ClipData.newPlainText("OpenContinuity", text ?: "")

            clipboardManager.setPrimaryClip(clip)

            // Verify the write landed (MIUI silently drops it)
            val verify = try { clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() } catch (_: Exception) { null }
            val ok = verify == (text ?: "")
            Log.d(TAG, "setPrimaryClip direct: ok=$ok '${(text ?: "").take(40)}'")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setPrimaryClip failed", e)
            false
        }
    }

    private fun launchCaptureActivity(mode: String) {
        try {
            context.startActivity(Intent(context, ClipboardCaptureActivity::class.java).apply {
                putExtra(ClipboardCaptureActivity.EXTRA_MODE, mode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            Log.d(TAG, "Launched ClipboardCaptureActivity mode=$mode")
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch ClipboardCaptureActivity: ${e.javaClass.simpleName}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notifications
    // ─────────────────────────────────────────────────────────────────────────

    private fun showReceivedNotification(preview: String, showApplyAction: Boolean = false) {
        val openPi = PendingIntent.getActivity(
            context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName),
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
            val applyPi = PendingIntent.getActivity(
                context, 1,
                Intent(context, ClipboardCaptureActivity::class.java).apply {
                    putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_RECEIVE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_set_as, "Apply", applyPi)
        }
        notificationManager.notify(NOTIF_ID_RECEIVED, builder.build())
    }

    private fun showPendingSendNotification() {
        if (connectionManager.connectionState.value !is ConnectionState.Connected) return
        val capturePi = PendingIntent.getActivity(
            context, 0,
            Intent(context, ClipboardCaptureActivity::class.java).apply {
                putExtra(ClipboardCaptureActivity.EXTRA_MODE, ClipboardCaptureActivity.MODE_SEND)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
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

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadBitmapFromUri(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { Log.e(TAG, "loadBitmap failed", e); null }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String? = try {
        val stream = ByteArrayOutputStream()
        val scaled = if (bitmap.byteCount > MAX_IMAGE_SIZE) {
            val scale = Math.sqrt(MAX_IMAGE_SIZE.toDouble() / bitmap.byteCount)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.PNG, 90, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { Log.e(TAG, "encodeBitmap failed", e); null }

    /**
     * ── FIX 1: SHA-256 hash.
     * Original used hashCode() — a 32-bit int with ~1-in-4B collision rate that
     * can silently suppress a clipboard update when two different strings happen
     * to share the same hash.  SHA-256 is collision-proof for practical purposes.
     */
    internal fun calculateHash(payload: ClipboardSyncPayload): String {
        val content = when (payload.contentType) {
            ClipboardContentType.TEXT  -> payload.textContent
            ClipboardContentType.HTML  -> payload.htmlContent
            ClipboardContentType.IMAGE -> payload.imageBase64?.take(100)
        } ?: return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
