package com.opencontinuity.core.connection

import android.content.Context
import android.os.Build
import android.util.Log
import com.opencontinuity.core.protocol.*
import com.opencontinuity.core.security.PairedDevice
import com.opencontinuity.core.security.SecurityManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Connection Manager handles the WebSocket server and client connections
 */
class ConnectionManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "ConnectionManager"
        const val DEFAULT_PORT = 8765
        const val HTTP_PORT = 8766
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
    }

    private val serverLock = Any()
    private var webSocketServer: NettyApplicationEngine? = null
    private var httpServer: NettyApplicationEngine? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    // Active WebSocket sessions
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // Message handlers for different message types
    private val messageHandlers = ConcurrentHashMap<MessageType, MessageHandler>()

    // Incoming message flow for external observers
    private val _incomingMessages = MutableSharedFlow<Pair<String, ProtocolMessage>>()
    val incomingMessages: SharedFlow<Pair<String, ProtocolMessage>> = _incomingMessages.asSharedFlow()

    // Coroutine scope for server operations
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active pairing code for validation
    private var activePairingCode: String? = null

    /**
     * Start the WebSocket server
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        synchronized(serverLock) {
            if (webSocketServer != null) {
                Log.w(TAG, "Server already running")
                return true
            }
            return try {
                webSocketServer = embeddedServer(Netty, port = port) {
                    installPlugins()
                    configureWebSocket()
                }.start(wait = false)

                httpServer = embeddedServer(Netty, port = HTTP_PORT) {
                    installPlugins()
                    configureHttpRoutes()
                }.start(wait = false)

                _connectionState.value = ConnectionState.Listening(port)
                Log.i(TAG, "WebSocket server started on port $port")
                Log.i(TAG, "HTTP server started on port $HTTP_PORT")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
                webSocketServer = null
                httpServer = null
                false
            }
        }
    }

    private fun Application.installPlugins() {
        install(ContentNegotiation) {
            json(protocolJson)
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Server error", cause)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to cause.message))
            }
        }
    }

    private fun Application.configureWebSocket() {
        install(WebSockets) {
            // Ping the client every 20s so we detect dropped connections quickly
            // (was 60s — which meant the server held a dead socket for up to 5 minutes).
            // Windows client sends its own WS ping every 15s; combined these keep the
            // radio awake on hotspot/tethering scenarios.
            // Balanced keepalive: responsive enough for interactive use, tolerant when the
            // CPU/Wi-Fi radio is throttled while the app is in the background.
            pingPeriod = Duration.ofSeconds(10)
            timeout = Duration.ofSeconds(30)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/connect") {
                val sessionId = java.util.UUID.randomUUID().toString()
                Log.i(TAG, "New WebSocket connection: $sessionId")

                sessions[sessionId] = this

                try {
                    // Wait for handshake
                    var authenticated = false
                    var clientInfo: ConnectedClient? = null

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                try {
                                    val message = protocolJson.decodeFromString<ProtocolMessage>(text)

                                    if (!authenticated && message.type == MessageType.HANDSHAKE) {
                                        val handshake = protocolJson.decodeFromJsonElement<HandshakePayload>(message.payload)
                                        clientInfo = handleHandshake(sessionId, handshake)
                                        authenticated = true
                                        updateConnectedClients(sessionId, clientInfo, true)
                                        ensureHeartbeat()
                                    } else if (authenticated) {
                                        handleMessage(sessionId, message)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse message", e)
                                }
                            }
                            is Frame.Close -> {
                                Log.i(TAG, "WebSocket closed: $sessionId")
                                break
                            }
                            else -> {}
                        }
                    }
                } finally {
                    sessions.remove(sessionId)
                    updateConnectedClients(sessionId, null, false)
                    Log.i(TAG, "WebSocket disconnected: $sessionId")
                }
            }

            // QR Code pairing endpoint
            webSocket("/pair") {
                val sessionId = java.util.UUID.randomUUID().toString()
                Log.i(TAG, "Pairing connection: $sessionId")

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            try {
                                val message = protocolJson.decodeFromString<ProtocolMessage>(text)
                                if (message.type == MessageType.PAIRING_REQUEST) {
                                    handlePairingRequest(this, message)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse pairing message", e)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun Application.configureHttpRoutes() {
        routing {
            // Device info endpoint
            get("/info") {
                val deviceInfo = mapOf(
                    "deviceName" to Build.MODEL,
                    "platform" to "android",
                    "version" to "1.0.0",
                    "publicKey" to securityManager.getPublicKeyBase64()
                )
                call.respond(deviceInfo)
            }

            // File upload endpoint
            post("/upload/{transferId}") {
                val transferId = call.parameters["transferId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing transfer ID")
                )
                // File upload handling will be implemented in FileTransferManager
                call.respond(mapOf("status" to "ok", "transferId" to transferId))
            }

            // File download endpoint
            get("/download/{fileId}") {
                val fileId = call.parameters["fileId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing file ID")
                )
                // File download handling will be implemented in FileTransferManager
                call.respond(mapOf("status" to "ok", "fileId" to fileId))
            }

            // Health check
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
        }
    }

    private suspend fun handleHandshake(
        sessionId: String,
        handshake: HandshakePayload
    ): ConnectedClient {
        Log.i(TAG, "Handshake from: ${handshake.deviceName}")

        val client = ConnectedClient(
            sessionId = sessionId,
            deviceName = handshake.deviceName,
            deviceType = handshake.deviceType,
            publicKey = handshake.publicKey,
            connectedAt = System.currentTimeMillis()
        )

        // Send handshake response
        val app = com.opencontinuity.OpenContinuityApp.instance
        val peerDeviceId = handshake.deviceId ?: sessionId
        val existing = app.sessionManager.getSession(peerDeviceId)
        val sessionToken = if (existing != null) {
            app.sessionManager.restoreSession(peerDeviceId, handshake.deviceName, existing.sessionToken)
            existing.sessionToken
        } else {
            val token = securityManager.generateSessionToken()
            app.sessionManager.createSession(
                deviceId = peerDeviceId,
                deviceName = handshake.deviceName,
                sessionToken = token,
                capabilities = handshake.features
            )
            token
        }

        val response = HandshakeResponsePayload(
            accepted = true,
            deviceName = Build.MODEL,
            deviceType = DeviceType.ANDROID,
            publicKey = securityManager.getPublicKeyBase64(),
            sessionToken = sessionToken,
            deviceId = securityManager.deviceId,
            features = listOf(
                "clipboard", "file_transfer", "notifications", "sms",
                "camera", "screen_mirror", "battery", "input_control", "touchpad"
            )
        )

        val responseMessage = ProtocolMessage(
            type = MessageType.HANDSHAKE_RESPONSE,
            payload = protocolJson.encodeToJsonElement(response)
        )

        sendToSession(sessionId, responseMessage)
        _connectionState.value = ConnectionState.Connected(handshake.deviceName)

        return client
    }

    private fun ensureHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = serverScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (sessions.isEmpty()) continue
                val msg = ProtocolMessage(
                    type = MessageType.HEARTBEAT,
                    payload = protocolJson.encodeToJsonElement(mapOf("ts" to System.currentTimeMillis()))
                )
                broadcast(msg)
            }
        }
    }

    private suspend fun handlePairingRequest(
        session: WebSocketSession,
        message: ProtocolMessage
    ) {
        val pairingRequest = protocolJson.decodeFromJsonElement<PairingRequestPayload>(message.payload)
        Log.i(TAG, "Pairing request from: ${pairingRequest.deviceName}")

        // Validate pairing code against active pairing session
        val success = (activePairingCode != null && activePairingCode == pairingRequest.pairingCode)

        val response = PairingResponsePayload(
            success = success,
            sessionToken = if (success) securityManager.generateSessionToken() else null
        )

        val responseMessage = ProtocolMessage(
            type = MessageType.PAIRING_RESPONSE,
            payload = protocolJson.encodeToJsonElement(response)
        )

        session.send(Frame.Text(protocolJson.encodeToString(responseMessage)))

        if (success) {
            val completeMessage = ProtocolMessage(
                type = MessageType.PAIRING_COMPLETE,
                payload = protocolJson.encodeToJsonElement(mapOf("status" to "paired"))
            )
            session.send(Frame.Text(protocolJson.encodeToString(completeMessage)))
        }
    }

    private suspend fun handleMessage(sessionId: String, message: ProtocolMessage) {
        Log.d(TAG, "Received message: ${message.type} from $sessionId")

        when (message.type) {
            MessageType.HEARTBEAT -> {
                val ack = ProtocolMessage(
                    type = MessageType.HEARTBEAT_ACK,
                    payload = protocolJson.encodeToJsonElement(mapOf("ok" to true))
                )
                sendToSession(sessionId, ack)
            }
            MessageType.SESSION_RESTORE -> {
                handleSessionRestore(sessionId, message)
            }
            else -> {
                _incomingMessages.emit(Pair(sessionId, message))
                messageHandlers[message.type]?.invoke(sessionId, message)
            }
        }
    }

    private suspend fun handleSessionRestore(sessionId: String, message: ProtocolMessage) {
        val payload = protocolJson.decodeFromJsonElement<SessionRestorePayload>(message.payload)
        val session = com.opencontinuity.OpenContinuityApp.instance.sessionManager
            .getSession(payload.deviceId)
        val restored = session != null && session.sessionToken == payload.sessionToken

        val ackPayload = SessionRestoreAckPayload(
            restored = restored,
            sessionToken = if (restored) session?.sessionToken else null,
            errorMessage = if (restored) null else "Invalid or expired session"
        )
        sendToSession(
            sessionId,
            ProtocolMessage(
                type = MessageType.SESSION_RESTORE_ACK,
                payload = protocolJson.encodeToJsonElement(ackPayload)
            )
        )
        if (restored) {
            session?.let { com.opencontinuity.OpenContinuityApp.instance.sessionManager.touchHeartbeat(it.deviceId) }
            Log.i(TAG, "Session restored for ${payload.deviceName}")
        } else {
            Log.w(TAG, "Session restore failed for ${payload.deviceName}")
        }
    }

    /**
     * Register a handler for a specific message type
     */
    fun registerHandler(type: MessageType, handler: MessageHandler) {
        messageHandlers[type] = handler
    }

    /**
     * Send a message to a specific session
     */
    suspend fun sendToSession(sessionId: String, message: ProtocolMessage) {
        val session = sessions[sessionId]
        if (session != null && session.isActive) {
            try {
                val json = protocolJson.encodeToString(message)
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message to $sessionId", e)
            }
        }
    }

    /**
     * Broadcast a message to all connected clients
     */
    suspend fun broadcast(message: ProtocolMessage) {
        val json = protocolJson.encodeToString(message)
        sessions.forEach { (sessionId, session) ->
            if (session.isActive) {
                try {
                    session.send(Frame.Text(json))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to broadcast to $sessionId", e)
                }
            }
        }
    }

    private fun updateConnectedClients(sessionId: String, client: ConnectedClient?, connected: Boolean) {
        val currentList = _connectedClients.value.toMutableList()
        if (connected && client != null) {
            currentList.add(client)
        } else {
            currentList.removeAll { it.sessionId == sessionId }
        }
        _connectedClients.value = currentList

        if (currentList.isEmpty()) {
            _connectionState.value = ConnectionState.Listening(DEFAULT_PORT)
        }
    }

    /**
     * Check if the server is actually running and responsive.
     * Uses resolvedConnectors() instead of just null-checking — if the Ktor Netty
     * engine has silently crashed, this will return false rather than a stale true.
     */
    fun isServerRunning(): Boolean {
        if (webSocketServer == null || httpServer == null) return false
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", HTTP_PORT), 500)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Set the active pairing code for validation when devices attempt to pair.
     * This should be called when the pairing UI generates a code.
     */
    fun setActivePairingCode(code: String) {
        activePairingCode = code
        Log.i(TAG, "Active pairing code set")
    }

    /**
     * Clear the active pairing code (called after successful pairing or session timeout)
     */
    fun clearActivePairingCode() {
        activePairingCode = null
        Log.i(TAG, "Active pairing code cleared")
    }

    fun stopServer() {
        synchronized(serverLock) {
            heartbeatJob?.cancel()
            heartbeatJob = null
            webSocketServer?.stop(1000, 2000)
            webSocketServer = null
            httpServer?.stop(1000, 2000)
            httpServer = null
            sessions.clear()
            _connectedClients.value = emptyList()
            _connectionState.value = ConnectionState.Disconnected
            Log.i(TAG, "Server stopped")
        }
    }

    fun cleanup() {
        stopServer()
        serverScope.cancel()
    }
}

/**
 * Type alias for message handlers
 */
typealias MessageHandler = suspend (String, ProtocolMessage) -> Unit

/**
 * Connection state
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Listening(val port: Int) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Represents a connected client
 */
data class ConnectedClient(
    val sessionId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val publicKey: String,
    val connectedAt: Long
)
