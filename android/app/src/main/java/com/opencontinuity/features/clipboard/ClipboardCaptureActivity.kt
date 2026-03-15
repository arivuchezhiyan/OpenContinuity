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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Invisible Activity that gains a foreground window so clipboard operations work on MIUI / POCO
 * and other OEMs that silently block clipboard read/write from background services.
 *
 * Two modes:
 *
 *  MODE_SEND   — User tapped "Share clipboard to laptop" notification.
 *                Reads the phone clipboard (requires focus on Android 10+), sends to laptop.
 *
 *  MODE_RECEIVE — Incoming clipboard from laptop.  Sets the phone clipboard via setPrimaryClip().
 *                 MIUI silently ignores setPrimaryClip() from a background foreground service,
 *                 but allows it from a visible Activity.  The activity grabs the pending text
 *                 from the static [pendingReceiveText] / [pendingReceiveHtml] fields, writes
 *                 the clipboard, and finishes instantly.
 *
 * Theme = Theme.OpenContinuity.Transparent  →  no window appears on screen at all.
 * FLAG_ACTIVITY_NO_ANIMATION              →  no transition animation.
 * noHistory + excludeFromRecents          →  no trace in recents.
 */
class ClipboardCaptureActivity : Activity() {

    companion object {
        private const val TAG = "ClipboardCapture"

        const val EXTRA_MODE = "mode"
        const val MODE_SEND = "send"
        const val MODE_RECEIVE = "receive"

        // Pending clipboard content for RECEIVE mode.
        // Set by ClipboardSyncManager before launching this activity.
        // Using static fields avoids Intent extras size limits for large text.
        @Volatile var pendingReceiveText: String? = null
        @Volatile var pendingReceiveHtml: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Suppress any enter/exit transition animation
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        when (intent?.getStringExtra(EXTRA_MODE)) {
            MODE_SEND -> captureAndSend()
            MODE_RECEIVE -> receiveAndSet()
            else -> {
                // Legacy path: default to send mode (old notification PendingIntents)
                captureAndSend()
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // ── MODE_SEND: Read phone clipboard → send to laptop ────────────────
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
            val clip = clipMgr.primaryClip

            if (clip == null || clip.itemCount == 0) {
                finish()
                return
            }

            val item = clip.getItemAt(0)
            val text = item.coerceToText(this)?.toString() ?: ""

            if (text.isBlank()) {
                finish()
                return
            }

            val payload = ClipboardSyncPayload(
                contentType = ClipboardContentType.TEXT,
                textContent = text
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val message = ProtocolMessage(
                        type = MessageType.CLIPBOARD_SYNC,
                        payload = protocolJson.encodeToJsonElement(payload)
                    )
                    connectionManager.broadcast(message)
                    Log.d(TAG, "Clipboard sent via activity: ${text.take(60)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send clipboard", e)
                }
            }

            val notifMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifMgr.cancel(ClipboardSyncManager.NOTIF_ID_PENDING)
            ClipboardSyncManager.clipboardMayHaveChanged = false

        } catch (e: Exception) {
            Log.e(TAG, "Clipboard capture error", e)
        } finally {
            finish()
        }
    }

    // ── MODE_RECEIVE: Write laptop clipboard → phone clipboard ──────────
    private fun receiveAndSet() {
        try {
            val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val text = pendingReceiveText
            val html = pendingReceiveHtml

            if (text == null && html == null) {
                Log.d(TAG, "No pending receive content")
                finish()
                return
            }

            // Activity is in the foreground → setPrimaryClip will NOT be silently ignored
            // by MIUI / POCO / Samsung battery savers.
            val clip = if (html != null && text != null) {
                ClipData.newHtmlText("OpenContinuity", text, html)
            } else {
                ClipData.newPlainText("OpenContinuity", text ?: "")
            }
            clipMgr.setPrimaryClip(clip)
            Log.d(TAG, "Clipboard set via activity: ${(text ?: html ?: "").take(60)}")

            // Clear pending fields
            pendingReceiveText = null
            pendingReceiveHtml = null

        } catch (e: Exception) {
            Log.e(TAG, "Clipboard receive error", e)
        } finally {
            finish()
        }
    }
}
