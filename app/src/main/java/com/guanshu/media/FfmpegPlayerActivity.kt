package com.guanshu.media

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.guanshu.media.utils.Logger

private const val TAG = "FfmpegPlayerActivity"

class FfmpegPlayerActivity : ComponentActivity() {

    init {
        System.loadLibrary("native-media")
    }

    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i(TAG, "onCreate")
        setContentView(R.layout.activity_ffmpeg_player)
        textView = findViewById(R.id.textview_ffmpeg)
        textView.text = stringFromNative()
    }

    private external fun stringFromNative(): String

}