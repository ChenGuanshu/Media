package com.guanshu.media

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.guanshu.media.application.GlobalDependency
import com.guanshu.media.view.Camera2GlSurfaceView

private const val TAG = "Camera2GlSurfaceActivity"

class Camera2GlSurfaceActivity : ComponentActivity() {

    private lateinit var surfaceView: Camera2GlSurfaceView
    private val camera2 get() = GlobalDependency.camera2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_to_glsurface)
        surfaceView = findViewById(R.id.glsurface_camera)
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
        surfaceView.onSurfaceCreate = { surface ->
            camera2.openCamera(surfaceView.width, surfaceView.height) { newWidth, newHeight ->
                // TODO update matrix
            }
            camera2.startPreview(surface)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
        camera2.stopPreview()
        camera2.closeCamera()
    }
}