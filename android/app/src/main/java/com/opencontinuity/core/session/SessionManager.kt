package com.opencontinuity.core.session

import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.MessageType
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class SessionManager(private val connectionManager: ConnectionManager) {

    companion object {
        private const val TAG = "SessionManager"
        private const val HEARTBEAT_TIMEOUT_MS = 30_000L
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
    private var scope: CoroutineScope? = null
    private var watchdogJob: Job? = null

    var onSessionExpired: ((DeviceSession) -> Unit)? = null
    var onSessionStarted: ((DeviceSession) -> Unit)? = null
    var onSessionRestored: ((DeviceSession) -> Unit)? = null

    fun start() {
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        connectionManager.registerHandler(MessageType.HEARTBEAT) { sessionId, _ ->
            touchHeartbeatForWsSession(sessionId)
        }

        connectionManager.registerHandler(MessageType.HEARTBEAT_ACK) { sessionId, _ ->
            touchHeartbeatForWsSession(sessionId)
        }

        startWatchdog()
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        scope?.cancel()
        scope = null
    }

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
        sessions.remove(deviceId)?.let { Log.i(TAG, "Session ended: ${it.deviceName}") }
    }

    fun touchHeartbeat(deviceId: String) {
        sessions[deviceId]?.lastHeartbeat = System.currentTimeMillis()
    }

    private fun touchHeartbeatForWsSession(wsSessionId: String) {
        val clientName = connectionManager.connectedClients.value
            .firstOrNull { it.sessionId == wsSessionId }?.deviceName ?: return
        sessions.values.firstOrNull { it.deviceName == clientName }
            ?.let { touchHeartbeat(it.deviceId) }
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope?.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                checkHeartbeats()
            }
        }
    }

    private fun checkHeartbeats() {
        val now = System.currentTimeMillis()
        for (session in sessions.values.toList()) {
            val elapsed = now - session.lastHeartbeat
            if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                Log.w(TAG, "Heartbeat timeout for ${session.deviceName} (${elapsed}ms)")
                sessions.remove(session.deviceId)
                onSessionExpired?.invoke(session)
            }
        }
    }
}
