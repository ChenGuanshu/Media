package com.guanshu.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.MP3_PATH
import java.io.File

private const val TAG = "FfmpegPlayerActivity"

class FfmpegPlayerActivity : ComponentActivity() {

    init {
        System.loadLibrary("native-media")
    }

    private lateinit var textView: TextView
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i(TAG, "onCreate")
        setContentView(R.layout.activity_ffmpeg_player)
        textView = findViewById(R.id.textview_ffmpeg)
        textView.text = loadFfmpegInfo()
    }

    override fun onResume() {
        super.onResume()
        Thread {
            val file = File(MP3_PATH)
            Logger.d(TAG, "file: $MP3_PATH")
            Logger.d(TAG, "file exist: ${file.exists()}, ${file.canRead()}")
            val decodeRet = decodeAudio(MP3_PATH)
            Logger.d(TAG, "decodeRet = $decodeRet")
        }.start()
    }

    private external fun loadFfmpegInfo(): String

    private external fun decodeAudio(file: String): Int

    private fun onDataReceive(data: ByteArray) {
        maybeInitAudioTrack()
        val ret = audioTrack?.write(data, 0, data.size) ?: 0
        if (ret < 0) {
            Logger.d(TAG, "write audio track: $ret")
        }
    }

    override fun onPause() {
        super.onPause()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
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