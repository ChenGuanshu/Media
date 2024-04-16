package com.guanshu.media.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.SurfaceHolder
import com.google.common.base.Optional
import com.guanshu.media.utils.postOrRun
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.UUID

private const val TAG = "Camera2"

class Camera2(private val context: Context) {

    private var orientation = 0
    private var camera = CameraCharacteristics.LENS_FACING_BACK

    // init when camera is opened
    private val cameraDeviceSubject = BehaviorSubject.create<Optional<CameraDevice>>()
    private var cameraDeviceCallback: CameraDeviceCallback? = null

    // init when preview is enabled
    private val captureSessionSubject = BehaviorSubject.create<Optional<CameraCaptureSession>>()
    private var previewDisposable: Disposable? = null

    private val cameraManager: CameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val cameraHandler: Handler by lazy {
        val thread = HandlerThread("Camera2Handler")
        thread.start()
        Handler(thread.looper)
    }

    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            this@Camera2.orientation = orientation
        }
    }

    inner class CameraDeviceCallback(
        val sessionId: String,
    ) : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "onOpened:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.of(camera))
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Log.i(TAG, "onClosed:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.i(TAG, "onDisconnected:$camera, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "onError $camera, $error, $sessionId")
            cameraDeviceSubject.onNext(Optional.absent())
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(width: Int, height: Int, onCameraSizeChosen: (Int, Int) -> Unit) {
        cameraHandler.postOrRun {
            Log.d(TAG, "open camera $width*$height")
            val oldCameraDevice = cameraDeviceSubject.value?.orNull()
            if (oldCameraDevice != null) {
                Log.w(TAG, "openCamera: already opened - close")
                closeCamera()
            } else if (cameraDeviceCallback != null) {
                Log.w(
                    TAG,
                    "there was a request to open the camera ${cameraDeviceCallback?.sessionId}"
                )
                // TODO wait for open or ?
            }

            val cameraCharacteristics = cameraManager.getCameraCharacteristics(camera.toString())
            val map = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(SurfaceHolder::class.java)
            val size = chooseSize(sizes, width, height)
            Log.d(TAG, "pick=$size, support sizes = ${sizes.joinToString(",")}")

            orientationEventListener.enable()
            cameraDeviceCallback = CameraDeviceCallback(UUID.randomUUID().toString())
            cameraManager.openCamera(camera.toString(), cameraDeviceCallback!!, cameraHandler)

            onCameraSizeChosen(size.height, size.width)
        }
    }

    fun closeCamera() {
        cameraHandler.post {
            Log.d(TAG, "close camera")
            orientationEventListener.disable()

            var disposable: Disposable? = null
            disposable = cameraDeviceSubject
                .take(1)
                .doOnNext {
                    it.orNull()?.close()
                }.subscribe({
                    disposable?.dispose()
                    cameraDeviceSubject.onNext(Optional.absent())
                    Log.d(TAG, "close camera done")
                }, {
                    disposable?.dispose()
                    cameraDeviceSubject.onNext(Optional.absent())
                    Log.e(TAG, "close camera failed", it)
                })
            cameraDeviceCallback = null
        }
    }

    fun startPreview(surfaceHolder: SurfaceHolder) {
        cameraHandler.postOrRun {
            Log.d(TAG, "startPreview")
            previewDisposable = cameraDeviceSubject
                .subscribe {
                    val cameraDevice = it.orNull()
                    if (cameraDevice == null) {
                        Log.w(TAG, "camera device is null")
                        return@subscribe
                    }

                    val requestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(surfaceHolder.surface)
                    cameraDevice.createCaptureSession(
                        listOf(surfaceHolder.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (previewDisposable == null || previewDisposable?.isDisposed == true) {
                                    Log.d(TAG, "startPreview: canceled")
                                    return
                                }
                                Log.d(TAG, "startPreview: onConfigured")

                                requestBuilder.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                requestBuilder.set(
                                    CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                )
                                val request = requestBuilder.build();
                                session.setRepeatingBurst(listOf(request), null, cameraHandler)
                                captureSessionSubject.onNext(Optional.of(session))
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "startPreview: onConfigureFailed")
                                captureSessionSubject.onNext(Optional.absent())
                            }
                        }, cameraHandler
                    )
                }
        }
    }

    fun stopPreview() {
        cameraHandler.postOrRun {
            Log.d(TAG, "stopPreview")
            previewDisposable?.dispose()
            previewDisposable = null


            var disposable: Disposable? = null
            disposable = captureSessionSubject
                .take(1)
                .subscribe {
                    it.orNull()?.stopRepeating()

                    disposable?.dispose()
                    captureSessionSubject.onNext(Optional.absent())
                    Log.d(TAG, "stopPreview done")
                }
        }
    }

    private fun chooseSize(sizes: Array<Size>, width: Int, height: Int): Size {
        sizes.forEach {
            // 90 rotated
            if (it.width < height) {
                return it
            }
        }
        return sizes.last()
    }
}