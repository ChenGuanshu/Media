package com.guanshu.media.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import com.google.common.base.Optional
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.postOrRun
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.lang.Exception
import java.util.ArrayList
import java.util.Arrays
import java.util.Scanner
import java.util.UUID
import kotlin.math.abs

private const val TAG = "Camera2"

/**
 * TODO refactor the camera2
 */
class Camera2(private val context: Context) {

    private var camera = CameraCharacteristics.LENS_FACING_BACK
    private var deviceOrientation = 0
    private var orientation = 0

    // init when camera is opened
    private val cameraDeviceSubject = BehaviorSubject.create<Optional<CameraDevice>>()
    private var cameraDeviceCallback: CameraDeviceCallback? = null

    // init when preview is enabled
    private val captureSessionSubject = BehaviorSubject.create<Optional<CameraCaptureSession>>()
    private var previewSize = Size(1080, 1920)
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewDisposable: Disposable? = null
    private var previewSurface: Surface? = null

    private var captureDisposable: Disposable? = null
    private var imageReader: ImageReader? = null
    private var imageCallback: ((Image) -> Unit)? = null

    private val cameraManager: CameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val cameraHandler: Handler by lazy {
        val thread = HandlerThread("Camera2Handler")
        thread.start()
        Handler(thread.looper)
    }

    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            this@Camera2.deviceOrientation = orientation
        }
    }

    inner class CameraDeviceCallback(
        val sessionId: String,
    ) : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Logger.i(TAG, "onOpened:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.of(camera))
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Logger.i(TAG, "onClosed:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }

        override fun onDisconnected(camera: CameraDevice) {
            Logger.i(TAG, "onDisconnected:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Logger.e(TAG, "onError $camera, $error, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(width: Int, height: Int, onCameraSizeChosen: (Int, Int) -> Unit) {
        cameraHandler.postOrRun {
            Logger.i(TAG, "open camera $width*$height")
            val oldCameraDevice = cameraDeviceSubject.value?.orNull()
            if (oldCameraDevice != null) {
                Logger.w(TAG, "openCamera: already opened - close")
                closeCamera()
            } else if (cameraDeviceCallback != null) {
                Logger.w(
                    TAG,
                    "there was a request to open the camera ${cameraDeviceCallback?.sessionId}"
                )
                // TODO wait for open or ?
            }

            val cameraCharacteristics = cameraManager.getCameraCharacteristics(camera.toString())
            val map = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            orientation = getOrientation(cameraCharacteristics, deviceOrientation)
            previewSize = chooseSize(sizes, width, height, orientation)
            Logger.i(TAG, "pick=$previewSize, support sizes = ${sizes.joinToString(",")}")

            orientationEventListener.enable()
            cameraDeviceCallback = CameraDeviceCallback(UUID.randomUUID().toString())
            cameraManager.openCamera(camera.toString(), cameraDeviceCallback!!, cameraHandler)

            onCameraSizeChosen(previewSize.width, previewSize.height)
        }
    }

    fun closeCamera() {
        cameraHandler.post {
            Logger.i(TAG, "close camera")
            orientationEventListener.disable()

            var disposable: Disposable? = null
            disposable = cameraDeviceSubject
                .take(1)
                .doOnNext {
                    it.orNull()?.close()
                }.subscribe({
                    disposable?.dispose()
                    cameraDeviceSubject.onNext(Optional.absent())
                    Logger.i(TAG, "close camera done")
                }, {
                    disposable?.dispose()
                    cameraDeviceSubject.onNext(Optional.absent())
                    Logger.e(TAG, "close camera failed", it)
                })
            cameraDeviceCallback = null
        }
    }

    fun startPreview(surface: Surface) {
        cameraHandler.postOrRun {
            Logger.i(TAG, "startPreview")
            previewSurface = surface
            previewDisposable = cameraDeviceSubject
                .subscribe {
                    val cameraDevice = it.orNull()
                    if (cameraDevice == null) {
                        Logger.w(TAG, "camera device is null")
                        return@subscribe
                    }

                    imageReader = ImageReader.newInstance(
                        previewSize.width,
                        previewSize.height,
                        ImageFormat.JPEG,
                        2
                    )
                    imageReader!!.setOnImageAvailableListener({
                        Logger.i(TAG, "on image available")
                        try {
                            val image = it.acquireNextImage()
                            imageCallback?.run { this(image) }
                            imageCallback = null
                        } catch (e: Exception) {
                            Logger.e(TAG, "image failed", e)
                        }
                    }, cameraHandler)

                    previewRequestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewRequestBuilder!!.addTarget(previewSurface!!)
//                    previewRequestBuilder!!.addTarget(imageReader!!.surface)

                    cameraDevice.createCaptureSession(
                        listOf(previewSurface!!, imageReader!!.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (previewDisposable == null || previewDisposable?.isDisposed == true) {
                                    Logger.i(TAG, "startPreview: canceled")
                                    return
                                }
                                Logger.i(TAG, "startPreview: onConfigured")

                                previewRequestBuilder!!.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                previewRequestBuilder!!.set(
                                    CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                )
//                                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)
                                val request = previewRequestBuilder!!.build();
                                session.setRepeatingBurst(listOf(request), null, cameraHandler)
                                captureSessionSubject.onNext(Optional.of(session))
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Logger.e(TAG, "startPreview: onConfigureFailed")
                                captureSessionSubject.onNext(Optional.absent())
                            }
                        }, cameraHandler
                    )
                }
        }
    }

    fun stopPreview() {
        cameraHandler.postOrRun {
            Logger.i(TAG, "stopPreview")
            previewDisposable?.dispose()
            previewDisposable = null


            var disposable: Disposable? = null
            disposable = captureSessionSubject
                .take(1)
                .subscribe {
                    it.orNull()?.stopRepeating()

                    disposable?.dispose()
                    captureSessionSubject.onNext(Optional.absent())
                    Logger.i(TAG, "stopPreview done")
                }
        }
    }

    fun takePicture(callback: (Image) -> Unit) {
        cameraHandler.postOrRun {
            Logger.i(TAG, "take picture: start")
            imageCallback = callback
            val captureBuilder = cameraDeviceSubject
                .take(1)
                .map {
                    val device =
                        it.orNull() ?: throw RuntimeException("takePicture: device is null")
                    val captureBuilder = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE
                    )

                    captureBuilder.addTarget(previewSurface!!)
                    captureBuilder.addTarget(imageReader!!.surface)

                    captureBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    captureBuilder.set(
                        CaptureRequest.JPEG_ORIENTATION,
                        orientation,
                    )
                    captureBuilder
                }

            Logger.i(TAG, "take picture: request")
            previewDisposable = captureBuilder.flatMapCompletable { builder ->
                captureSessionSubject.take(1).flatMapCompletable { optionalSession ->
                    val session = optionalSession.get()

                    Logger.i(TAG, "take picture: stop preview")
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                        session.stopRepeating()
                    } else {
                        session.stopRepeating()
                        session.abortCaptures()
                    }

                    session.capture(
                        builder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                super.onCaptureCompleted(session, request, result)
                                Logger.i(TAG, "capture result = $result")

                                session.setRepeatingBurst(
                                    listOf(previewRequestBuilder!!.build()),
                                    null,
                                    cameraHandler
                                )
                            }
                        },
                        cameraHandler,
                    )
                    Completable.complete()
                }
            }.subscribe({
                Logger.i(TAG, "start capture: done")
            }, {
                Logger.e(TAG, "capture failed", it)
            })
        }
    }

    private fun chooseSize(sizes: Array<Size>, width: Int, height: Int, orientation: Int): Size {
        Logger.i(TAG, "choose size for $width, $height, $orientation")
        val options = arrayListOf<Size>()
        sizes.forEachIndexed { index, size ->
            val thisSize =
                if (orientation == 90 || orientation == 270) Size(size.height, size.width) else size
            if (thisSize.width >= width && thisSize.height >= height) {
                options.add(thisSize)
            }
        }

        val viewAspectRatio = width.toFloat() / height
        var minAspectRatioDiff = Float.MAX_VALUE
        var pick: Size? = null
        options.forEachIndexed { index, size ->
            val optionAspectRatio = size.width.toFloat() / size.height
            val diff = abs(optionAspectRatio - viewAspectRatio)
            if (diff < minAspectRatioDiff) {
                minAspectRatioDiff = diff
                pick = size
            }
        }

        return pick ?: throw RuntimeException("size not found")
    }

    private fun getOrientation(
        cameraCharacteristics: CameraCharacteristics,
        deviceOrientation: Int
    ): Int {
        if (deviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) return 0
        val sensorOrientation =
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        // Round device orientation to a multiple of 90
        var deviceOrientation = (deviceOrientation + 45) / 90 * 90
        // Reverse device orientation for front-facing cameras
        val facingFront =
            cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics
                .LENS_FACING_FRONT
        if (facingFront) deviceOrientation = -deviceOrientation
        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        val retOrientation = (sensorOrientation + deviceOrientation + 360) % 360
        Logger.i(TAG, "retOrientation: $retOrientation")
        return retOrientation
    }
}