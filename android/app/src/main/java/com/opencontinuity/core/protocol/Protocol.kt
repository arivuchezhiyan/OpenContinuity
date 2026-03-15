package com.opencontinuity.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Protocol version for compatibility checking
 */
const val PROTOCOL_VERSION = "2.0.0"

/**
 * JSON serializer configuration
 */
val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = false
}

/**
 * Base message envelope for all protocol messages
 */
@Serializable
data class ProtocolMessage(
    val type: MessageType,
    val payload: JsonElement,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String = generateMessageId(),
    val signature: String? = null
)

/**
 * All supported message types
 */
@Serializable
enum class MessageType {
    // Connection & Pairing
    @SerialName("handshake") HANDSHAKE,
    @SerialName("handshake_response") HANDSHAKE_RESPONSE,
    @SerialName("pairing_request") PAIRING_REQUEST,
    @SerialName("pairing_response") PAIRING_RESPONSE,
    @SerialName("pairing_complete") PAIRING_COMPLETE,
    @SerialName("heartbeat") HEARTBEAT,
    @SerialName("heartbeat_ack") HEARTBEAT_ACK,
    @SerialName("disconnect") DISCONNECT,

    // Clipboard
    @SerialName("clipboard_sync") CLIPBOARD_SYNC,
    @SerialName("clipboard_ack") CLIPBOARD_ACK,

    // File Transfer
    @SerialName("file_transfer_request") FILE_TRANSFER_REQUEST,
    @SerialName("file_transfer_accept") FILE_TRANSFER_ACCEPT,
    @SerialName("file_transfer_reject") FILE_TRANSFER_REJECT,
    @SerialName("file_transfer_progress") FILE_TRANSFER_PROGRESS,
    @SerialName("file_transfer_complete") FILE_TRANSFER_COMPLETE,
    @SerialName("file_transfer_error") FILE_TRANSFER_ERROR,

    // Notifications
    @SerialName("notification_post") NOTIFICATION_POST,
    @SerialName("notification_dismiss") NOTIFICATION_DISMISS,
    @SerialName("notification_action") NOTIFICATION_ACTION,

    // SMS
    @SerialName("sms_conversations") SMS_CONVERSATIONS,
    @SerialName("sms_messages") SMS_MESSAGES,
    @SerialName("sms_send") SMS_SEND,
    @SerialName("sms_send_result") SMS_SEND_RESULT,
    @SerialName("sms_received") SMS_RECEIVED,

    // Camera / Screen Mirror
    @SerialName("stream_start") STREAM_START,
    @SerialName("stream_stop") STREAM_STOP,
    @SerialName("stream_offer") STREAM_OFFER,
    @SerialName("stream_answer") STREAM_ANSWER,
    @SerialName("stream_ice_candidate") STREAM_ICE_CANDIDATE,

    // Battery & Status
    @SerialName("device_status") DEVICE_STATUS,
    @SerialName("battery_status") BATTERY_STATUS,

    // Input Control
    @SerialName("input_event") INPUT_EVENT,
    @SerialName("touchpad_event") TOUCHPAD_EVENT,

    // File Chunk Transfer
    @SerialName("file_chunk") FILE_CHUNK,

    // Cross-Device Drag and Drop
    @SerialName("drag_file_start") DRAG_FILE_START,
    @SerialName("drag_file_cancel") DRAG_FILE_CANCEL,
    @SerialName("drag_file_drop") DRAG_FILE_DROP,

    // Session restore
    @SerialName("session_restore") SESSION_RESTORE,
    @SerialName("session_restore_ack") SESSION_RESTORE_ACK,

    // Screenshot Sync
    @SerialName("screenshot_available") SCREENSHOT_AVAILABLE,
    @SerialName("screenshot_request") SCREENSHOT_REQUEST,

    // Error
    @SerialName("error") ERROR
}

// ============== Pairing Messages ==============

@Serializable
data class HandshakePayload(
    val deviceName: String,
    val deviceType: DeviceType,
    val protocolVersion: String = PROTOCOL_VERSION,
    val publicKey: String,
    val features: List<String> = emptyList(),
    /** Granular capability flags for dynamic feature negotiation */
    val capabilities: DeviceCapabilities? = null,
    val deviceId: String? = null
)

@Serializable
data class DeviceCapabilities(
    val clipboard: Boolean = true,
    val input: Boolean = true,
    val fileTransfer: Boolean = true,
    val dragDrop: Boolean = true,
    val notifications: Boolean = true,
    val sms: Boolean = true,
    val camera: Boolean = true,
    val screenMirror: Boolean = true,
    val battery: Boolean = true
)

@Serializable
data class HandshakeResponsePayload(
    val accepted: Boolean,
    val deviceName: String,
    val deviceType: DeviceType,
    val protocolVersion: String = PROTOCOL_VERSION,
    val publicKey: String,
    val sessionToken: String? = null,
    val features: List<String> = emptyList()
)

@Serializable
data class PairingRequestPayload(
    val pairingCode: String,
    val deviceName: String,
    val publicKey: String
)

@Serializable
data class PairingResponsePayload(
    val success: Boolean,
    val sessionToken: String? = null,
    val errorMessage: String? = null
)

@Serializable
enum class DeviceType {
    @SerialName("android") ANDROID,
    @SerialName("windows") WINDOWS,
    @SerialName("unknown") UNKNOWN
}

// ============== Clipboard Messages ==============

@Serializable
data class ClipboardSyncPayload(
    val contentType: ClipboardContentType,
    val textContent: String? = null,
    val htmlContent: String? = null,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    /** Source device ID — receivers skip own messages to prevent echo */
    val deviceId: String? = null,
    /** SHA-256 hex of content for cross-device deduplication */
    val contentHash: String? = null
)

@Serializable
enum class ClipboardContentType {
    @SerialName("text") TEXT,
    @SerialName("html") HTML,
    @SerialName("image") IMAGE
}

// ============== File Transfer Messages ==============

@Serializable
data class FileTransferRequestPayload(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val checksum: String? = null
)

@Serializable
data class FileTransferProgressPayload(
    val transferId: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progress: Float
)

@Serializable
data class FileTransferCompletePayload(
    val transferId: String,
    val success: Boolean,
    val filePath: String? = null,
    val errorMessage: String? = null
)

// ============== File Chunk Messages ==============

@Serializable
data class FileChunkPayload(
    val transferId: String,
    /** 0-based chunk index */
    val sequence: Int,
    val totalChunks: Int,
    /** Base64-encoded raw bytes */
    val data: String,
    /** SHA-256 hex of raw chunk bytes for per-chunk integrity */
    val checksum: String
)

// ============== Cross-Device Drag and Drop ==============

@Serializable
data class DragFileStartPayload(
    val dragId: String,
    val sourceDeviceId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    /** Normalized 0–1 screen-edge position where drag exited */
    val edgeX: Float,
    val edgeY: Float
)

@Serializable
data class DragFileDropPayload(
    val dragId: String,
    val targetDeviceId: String,
    val accepted: Boolean,
    val errorMessage: String? = null
)

// ============== Session Restore ==============

@Serializable
data class SessionRestorePayload(
    val sessionToken: String,
    val deviceId: String,
    val deviceName: String
)

// ============== Notification Messages ==============

@Serializable
data class NotificationPayload(
    val notificationId: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val subText: String? = null,
    val iconBase64: String? = null,
    val timestamp: Long,
    val actions: List<NotificationAction> = emptyList()
)

@Serializable
data class NotificationAction(
    val actionId: String,
    val title: String
)

// ============== SMS Messages ==============

@Serializable
data class SmsConversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String?,
    val timestamp: Long,
    val unreadCount: Int
)

@Serializable
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isRead: Boolean
)

@Serializable
data class SmsSendPayload(
    val address: String,
    val body: String,
    val requestId: String
)

@Serializable
data class SmsSendResultPayload(
    val requestId: String,
    val success: Boolean,
    val errorMessage: String? = null
)

// ============== Device Status Messages ==============

@Serializable
data class BatteryStatusPayload(
    val level: Int,
    val isCharging: Boolean,
    val chargeType: String,
    val temperature: Float,
    val health: String
)

@Serializable
data class DeviceStatusPayload(
    val deviceName: String,
    val battery: BatteryStatusPayload,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val wifiConnected: Boolean,
    val wifiSsid: String?
)

// ============== Input Control Messages ==============

@Serializable
data class InputEventPayload(
    val eventType: InputEventType,
    val x: Float? = null,
    val y: Float? = null,
    val keyCode: Int? = null,
    val text: String? = null,
    val scrollDelta: Float? = null
)

@Serializable
enum class InputEventType {
    @SerialName("tap") TAP,
    @SerialName("long_press") LONG_PRESS,
    @SerialName("swipe") SWIPE,
    @SerialName("scroll") SCROLL,
    @SerialName("key") KEY,
    @SerialName("text") TEXT
}

@Serializable
data class TouchpadEventPayload(
    val eventType: TouchpadEventType,
    val deltaX: Float? = null,
    val deltaY: Float? = null,
    val fingers: Int = 1,
    val scrollDelta: Float? = null
)

@Serializable
enum class TouchpadEventType {
    @SerialName("move") MOVE,
    @SerialName("click") CLICK,
    @SerialName("right_click") RIGHT_CLICK,
    @SerialName("scroll") SCROLL,
    @SerialName("drag_start") DRAG_START,
    @SerialName("drag_end") DRAG_END
}

// ============== Error Messages ==============

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
    val details: String? = null
)

// ============== Utility Functions ==============

private fun generateMessageId(): String {
    return java.util.UUID.randomUUID().toString()
}
