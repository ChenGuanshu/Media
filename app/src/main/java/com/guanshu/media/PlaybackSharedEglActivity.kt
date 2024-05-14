package com.guanshu.media

import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.VIDEO_PATH
import com.guanshu.media.view.AdvancedOpenglSurfaceView

private const val TAG = "PlaybackSharedEglActivity"

class PlaybackSharedEglActivity : ComponentActivity(), Player.Listener {

    private lateinit var surfaceView: AdvancedOpenglSurfaceView
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_to_custom_shared_glsurface)
        surfaceView = findViewById(R.id.shared_glsurface_playback)
        surfaceView.init()

        val player = ExoPlayer.Builder(this.applicationContext).build()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(VIDEO_PATH)))
        player.prepare()
        // https://github.com/TheWidlarzGroup/react-native-video/issues/2767
        // it's reported repeat_all causing OOM
        player.repeatMode = ExoPlayer.REPEAT_MODE_OFF
        player.addListener(this)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format
            ) {
                super.onVideoInputFormatChanged(eventTime, format)
                val size = when (format.rotationDegrees) {
                    90, 270 -> Size(format.height, format.width)
                    else -> Size(format.width, format.height)
                }
                if (size != surfaceView.getMediaResolution()) {
                    Logger.i(TAG, "input format=$format")
                    surfaceView.setMediaResolution(size)
                }
            }
        })
        player.addListener(object : Player.Listener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                super.onPlayerStateChanged(playWhenReady, playbackState)
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.playWhenReady = true
                }
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
        surfaceView.requestSurface { surface ->
            surfaceView.post {
                player?.setVideoSurface(surface)
                player?.playWhenReady = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Logger.i(TAG, "onPause")
        player?.playWhenReady = false
        player?.setVideoSurface(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.stop()
        player?.release()
        surfaceView.release()
    }
}