package com.opencontinuity.features.unlock

import android.os.Build
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Sends a wake/unlock request to the connected Windows PC.
 * Wakes the display; does not bypass the lock-screen PIN.
 */
object UnlockHelper {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun requestPcWake(reason: String? = null) {
        scope.launch {
            val app = OpenContinuityApp.instance
            val payload = UnlockRequestPayload(
                deviceName = Build.MODEL,
                reason = reason ?: "proximity_wake"
            )
            app.connectionManager.broadcast(
                ProtocolMessage(
                    type = MessageType.UNLOCK_REQUEST,
                    payload = protocolJson.encodeToJsonElement(payload)
                )
            )
        }
    }
}
