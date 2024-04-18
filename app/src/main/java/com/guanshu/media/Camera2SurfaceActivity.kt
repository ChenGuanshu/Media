package com.guanshu.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.guanshu.media.application.GlobalDependency
import com.guanshu.media.view.Camera2SurfaceView


private const val TAG = "Camera2SurfaceActivity"

class Camera2SurfaceActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: Camera2SurfaceView
    private var surfaceHolder: SurfaceHolder? = null
    private val camera2 get() = GlobalDependency.camera2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_to_surface)
        surfaceView = findViewById(R.id.surface_camera)
        surfaceView.holder.addCallback(this)

        val imageView = findViewById<ImageView>(R.id.surface_camera_image)
        findViewById<View>(R.id.surface_camera_button).setOnClickListener {
            Toast.makeText(this, "Capture", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "capture")

            camera2.takePicture { image ->
                val buffer = image.planes[0].buffer;
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val flipBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postScale(-1f, 1f) },
                    false
                )
                Log.i(TAG, "capture done: update imageview")
                imageView.post {
                    imageView.setImageBitmap(flipBitmap)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        this.surfaceHolder = holder
        Log.i(TAG, "surfaceCreated")

        camera2.openCamera(surfaceView.width, surfaceView.height) { newWidth, newHeight ->
            surfaceView.updateSize(newWidth, newHeight)
        }
        camera2.startPreview(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.surfaceHolder = holder
        Log.i(TAG, "surfaceChanged,format=$format,resolution=$width*$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        this.surfaceHolder = holder
        Log.i(TAG, "surfaceDestroyed")

        camera2.stopPreview()
        camera2.closeCamera()
    }
}