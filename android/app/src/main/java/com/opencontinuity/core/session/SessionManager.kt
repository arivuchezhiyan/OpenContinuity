package com.opencontinuity.core.session

import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

/**
 * Session Manager — maintains per-device session state across reconnects.
 *
 * Responsibilities:
 *  - One [DeviceSession] per paired device (keyed by deviceId)
 *  - Stores the ECDH-derived encryption key for the lifetime of the session
 *  - Heartbeat tracking: session expires if no ping in 10 s
 *  - Calls [onSessionExpired] so ConnectionService can restart the stack
 */
class SessionManager(private val connectionManager: ConnectionManager) {

    companion object {
        private const val TAG = "SessionManager"
        private const val HEARTBEAT_TIMEOUT_MS = 10_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
    }

    data class DeviceSession(
        val deviceId: String,
        var deviceName: String,
        var sessionToken: String,
        var encryptionKey: SecretKey? = null,
        val capabilities: List<String> = emptyList(),
        val connectedAt: Long = System.currentTimeMillis(),
        @Volatile var lastHeartbeat: Long = System.currentTimeMillis(),
        @Volatile var reconnectCount: Int = 0
    )

    private val sessions = ConcurrentHashMap<String, DeviceSession>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null

    var onSessionExpired: ((DeviceSession) -> Unit)? = null
    var onSessionStarted: ((DeviceSession) -> Unit)? = null
    var onSessionRestored: ((DeviceSession) -> Unit)? = null

    // ─────────────────────────── lifecycle ────────────────────────────────

    fun start() {
        startWatchdog()
        // Register HEARTBEAT_ACK so we can track liveness
        connectionManager.registerHandler(MessageType.HEARTBEAT_ACK) { _, _ ->
            // Find the active session by the connected device
            sessions.values.firstOrNull()?.let { touchHeartbeat(it.deviceId) }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        scope.cancel()
    }

    // ───────────────────────── session CRUD ───────────────────────────────

    fun createSession(
        deviceId: String,
        deviceName: String,
        sessionToken: String,
        capabilities: List<String> = emptyList(),
        encryptionKey: SecretKey? = null
    ): DeviceSession {
        val session = DeviceSession(
            deviceId = deviceId,
            deviceName = deviceName,
            sessionToken = sessionToken,
            encryptionKey = encryptionKey,
            capabilities = capabilities
        )
        sessions[deviceId] = session
        onSessionStarted?.invoke(session)
        Log.i(TAG, "Session started: $deviceName ($deviceId)")
        return session
    }

    fun restoreSession(deviceId: String, deviceName: String, newToken: String): DeviceSession? {
        val existing = sessions[deviceId] ?: return null
        existing.deviceName = deviceName
        existing.sessionToken = newToken
        existing.lastHeartbeat = System.currentTimeMillis()
        existing.reconnectCount++
        onSessionRestored?.invoke(existing)
        Log.i(TAG, "Session restored: $deviceName (reconnect #${existing.reconnectCount})")
        return existing
    }

    fun getSession(deviceId: String): DeviceSession? = sessions[deviceId]

    fun getAllSessions(): List<DeviceSession> = sessions.values.toList()

    fun setEncryptionKey(deviceId: String, key: SecretKey) {
        sessions[deviceId]?.encryptionKey = key
    }

    fun endSession(deviceId: String) {
        sessions.remove(deviceId)?.let {
            Log.i(TAG, "Session ended: ${it.deviceName}")
        }
    }

    // ────────────────────────── heartbeat ─────────────────────────────────

    fun touchHeartbeat(deviceId: String) {
        sessions[deviceId]?.lastHeartbeat = System.currentTimeMillis()
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                checkHeartbeats()
            }
        }
    }

    private fun checkHeartbeats() {
        val now = System.currentTimeMillis()
        for (session in sessions.values) {
            val elapsed = now - session.lastHeartbeat
            if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                Log.w(TAG, "Heartbeat timeout for ${session.deviceName} (${elapsed}ms)")
                onSessionExpired?.invoke(session)
            }
        }
    }
}
