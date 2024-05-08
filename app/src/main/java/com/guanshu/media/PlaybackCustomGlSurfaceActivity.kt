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
import com.guanshu.media.opengl.filters.FilterConstants
import com.guanshu.media.opengl.filters.RenderGraph
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.VIDEO_PATH
import com.guanshu.media.view.OpenglSurfaceView

private const val TAG = "PlaybackCustomGlSurfaceActivity"

class PlaybackCustomGlSurfaceActivity : ComponentActivity(), Player.Listener {

    private lateinit var surfaceView: OpenglSurfaceView
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_to_custom_glsurface)
        surfaceView = findViewById(R.id.glsurface_playback)
        surfaceView.renderGraph = RenderGraph(FilterConstants.FLATTEN_WITH_IMAGE)
//        surfaceView.renderGraph =
//            RenderGraph().apply { addOutputFilter(FilterConstants.TEXTURE_WITH_IMAGE) }
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
                val size = when (format.rotationDegrees) {
                    90, 270 -> Size(format.height, format.width)
                    else -> Size(format.width, format.height)
                }
                if (size != surfaceView.getMediaResolution(0)) {
                    Logger.i(TAG, "input format=$format")
                    surfaceView.setMediaResolution(0, size)
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
        surfaceView.onSurfaceCreate = { surface ->
            surfaceView.post {
                player?.setVideoSurface(surface.first())
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