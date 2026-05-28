package com.opencontinuity.features.notes

import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Receives note sync strokes from Windows (when PC drawing is added later).
 * NoteTakerScreen currently sends to PC; this handles the reverse direction.
 */
class NoteSyncManager(private val connectionManager: ConnectionManager) {

    companion object {
        private const val TAG = "NoteSyncManager"
    }

    private val _incoming = MutableSharedFlow<NoteSyncPayload>(extraBufferCapacity = 32)
    val incoming: SharedFlow<NoteSyncPayload> = _incoming.asSharedFlow()

    fun start() {
        connectionManager.registerHandler(MessageType.NOTE_SYNC) { _, message ->
            try {
                val payload = protocolJson.decodeFromJsonElement<NoteSyncPayload>(message.payload)
                _incoming.tryEmit(payload)
                Log.d(TAG, "Note sync from PC: ${payload.action}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse note sync", e)
            }
        }
        Log.i(TAG, "Note sync listener started")
    }

    fun stop() {
        Log.i(TAG, "Note sync listener stopped")
    }
}
