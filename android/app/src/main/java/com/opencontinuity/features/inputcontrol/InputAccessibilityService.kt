package com.opencontinuity.features.inputcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Input Accessibility Service - injects touch/input events for remote control
 */
class InputAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InputAccessibilityService"
        var instance: InputAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        // Register handler for input events
        val app = application as? OpenContinuityApp
        app?.connectionManager?.registerHandler(MessageType.INPUT_EVENT) { _, message ->
            handleInputEvent(message)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for input injection
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }

    private fun handleInputEvent(message: ProtocolMessage) {
        try {
            val event = protocolJson.decodeFromJsonElement<InputEventPayload>(message.payload)

            when (event.eventType) {
                InputEventType.TAP -> {
                    event.x?.let { x ->
                        event.y?.let { y ->
                            performTap(x, y)
                        }
                    }
                }
                InputEventType.LONG_PRESS -> {
                    event.x?.let { x ->
                        event.y?.let { y ->
                            performLongPress(x, y)
                        }
                    }
                }
                InputEventType.SWIPE -> {
                    // Requires start and end coordinates
                    // Would need additional payload fields
                }
                InputEventType.SCROLL -> {
                    event.scrollDelta?.let { delta ->
                        event.x?.let { x ->
                            event.y?.let { y ->
                                performScroll(x, y, delta)
                            }
                        }
                    }
                }
                InputEventType.KEY -> {
                    // Key events are more complex
                    Log.d(TAG, "Key event: ${event.keyCode}")
                }
                InputEventType.TEXT -> {
                    event.text?.let { text ->
                        performTextInput(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle input event", e)
        }
    }

    fun performTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gesture dispatch requires API 24+")
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled")
            }
        }, null)
    }

    fun performLongPress(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Long press completed at ($x, $y)")
            }
        }, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed")
            }
        }, null)
    }

    fun performScroll(x: Float, y: Float, deltaY: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val endY = y + deltaY
        performSwipe(x, y, x, endY, 200)
    }

    private fun performTextInput(text: String) {
        // Text input requires finding a focused text field
        // This is a simplified implementation
        Log.d(TAG, "Text input requested: $text")

        // Could use clipboard + paste approach
        // Or dispatch key events for each character
    }
}
