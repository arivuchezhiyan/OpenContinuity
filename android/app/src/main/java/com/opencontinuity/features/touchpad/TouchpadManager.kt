package com.opencontinuity.features.touchpad

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Touchpad Manager - captures touch gestures and sends them to PC as mouse events.
 *
 * Input optimization:
 *  - Events are coalesced into 60 Hz batches (one send per ~16 ms frame).
 *  - Dead-zone filter: micro-movements smaller than DEAD_ZONE pixels are ignored.
 *  - The flush coroutine fires only when there is pending movement, keeping the
 *    channel quiet during pauses and reducing unnecessary radio wake-ups.
 */
class TouchpadManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "TouchpadManager"
        /**
         * Send raw touch-pixel deltas to Windows (no pre-scale).
         * Windows-side pointer acceleration maps them to screen pixels naturally —
         * slow → precise, fast → covers screen — just like a real laptop trackpad.
         * Previously 3.5 here × 2.0 on Windows = brutal 7× flat multiplier (jitter/overshoot).
         */
        private const val SENSITIVITY = 1.0f
        private const val TAP_THRESHOLD_MS = 200
        private const val TAP_DISTANCE_THRESHOLD = 20f
        private const val LONG_PRESS_THRESHOLD_MS = 500
        /** Minimum movement (raw pixels) before a delta is forwarded — smaller = more responsive */
        private const val DEAD_ZONE = 0.3f
        /** 120 Hz batching: 1000 / 120 = 8 ms — halves input latency vs previous 60 Hz */
        private const val FRAME_MS = 8L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isEnabled = false
    private var lastX = 0f
    private var lastY = 0f
    private var touchStartTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var fingerCount = 0
    private var isDragging = false

    // Accumulated deltas for coalescing within the current 16 ms frame
    @Volatile private var pendingDeltaX = 0f
    @Volatile private var pendingDeltaY = 0f
    @Volatile private var pendingScrollDelta = 0f
    @Volatile private var hasPendingMove = false
    @Volatile private var hasPendingScroll = false

    // Flush coroutine — started once, runs at 60 Hz while enabled
    private var flushJob: Job? = null

    fun start() {
        isEnabled = true
        startFlushLoop()
        Log.i(TAG, "Touchpad manager started (120 Hz batching, dead-zone=${DEAD_ZONE}px)")
    }

    fun stop() {
        isEnabled = false
        flushJob?.cancel()
        flushJob = null
        scope.cancel()
        Log.i(TAG, "Touchpad manager stopped")
    }

    // ─────────────────────────── 120 Hz flush loop ──────────────────────────

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive && isEnabled) {
                delay(FRAME_MS)

                // Read+reset move accumulators inside the coroutine — no extra launch() overhead
                if (hasPendingMove) {
                    val dx = pendingDeltaX; pendingDeltaX = 0f
                    val dy = pendingDeltaY; pendingDeltaY = 0f
                    hasPendingMove = false
                    try {
                        connectionManager.broadcast(
                            ProtocolMessage(
                                type = MessageType.TOUCHPAD_EVENT,
                                payload = protocolJson.encodeToJsonElement(
                                    TouchpadEventPayload(
                                        eventType = TouchpadEventType.MOVE,
                                        deltaX = dx,
                                        deltaY = dy,
                                        fingers = 1
                                    )
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send touchpad move", e)
                    }
                }

                if (hasPendingScroll) {
                    val sd = pendingScrollDelta; pendingScrollDelta = 0f
                    hasPendingScroll = false
                    try {
                        connectionManager.broadcast(
                            ProtocolMessage(
                                type = MessageType.TOUCHPAD_EVENT,
                                payload = protocolJson.encodeToJsonElement(
                                    TouchpadEventPayload(
                                        eventType = TouchpadEventType.SCROLL,
                                        scrollDelta = sd,
                                        fingers = 2
                                    )
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send touchpad scroll", e)
                    }
                }
            }
        }
    }

    /**
     * Process touch events from the touchpad UI
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        fingerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val rawDeltaX = event.x - lastX
                val rawDeltaY = event.y - lastY

                if (fingerCount == 1) {
                    // Apply sensitivity factor (1.0 = raw) and dead-zone filter
                    val scaledDX = rawDeltaX * SENSITIVITY
                    val scaledDY = rawDeltaY * SENSITIVITY
                    if (kotlin.math.abs(rawDeltaX) > DEAD_ZONE || kotlin.math.abs(rawDeltaY) > DEAD_ZONE) {
                        // Coalesce into pending delta — the 120 Hz flush loop batches sends
                        pendingDeltaX += scaledDX
                        pendingDeltaY += scaledDY
                        hasPendingMove = true
                    }
                } else if (fingerCount == 2) {
                    // Scroll: accumulate vertical scroll delta (raw pixels, Windows scales)
                    if (kotlin.math.abs(rawDeltaY) > DEAD_ZONE) {
                        pendingScrollDelta -= rawDeltaY  // Invert for natural scrolling
                        hasPendingScroll = true
                    }
                }

                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val touchDuration = System.currentTimeMillis() - touchStartTime
                val touchDistance = kotlin.math.hypot(
                    (event.x - touchStartX).toDouble(),
                    (event.y - touchStartY).toDouble()
                ).toFloat()

                if (touchDistance < TAP_DISTANCE_THRESHOLD) {
                    if (touchDuration < TAP_THRESHOLD_MS) {
                        // Tap - left click
                        sendTouchpadEvent(
                            TouchpadEventPayload(
                                eventType = TouchpadEventType.CLICK,
                                fingers = fingerCount
                            )
                        )
                    } else if (touchDuration >= LONG_PRESS_THRESHOLD_MS) {
                        // Long press - right click
                        sendTouchpadEvent(
                            TouchpadEventPayload(
                                eventType = TouchpadEventType.RIGHT_CLICK,
                                fingers = 1
                            )
                        )
                    }
                }

                if (isDragging) {
                    sendTouchpadEvent(
                        TouchpadEventPayload(
                            eventType = TouchpadEventType.DRAG_END,
                            fingers = 1
                        )
                    )
                    isDragging = false
                }

                fingerCount = 0
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    // Two finger tap - right click
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - touchStartTime < TAP_THRESHOLD_MS * 2) {
                        sendTouchpadEvent(
                            TouchpadEventPayload(
                                eventType = TouchpadEventType.RIGHT_CLICK,
                                fingers = 2
                            )
                        )
                    }
                }
            }
        }

        return true
    }

    /**
     * Start drag mode (called on double-tap and hold)
     */
    fun startDrag() {
        isDragging = true
        sendTouchpadEvent(
            TouchpadEventPayload(
                eventType = TouchpadEventType.DRAG_START,
                fingers = 1
            )
        )
    }

    private fun sendTouchpadEvent(payload: TouchpadEventPayload) {
        scope.launch {
            try {
                val message = ProtocolMessage(
                    type = MessageType.TOUCHPAD_EVENT,
                    payload = protocolJson.encodeToJsonElement(payload)
                )

                connectionManager.broadcast(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send touchpad event", e)
            }
        }
    }
}
