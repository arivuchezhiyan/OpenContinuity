package com.opencontinuity.features.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera Manager - handles camera capture for webcam streaming
 */
class CameraStreamManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraStreamManager"
        val SUPPORTED_RESOLUTIONS = listOf(
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480)
        )
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentResolution = MutableStateFlow(SUPPORTED_RESOLUTIONS[1]) // Default 720p
    val currentResolution: StateFlow<Size> = _currentResolution.asStateFlow()

    private var frameCallback: ((ByteArray, Long) -> Unit)? = null

    fun setFrameCallback(callback: (ByteArray, Long) -> Unit) {
        frameCallback = callback
    }

    fun startStream(cameraId: String = getFrontCameraId() ?: getBackCameraId() ?: "0") {
        if (_isStreaming.value) {
            Log.w(TAG, "Already streaming")
            return
        }

        startBackgroundThread()
        openCamera(cameraId)
    }

    fun stopStream() {
        _isStreaming.value = false
        closeCamera()
        stopBackgroundThread()
    }

    fun setResolution(resolution: Size) {
        if (resolution in SUPPORTED_RESOLUTIONS) {
            _currentResolution.value = resolution
            if (_isStreaming.value) {
                // Restart stream with new resolution
                stopStream()
                startStream()
            }
        }
    }

    private fun getFrontCameraId(): String? {
        return cameraManager.cameraIdList.find { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun getBackCameraId(): String? {
        return cameraManager.cameraIdList.find { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun openCamera(cameraId: String) {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Camera opened: $cameraId")
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val resolution = _currentResolution.value

        imageReader = ImageReader.newInstance(
            resolution.width,
            resolution.height,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    frameCallback?.invoke(bytes, image.timestamp)
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }

        val surface = imageReader!!.surface

        try {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview(surface)
                        _isStreaming.value = true
                        Log.i(TAG, "Capture session configured")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun startPreview(surface: Surface) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return

        try {
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            session.setRepeatingRequest(
                captureRequest.build(),
                null,
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        imageReader?.close()
        imageReader = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to stop background thread", e)
        }
    }

    fun getAvailableCameras(): List<CameraInfo> {
        return cameraManager.cameraIdList.map { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            CameraInfo(
                id = id,
                name = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back Camera"
                    else -> "Camera $id"
                },
                isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
            )
        }
    }
}

data class CameraInfo(
    val id: String,
    val name: String,
    val isFront: Boolean
)
