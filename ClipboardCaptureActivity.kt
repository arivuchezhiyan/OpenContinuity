package com.opencontinuity.features.clipboard

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.connection.ConnectionState
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Invisible Activity that gains a foreground window for clipboard access.
 *
 * Two modes:
 *  MODE_SEND    — user tapped "Share clipboard to laptop" notification.
 *  MODE_RECEIVE — incoming clipboard from laptop needs setPrimaryClip()
 *                 that the OEM blocks from a background foreground-service.
 *
 * KEY FIXES vs original:
 *  1. captureAndSend() now tags the outgoing payload with sharedDeviceId so
 *     the echo-suppression logic in ClipboardSyncManager ignores the round-trip.
 *  2. captureAndSend() updates ClipboardSyncManager.lastSentHash so the 1.5 s
 *     poll loop doesn't re-send the same content immediately after this Activity.
 *  3. The coroutine scope is tied to the Activity (cancelled in onDestroy).
 *  4. receiveAndSet() reads pendingReceiveText/Html then nulls them before
 *     calling setPrimaryClip() — avoids a concurrent read from a second launch.
 */
class ClipboardCaptureActivity : Activity() {

    companion object {
        private const val TAG = "ClipboardCapture"

        const val EXTRA_MODE    = "mode"
        const val MODE_SEND     = "send"
        const val MODE_RECEIVE  = "receive"

        @Volatile var pendingReceiveText: String? = null
        @Volatile var pendingReceiveHtml: String? = null
    }

    // ── FIX 3: Activity-scoped coroutine job, cancelled in onDestroy
    private var sendJob: Job? = null
    private val activityScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        when (intent?.getStringExtra(EXTRA_MODE)) {
            MODE_SEND    -> captureAndSend()
            MODE_RECEIVE -> receiveAndSet()
            else         -> captureAndSend()   // legacy path
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        sendJob?.cancel()
        super.onDestroy()
    }

    // ── MODE_SEND ────────────────────────────────────────────────────────────

    private fun captureAndSend() {
        try {
            val app = application as OpenContinuityApp
            val connectionManager = app.connectionManager

            if (connectionManager.connectionState.value !is ConnectionState.Connected) {
                Log.d(TAG, "Not connected — skip clipboard capture")
                finish()
                return
            }

            val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip    = clipMgr.primaryClip
            if (clip == null || clip.itemCount == 0) { finish(); return }

            val item = clip.getItemAt(0)
            val text = item.coerceToText(this)?.toString() ?: ""
            if (text.isBlank()) { finish(); return }

            val payload = ClipboardSyncPayload(
                contentType = ClipboardContentType.TEXT,
                textContent = text,
                // ── FIX 1: tag with our deviceId so manager suppresses the echo
                deviceId    = ClipboardSyncManager.sharedDeviceId
            )

            // ── FIX 2: pre-compute and store hash before the send so the poll loop
            // won't re-detect the same content 1.5 s later and send it again.
            val hash = computeTextHash(text)
            ClipboardSyncManager.lastSentHash = hash

            sendJob = activityScope.launch {
                try {
                    connectionManager.broadcast(ProtocolMessage(
                        type    = MessageType.CLIPBOARD_SYNC,
                        payload = protocolJson.encodeToJsonElement(payload.copy(contentHash = hash))
                    ))
                    Log.d(TAG, "Clipboard sent via activity: ${text.take(60)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send clipboard", e)
                }
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ClipboardSyncManager.NOTIF_ID_PENDING)
            ClipboardSyncManager.clipboardMayHaveChanged = false

        } catch (e: Exception) {
            Log.e(TAG, "Clipboard capture error", e)
        } finally {
            finish()
        }
    }

    // ── MODE_RECEIVE ─────────────────────────────────────────────────────────

    private fun receiveAndSet() {
        try {
            // ── FIX 4: read and clear atomically-ish before calling setPrimaryClip
            val text = pendingReceiveText.also { pendingReceiveText = null }
            val html = pendingReceiveHtml.also { pendingReceiveHtml = null }

            if (text == null && html == null) {
                Log.d(TAG, "No pending receive content")
                finish()
                return
            }

            val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = if (html != null && text != null)
                ClipData.newHtmlText("OpenContinuity", text, html)
            else
                ClipData.newPlainText("OpenContinuity", text ?: "")

            clipMgr.setPrimaryClip(clip)
            Log.d(TAG, "Clipboard set via activity: ${(text ?: html ?: "").take(60)}")

        } catch (e: Exception) {
            Log.e(TAG, "Clipboard receive error", e)
        } finally {
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Lightweight SHA-256 for a plain-text string — same algorithm as ClipboardSyncManager */
    private fun computeTextHash(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
