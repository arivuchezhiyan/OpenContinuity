package com.opencontinuity.features.screenshot

import android.os.FileObserver
import android.util.Log
import java.io.File

/**
 * Observes one or more directories for new screenshot files.
 */
class ScreenshotFileObserver(
    private val directories: List<File>,
    private val onScreenshot: (File) -> Unit
) {
    companion object {
        private const val TAG = "ScreenshotObserver"
        private val EVENT_MASK = CREATE or MOVED_TO or CLOSE_WRITE
    }

    private val observers = directories.map { dir ->
        object : FileObserver(dir.absolutePath, EVENT_MASK) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val file = File(dir, path)
                if (file.isFile) {
                    onScreenshot(file)
                }
            }
        }
    }

    fun startWatching() {
        observers.forEach {
            try {
                it.startWatching()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to watch directory", e)
            }
        }
    }

    fun stopWatching() {
        observers.forEach {
            try {
                it.stopWatching()
            } catch (_: Exception) {
            }
        }
    }
}
