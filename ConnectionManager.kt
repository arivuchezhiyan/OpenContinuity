package com.opencontinuity.core.connection

import android.content.Context
import android.os.Build
import android.util.Log
import com.opencontinuity.core.protocol.*
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connection Manager: WebSocket + HTTP server for all device communication.
 *
 * KEY FIXES vs original:
 *  1. startServer() is now synchronized (serverLock) so two coroutines can't
 *     race to create duplicate Netty engines.
 *  2. isServerRunning() now performs an actual TCP health-check instead of
 *     reading the .application property, which never throws even after a crash.
 *  3. HEARTBEAT messages are sent to connected clients every 10 s so
 *     SessionManager's liveness tracker actually works.
 *  4. On disconnect the connectionState correctly falls back to Listening
 *     (not Disconnected) so the UI and watchdog agree on the real state.
 */
class ConnectionManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "ConnectionManager"
        const val DEFAULT_PORT = 8765
        const val HTTP_PORT    = 8766
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
    }

    // ── FIX 1: mutex-style guard so startServer() is safe to call concurrently
    private val serverLock = Any()

    private var webSocketServer: NettyApplicationEngine? = null
    private var httpServer: NettyApplicationEngine? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val sessions       = ConcurrentHashMap<String, WebSocketSession>()
    private val messageHandlers = ConcurrentHashMap<MessageType, MessageHandler>()

    private val _incomingMessages = MutableSharedFlow<Pair<String, ProtocolMessage>>()
    val incomingMessages: SharedFlow<Pair<String, ProtocolMessage>> = _incomingMessages.asSharedFlow()

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activePairingCode: String? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Server lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        // ── FIX 1: synchronized block prevents double-start race
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

    /**
     * ── FIX 2: Real health check via TCP connect.
     *
     * The original implementation did `ws.application` / `http.application`
     * which NEVER throws — even after a Netty crash — so the watchdog in
     * ConnectionService could never detect a dead server and would never
     * trigger a restart.
     *
     * Now we open a TCP socket to localhost:HTTP_PORT.  If it refuses we
     * know the server is gone.  This is cheap (~1 ms on loopback) and reliable.
     */
    fun isServerRunning(): Boolean {
        if (webSocketServer == null || httpServer == null) return false
        return try {
            java.net.Socket().use { socket ->
                socket.connect(
                    java.net.InetSocketAddress("127.0.0.1", HTTP_PORT),
                    500  // 500 ms timeout
                )
            }
            true
        } catch (_: Exception) {
            false
        }
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
            _connectionState.value  = ConnectionState.Disconnected
            Log.i(TAG, "Server stopped")
        }
    }

    fun cleanup() {
        stopServer()
        serverScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ktor configuration
    // ─────────────────────────────────────────────────────────────────────────

    private fun Application.installPlugins() {
        install(ContentNegotiation) { json(protocolJson) }
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
            // Ping every 5 s — dead socket detected within ~5 s when phone goes to background.
            pingPeriod  = Duration.ofSeconds(5)
            timeout     = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/connect") {
                val sessionId = java.util.UUID.randomUUID().toString()
                Log.i(TAG, "New WebSocket connection: $sessionId")
                sessions[sessionId] = this

                try {
                    var authenticated = false

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                try {
                                    val message = protocolJson.decodeFromString<ProtocolMessage>(text)

                                    if (!authenticated && message.type == MessageType.HANDSHAKE) {
                                        val hs = protocolJson.decodeFromJsonElement<HandshakePayload>(message.payload)
                                        val client = handleHandshake(sessionId, hs)
                                        authenticated = true
                                        updateConnectedClients(sessionId, client, true)
                                        // ── FIX 3: start heartbeats only after first authenticated client
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

            webSocket("/pair") {
                val sessionId = java.util.UUID.randomUUID().toString()
                Log.i(TAG, "Pairing connection: $sessionId")
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            val msg = protocolJson.decodeFromString<ProtocolMessage>(frame.readText())
                            if (msg.type == MessageType.PAIRING_REQUEST) handlePairingRequest(this, msg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse pairing message", e)
                        }
                    }
                }
            }
        }
    }

    private fun Application.configureHttpRoutes() {
        routing {
            get("/info") {
                call.respond(mapOf(
                    "deviceName" to Build.MODEL,
                    "platform"   to "android",
                    "version"    to "1.0.0",
                    "publicKey"  to securityManager.getPublicKeyBase64()
                ))
            }
            post("/upload/{transferId}") {
                val id = call.parameters["transferId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Missing transfer ID"))
                call.respond(mapOf("status" to "ok", "transferId" to id))
            }
            get("/download/{fileId}") {
                val id = call.parameters["fileId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Missing file ID"))
                call.respond(mapOf("status" to "ok", "fileId" to id))
            }
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Heartbeat  (FIX 3)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a HEARTBEAT to every connected client every 10 s.
     * SessionManager listens for the matching HEARTBEAT_ACK and updates
     * lastHeartbeat, which is how it detects stale sessions.
     *
     * The original code registered a HEARTBEAT_ACK handler but never sent
     * any HEARTBEAT — so sessions always expired immediately.
     */
    private fun ensureHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = serverScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (sessions.isEmpty()) continue
                val msg = ProtocolMessage(
                    type    = MessageType.HEARTBEAT,
                    payload = protocolJson.encodeToJsonElement(mapOf("ts" to System.currentTimeMillis()))
                )
                broadcast(msg)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message handling
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun handleHandshake(sessionId: String, handshake: HandshakePayload): ConnectedClient {
        Log.i(TAG, "Handshake from: ${handshake.deviceName}")

        val client = ConnectedClient(
            sessionId   = sessionId,
            deviceName  = handshake.deviceName,
            deviceType  = handshake.deviceType,
            publicKey   = handshake.publicKey,
            connectedAt = System.currentTimeMillis()
        )

        val response = HandshakeResponsePayload(
            accepted    = true,
            deviceName  = Build.MODEL,
            deviceType  = DeviceType.ANDROID,
            publicKey   = securityManager.getPublicKeyBase64(),
            sessionToken = securityManager.generateSessionToken(),
            features    = listOf(
                "clipboard", "file_transfer", "notifications", "sms",
                "camera", "screen_mirror", "battery", "input_control", "touchpad"
            )
        )
        sendToSession(sessionId, ProtocolMessage(
            type    = MessageType.HANDSHAKE_RESPONSE,
            payload = protocolJson.encodeToJsonElement(response)
        ))
        _connectionState.value = ConnectionState.Connected(handshake.deviceName)
        return client
    }

    private suspend fun handlePairingRequest(session: WebSocketSession, message: ProtocolMessage) {
        val req = protocolJson.decodeFromJsonElement<PairingRequestPayload>(message.payload)
        Log.i(TAG, "Pairing request from: ${req.deviceName}")

        val success = activePairingCode != null && activePairingCode == req.pairingCode
        session.send(Frame.Text(protocolJson.encodeToString(ProtocolMessage(
            type    = MessageType.PAIRING_RESPONSE,
            payload = protocolJson.encodeToJsonElement(PairingResponsePayload(
                success      = success,
                sessionToken = if (success) securityManager.generateSessionToken() else null
            ))
        ))))

        if (success) {
            session.send(Frame.Text(protocolJson.encodeToString(ProtocolMessage(
                type    = MessageType.PAIRING_COMPLETE,
                payload = protocolJson.encodeToJsonElement(mapOf("status" to "paired"))
            ))))
        }
    }

    private suspend fun handleMessage(sessionId: String, message: ProtocolMessage) {
        Log.d(TAG, "Received message: ${message.type} from $sessionId")
        _incomingMessages.emit(Pair(sessionId, message))
        messageHandlers[message.type]?.invoke(sessionId, message)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun registerHandler(type: MessageType, handler: MessageHandler) {
        messageHandlers[type] = handler
    }

    suspend fun sendToSession(sessionId: String, message: ProtocolMessage) {
        val session = sessions[sessionId] ?: return
        if (!session.isActive) return
        try {
            session.send(Frame.Text(protocolJson.encodeToString(message)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to $sessionId", e)
        }
    }

    suspend fun broadcast(message: ProtocolMessage) {
        val json = protocolJson.encodeToString(message)
        sessions.forEach { (id, session) ->
            if (session.isActive) {
                try { session.send(Frame.Text(json)) }
                catch (e: Exception) { Log.e(TAG, "Broadcast failed to $id", e) }
            }
        }
    }

    private fun updateConnectedClients(sessionId: String, client: ConnectedClient?, connected: Boolean) {
        val list = _connectedClients.value.toMutableList()
        if (connected && client != null) list.add(client)
        else list.removeAll { it.sessionId == sessionId }
        _connectedClients.value = list

        // ── FIX 4: when last client disconnects, go to Listening (not Disconnected)
        // so the UI shows "waiting" and the watchdog doesn't mistakenly restart.
        if (list.isEmpty()) {
            _connectionState.value = ConnectionState.Listening(DEFAULT_PORT)
        }
    }

    fun setActivePairingCode(code: String) { activePairingCode = code }
    fun clearActivePairingCode()           { activePairingCode = null }
}

typealias MessageHandler = suspend (String, ProtocolMessage) -> Unit

sealed class ConnectionState {
    object Disconnected                      : ConnectionState()
    data class Listening(val port: Int)      : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String)    : ConnectionState()
}

data class ConnectedClient(
    val sessionId:   String,
    val deviceName:  String,
    val deviceType:  DeviceType,
    val publicKey:   String,
    val connectedAt: Long
)
