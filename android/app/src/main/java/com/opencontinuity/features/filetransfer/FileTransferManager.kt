package com.opencontinuity.features.filetransfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * File Transfer Manager - handles sending and receiving files
 */
class FileTransferManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "FileTransferManager"
        private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active transfers
    private val _activeTransfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val activeTransfers: StateFlow<List<FileTransfer>> = _activeTransfers.asStateFlow()

    private val pendingTransfers = ConcurrentHashMap<String, PendingTransfer>()

    // Download directory
    private val downloadDir: File by lazy {
        File(context.getExternalFilesDir(null), "Downloads").apply {
            if (!exists()) mkdirs()
        }
    }

    fun start() {
        // Register handlers
        connectionManager.registerHandler(MessageType.FILE_TRANSFER_REQUEST) { sessionId, message ->
            handleTransferRequest(sessionId, message)
        }

        connectionManager.registerHandler(MessageType.FILE_TRANSFER_ACCEPT) { sessionId, message ->
            handleTransferAccepted(sessionId, message)
        }

        connectionManager.registerHandler(MessageType.FILE_TRANSFER_REJECT) { sessionId, message ->
            handleTransferRejected(message)
        }

        Log.i(TAG, "File transfer manager started")
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "File transfer manager stopped")
    }

    /**
     * Send a file to connected devices
     */
    suspend fun sendFile(uri: Uri): String? {
        return try {
            val fileInfo = getFileInfo(uri) ?: return null
            val transferId = UUID.randomUUID().toString()

            // Create transfer record
            val transfer = FileTransfer(
                id = transferId,
                fileName = fileInfo.name,
                fileSize = fileInfo.size,
                direction = TransferDirection.OUTGOING,
                status = TransferStatus.PENDING,
                progress = 0f
            )

            updateTransfer(transfer)

            // Calculate checksum
            val checksum = calculateChecksum(uri)

            // Send transfer request
            val request = FileTransferRequestPayload(
                transferId = transferId,
                fileName = fileInfo.name,
                fileSize = fileInfo.size,
                mimeType = fileInfo.mimeType,
                checksum = checksum
            )

            val message = ProtocolMessage(
                type = MessageType.FILE_TRANSFER_REQUEST,
                payload = protocolJson.encodeToJsonElement(request)
            )

            // Store pending transfer
            pendingTransfers[transferId] = PendingTransfer(
                transferId = transferId,
                uri = uri,
                fileInfo = fileInfo
            )

            connectionManager.broadcast(message)
            Log.i(TAG, "File transfer request sent: ${fileInfo.name}")

            transferId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file", e)
            null
        }
    }

    private suspend fun handleTransferRequest(sessionId: String, message: ProtocolMessage) {
        val request = protocolJson.decodeFromJsonElement<FileTransferRequestPayload>(message.payload)

        Log.i(TAG, "Received transfer request: ${request.fileName}")

        // Create transfer record
        val transfer = FileTransfer(
            id = request.transferId,
            fileName = request.fileName,
            fileSize = request.fileSize,
            direction = TransferDirection.INCOMING,
            status = TransferStatus.PENDING,
            progress = 0f
        )

        updateTransfer(transfer)

        // Auto-accept (could show UI prompt)
        acceptTransfer(sessionId, request.transferId)
    }

    private suspend fun acceptTransfer(sessionId: String, transferId: String) {
        val acceptMessage = ProtocolMessage(
            type = MessageType.FILE_TRANSFER_ACCEPT,
            payload = protocolJson.encodeToJsonElement(mapOf("transferId" to transferId))
        )

        connectionManager.sendToSession(sessionId, acceptMessage)
        updateTransferStatus(transferId, TransferStatus.IN_PROGRESS)
    }

    private suspend fun handleTransferAccepted(sessionId: String, message: ProtocolMessage) {
        val payload = protocolJson.decodeFromJsonElement<Map<String, String>>(message.payload)
        val transferId = payload["transferId"] ?: return

        val pending = pendingTransfers[transferId] ?: return

        Log.i(TAG, "Transfer accepted: $transferId")
        updateTransferStatus(transferId, TransferStatus.IN_PROGRESS)

        // Start uploading file
        scope.launch {
            uploadFile(sessionId, pending)
        }
    }

    private suspend fun uploadFile(sessionId: String, pending: PendingTransfer) {
        try {
            context.contentResolver.openInputStream(pending.uri)?.use { inputStream ->
                val totalBytes = pending.fileInfo.size
                var bytesTransferred = 0L

                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                
                var sequence = 0
                val totalChunks = Math.ceil(totalBytes.toDouble() / CHUNK_SIZE).toInt()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = buffer.copyOfRange(0, bytesRead)
                    val encodedData = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                    
                    val digest = MessageDigest.getInstance("SHA-256")
                    val checksumBytes = digest.digest(chunk)
                    val checksum = checksumBytes.joinToString("") { "%02x".format(it) }

                    val chunkPayload = FileChunkPayload(
                        transferId = pending.transferId,
                        sequence = sequence,
                        totalChunks = totalChunks,
                        data = encodedData,
                        checksum = checksum
                    )
                    
                    val chunkMessage = ProtocolMessage(
                        type = MessageType.FILE_CHUNK,
                        payload = protocolJson.encodeToJsonElement(chunkPayload)
                    )
                    
                    connectionManager.sendToSession(sessionId, chunkMessage)

                    bytesTransferred += bytesRead
                    val progress = bytesTransferred.toFloat() / totalBytes

                    // Update progress
                    updateTransferProgress(pending.transferId, progress, bytesTransferred)

                    // Send progress update
                    val progressPayload = FileTransferProgressPayload(
                        transferId = pending.transferId,
                        bytesTransferred = bytesTransferred,
                        totalBytes = totalBytes,
                        progress = progress
                    )

                    val progressMessage = ProtocolMessage(
                        type = MessageType.FILE_TRANSFER_PROGRESS,
                        payload = protocolJson.encodeToJsonElement(progressPayload)
                    )

                    connectionManager.sendToSession(sessionId, progressMessage)

                    sequence++
                }

                // Transfer complete
                val completePayload = FileTransferCompletePayload(
                    transferId = pending.transferId,
                    success = true
                )

                val completeMessage = ProtocolMessage(
                    type = MessageType.FILE_TRANSFER_COMPLETE,
                    payload = protocolJson.encodeToJsonElement(completePayload)
                )

                connectionManager.sendToSession(sessionId, completeMessage)
                updateTransferStatus(pending.transferId, TransferStatus.COMPLETED)
                pendingTransfers.remove(pending.transferId)

                Log.i(TAG, "File transfer completed: ${pending.fileInfo.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "File upload failed", e)
            updateTransferStatus(pending.transferId, TransferStatus.FAILED)

            val errorPayload = FileTransferCompletePayload(
                transferId = pending.transferId,
                success = false,
                errorMessage = e.message
            )

            val errorMessage = ProtocolMessage(
                type = MessageType.FILE_TRANSFER_ERROR,
                payload = protocolJson.encodeToJsonElement(errorPayload)
            )

            connectionManager.broadcast(errorMessage)
        }
    }

    private fun handleTransferRejected(message: ProtocolMessage) {
        val payload = protocolJson.decodeFromJsonElement<Map<String, String>>(message.payload)
        val transferId = payload["transferId"] ?: return

        updateTransferStatus(transferId, TransferStatus.REJECTED)
        pendingTransfers.remove(transferId)
    }

    private fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                cursor.moveToFirst()

                val name = cursor.getString(nameIndex)
                val size = cursor.getLong(sizeIndex)
                val mimeType = context.contentResolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        name.substringAfterLast('.', "")
                    ) ?: "application/octet-stream"

                FileInfo(name, size, mimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info", e)
            null
        }
    }

    private fun calculateChecksum(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate checksum", e)
            ""
        }
    }

    private fun updateTransfer(transfer: FileTransfer) {
        val list = _activeTransfers.value.toMutableList()
        list.removeAll { it.id == transfer.id }
        list.add(transfer)
        _activeTransfers.value = list
    }

    private fun updateTransferStatus(transferId: String, status: TransferStatus) {
        val list = _activeTransfers.value.toMutableList()
        val index = list.indexOfFirst { it.id == transferId }
        if (index >= 0) {
            list[index] = list[index].copy(status = status)
            _activeTransfers.value = list
        }
    }

    private fun updateTransferProgress(transferId: String, progress: Float, bytesTransferred: Long) {
        val list = _activeTransfers.value.toMutableList()
        val index = list.indexOfFirst { it.id == transferId }
        if (index >= 0) {
            list[index] = list[index].copy(
                progress = progress,
                bytesTransferred = bytesTransferred
            )
            _activeTransfers.value = list
        }
    }
}

data class FileInfo(
    val name: String,
    val size: Long,
    val mimeType: String
)

data class FileTransfer(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Float,
    val bytesTransferred: Long = 0
)

enum class TransferDirection {
    INCOMING, OUTGOING
}

enum class TransferStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, REJECTED, CANCELLED
}

data class PendingTransfer(
    val transferId: String,
    val uri: Uri,
    val fileInfo: FileInfo
)
