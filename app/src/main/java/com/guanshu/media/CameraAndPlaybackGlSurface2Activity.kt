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
import com.guanshu.media.application.GlobalDependency
import com.guanshu.media.utils.Logger
import com.guanshu.media.utils.VIDEO_PATH
import com.guanshu.media.view.OpenglSurfaceView
import com.guanshu.media.view.RenderingMode

private const val TAG = "CameraAndPlaybackGlSurface2Activity"

/**
 * Render with the OpenglSurfaceView
 * Control the frame rate by [RenderingMode]
 */
class CameraAndPlaybackGlSurface2Activity : ComponentActivity(), Player.Listener {

    private lateinit var surfaceView: OpenglSurfaceView

    private val camera2 get() = GlobalDependency.camera2
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_playback_to_glsurface2)
        surfaceView = findViewById(R.id.glsurface_camera_playback2)
        surfaceView.filterId = 5
        surfaceView.renderingMode = RenderingMode.RenderFixedRate(15)
        surfaceView.init(2)

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
                surfaceView.setMediaResolution(1, size)
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
        surfaceView.onSurfaceCreate = { surfaces ->
            if (surfaces.size >= 2) {
                val cameraSurface = surfaces[0]
                val playbackSurface = surfaces[1]

                surfaceView.onDisplaySurfaceCreate = {
                    surfaceView.post {
                        camera2.openCamera(
                            surfaceView.width,
                            surfaceView.height
                        ) { newWidth, newHeight ->
                            surfaceView.setMediaResolution(0, Size(newWidth, newHeight))
                        }
                        camera2.startPreview(cameraSurface)
                    }
                }

                surfaceView.post {
                    this.player?.setVideoSurface(playbackSurface)
                    this.player?.playWhenReady = true
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Logger.i(TAG, "onPause")

        camera2.stopPreview()
        camera2.closeCamera()

        this.player?.playWhenReady = false
        this.player?.setVideoSurface(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        surfaceView.release()
    }
}