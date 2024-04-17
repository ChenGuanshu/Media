package com.guanshu.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.camera2_to_surface).setOnClickListener {
            val intent = Intent(this, Camera2SurfaceActivity::class.java)
            startActivity(intent)
        }
    }
}