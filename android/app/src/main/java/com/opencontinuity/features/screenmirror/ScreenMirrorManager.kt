package com.opencontinuity.features.screenmirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screen Mirror Manager - captures screen frames for streaming
 */
class ScreenMirrorManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenMirrorManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }

    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val _isMirroring = MutableStateFlow(false)
    val isMirroring: StateFlow<Boolean> = _isMirroring.asStateFlow()

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var frameCallback: ((Bitmap, Long) -> Unit)? = null

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    fun setFrameCallback(callback: (Bitmap, Long) -> Unit) {
        frameCallback = callback
    }

    /**
     * Get the intent to request screen capture permission
     * Should be started with startActivityForResult
     */
    fun getScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    /**
     * Start screen mirroring with the result from permission request
     */
    fun startMirroring(resultCode: Int, data: Intent) {
        if (_isMirroring.value) {
            Log.w(TAG, "Already mirroring")
            return
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection")
            return
        }

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                stopMirroring()
            }
        }, null)

        createVirtualDisplay()
        _isMirroring.value = true
        Log.i(TAG, "Screen mirroring started")
    }

    fun stopMirroring() {
        _isMirroring.value = false

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        Log.i(TAG, "Screen mirroring stopped")
    }

    private fun createVirtualDisplay() {
        // Scale down for performance
        val scaleFactor = 0.5f
        val width = (screenWidth * scaleFactor).toInt()
        val height = (screenHeight * scaleFactor).toInt()

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop to actual size
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (bitmap != croppedBitmap) {
                    bitmap.recycle()
                }

                frameCallback?.invoke(croppedBitmap, image.timestamp)
            } finally {
                image.close()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenMirror",
            width,
            height,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    fun getScreenDimensions(): Pair<Int, Int> = Pair(screenWidth, screenHeight)
}
