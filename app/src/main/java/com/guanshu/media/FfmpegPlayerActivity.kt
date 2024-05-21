package com.guanshu.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.MP3_PATH
import com.guanshu.media.utils.VIDEO_PATH
import java.io.File

private const val TAG = "FfmpegPlayerActivity"

class FfmpegPlayerActivity : ComponentActivity(), SurfaceHolder.Callback {

    init {
        System.loadLibrary("native-media")
    }

    private lateinit var surfaceView: SurfaceView
    private var surfaceHolder: SurfaceHolder? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i(TAG, "onCreate")
        setContentView(R.layout.activity_ffmpeg_player)
        surfaceView = findViewById(R.id.surface_ffmpeg)
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated")
        surfaceHolder = holder
        Thread {
            Logger.d(TAG,"start decode media")
            decodeMedia(VIDEO_PATH, holder.surface)
        }.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG, "surfaceChanged $width*$height")
        surfaceHolder = holder
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceDestroyed")
        surfaceHolder = null
        stopMedia()
    }

    override fun onResume() {
        super.onResume()
//        Thread {
//            val file = File(MP3_PATH)
//            Logger.d(TAG, "file: $MP3_PATH")
//            Logger.d(TAG, "file exist: ${file.exists()}, ${file.canRead()}")
//            val decodeRet = decodeAudio(MP3_PATH)
//            Logger.d(TAG, "decodeRet = $decodeRet")
//        }.start()
    }

    override fun onPause() {
        super.onPause()
        stopAudio()
        stopMedia()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private external fun loadFfmpegInfo(): String

    private external fun decodeAudio(file: String): Int

    private external fun stopAudio()

    private external fun decodeMedia(file: String, surface: Surface): Int

    private external fun stopMedia()

    private fun onDataReceive(data: ByteArray) {
        maybeInitAudioTrack()
        val ret = audioTrack?.write(data, 0, data.size) ?: 0
        if (ret < 0) {
            Logger.d(TAG, "write audio track: $ret")
        }
    }

    private fun maybeInitAudioTrack() {
        if (audioTrack != null) {
            return
        }
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
        val audioFormat = AudioFormat.Builder().setEncoding(
            AudioFormat.ENCODING_PCM_16BIT
        ).setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        audioTrack = AudioTrack(
            attr, audioFormat, AudioTrack.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }
}