package com.guanshu.media.application

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import com.guanshu.media.camera.Camera2

private const val TAG = "MediaApplication"

class MediaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        GlobalDependency.context = this.applicationContext
        Log.i(TAG, "onCreate")
    }
}

@SuppressLint("StaticFieldLeak")
object GlobalDependency {
    lateinit var context: Context
    val camera2 by lazy { Camera2(context) }
}