package com.guanshu.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.guanshu.media.utils.Logger
import com.guanshu.media.view.Cube3DGlSurfaceView

private const val TAG = "Cube3dActivity"

class Cube3dActivity : ComponentActivity() {

    private lateinit var surfaceView: Cube3DGlSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG, "onCreate")
        setContentView(R.layout.activity_cube_3d)
        surfaceView = findViewById(R.id.surface_cube_3d)
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy")
    }
}