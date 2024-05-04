package com.guanshu.media

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.guanshu.media.utils.Logger
import com.guanshu.media.view.OpenglTextureView
import com.guanshu.media.view.SingleSourceGlSurfaceView

private const val TAG = "PlaybackCustomGlTextureActivity"
private const val VIDEO_PATH = "/sdcard/DCIM/Camera/lv_0_20240122222838.mp4"

class PlaybackCustomGlTextureActivity : ComponentActivity(), Player.Listener {

    private lateinit var surfaceView: OpenglTextureView
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_to_custom_gltexture)
        surfaceView = findViewById(R.id.glsurface_playback)
        surfaceView.init()

        val player = ExoPlayer.Builder(this.applicationContext).build()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(VIDEO_PATH)))
        player.prepare()
        player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
        player.addListener(this)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format
            ) {
                super.onVideoInputFormatChanged(eventTime, format)
                Logger.i(TAG, "input format=$format")
                val size = when (format.rotationDegrees) {
                    90, 270 -> Size(format.height, format.width)
                    else -> Size(format.width, format.height)
                }
                surfaceView.mediaResolution = size
            }
        })

        this.player = player
    }

    override fun onPlayerError(error: PlaybackException) {
        Logger.e(TAG, "onPlayerError", error)
    }

    override fun onResume() {
        super.onResume()
        Logger.i(TAG, "onResume")
        surfaceView.onSurfaceCreate = { surface ->
            surfaceView.post {
                this.player?.setVideoSurface(surface)
                this.player?.playWhenReady = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Logger.i(TAG, "onPause")
        this.player?.playWhenReady = false
        this.player?.setVideoSurface(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        surfaceView.release()
    }
}