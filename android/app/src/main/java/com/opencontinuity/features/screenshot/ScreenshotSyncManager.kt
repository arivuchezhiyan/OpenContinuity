package com.opencontinuity.features.screenshot

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.util.UUID

/**
 * Watches the system Screenshots folder and pushes new captures to the connected PC.
 */
class ScreenshotSyncManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "ScreenshotSync"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observer: ScreenshotFileObserver? = null
    private val recentIds = LinkedHashSet<String>()

    fun start() {
        if (observer != null) return

        connectionManager.registerHandler(MessageType.SCREENSHOT_REQUEST) { _, message ->
            scope.launch { handleScreenshotRequest(message) }
        }

        val dirs = screenshotDirectories()
        if (dirs.isEmpty()) {
            Log.w(TAG, "No screenshot directories found")
            return
        }

        observer = ScreenshotFileObserver(dirs) { file ->
            scope.launch { onNewScreenshot(file) }
        }.also { it.startWatching() }

        Log.i(TAG, "Screenshot sync started (${dirs.size} dirs)")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        scope.coroutineContext.cancelChildren()
    }

    private fun screenshotDirectories(): List<File> {
        val candidates = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                ?.let { File(it, "Screenshots") },
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                ?.let { File(it, "Screenshots") }
        )
        return candidates.filterNotNull().filter { it.exists() || it.mkdirs() }
    }

    private suspend fun onNewScreenshot(file: File) {
        if (!file.isFile || !file.name.lowercase().endsWith(".png") &&
            !file.name.lowercase().endsWith(".jpg") &&
            !file.name.lowercase().endsWith(".jpeg")
        ) {
            return
        }

        val key = "${file.absolutePath}:${file.lastModified()}"
        if (!recentIds.add(key)) return
        if (recentIds.size > 50) {
            recentIds.remove(recentIds.first())
        }

        delay(400) // allow write to finish

        try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return

            val screenshotId = UUID.randomUUID().toString()
            val payload = ScreenshotAvailablePayload(
                screenshotId = screenshotId,
                fileName = file.name,
                timestamp = System.currentTimeMillis(),
                imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            )

            connectionManager.broadcast(
                ProtocolMessage(
                    type = MessageType.SCREENSHOT_AVAILABLE,
                    payload = protocolJson.encodeToJsonElement(payload)
                )
            )
            Log.i(TAG, "Sent screenshot: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send screenshot", e)
        }
    }

    private suspend fun handleScreenshotRequest(message: ProtocolMessage) {
        val request = protocolJson.decodeFromJsonElement<ScreenshotRequestPayload>(message.payload)
        if (request.imageBase64 != null) return

        val dirs = screenshotDirectories()
        val match = dirs.asSequence()
            .flatMap { dir -> dir.listFiles()?.asSequence() ?: emptySequence() }
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }
            ?: return

        try {
            val bytes = match.readBytes()
            val response = ScreenshotRequestPayload(
                screenshotId = request.screenshotId,
                imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                fileName = match.name
            )
            connectionManager.broadcast(
                ProtocolMessage(
                    type = MessageType.SCREENSHOT_REQUEST,
                    payload = protocolJson.encodeToJsonElement(response)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fulfill screenshot request", e)
        }
    }
}
