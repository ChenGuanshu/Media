package com.guanshu.media

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.guanshu.media.imageplayback.ImagePlayer
import com.guanshu.media.imageplayback.ImageSource
import com.guanshu.media.utils.Logger

private const val TAG = "PlaybackImageActivity"

class PlaybackImageActivity : ComponentActivity() {

    private lateinit var imagePlayer: ImagePlayer
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImageAdapter

    private val imageSources = listOf(
        ImageSource("/res/drawable/pikachu.png"),
        ImageSource("/res/drawable/pikachu2.png"),
        ImageSource("/res/drawable/pikachu3.jpeg"),
        ImageSource("/res/drawable/pikachu4.jpeg"),
        ImageSource("/res/drawable/pikachu5.jpeg"),
    )

    private val surfaceMap = hashMapOf<SurfaceView, SurfaceReference>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_image)
        viewPager = findViewById(R.id.image_playback_view_pager)
        adapter = ImageAdapter()
        viewPager.adapter = adapter
//        viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        viewPager.offscreenPageLimit = 1
        imagePlayer = ImagePlayer()
        imagePlayer.init()
        imagePlayer.setDataSource(imageSources)
    }

    override fun onDestroy() {
        super.onDestroy()
        imagePlayer.release()
    }

    private inner class ImageAdapter : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            Logger.v(TAG, "createViewHolder")
            val surfaceView = SurfaceView(parent.context)
            val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            surfaceView.layoutParams = layoutParams
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) =
                    Logger.d(TAG, "surfaceCreated $holder")

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    Logger.d(TAG, "surfaceChanged $holder")
                    surfaceMap[surfaceView]?.run {
                        surface = holder.surface
                        size = Size(width, height)
                    }
                    maybeSeek(surfaceView)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Logger.d(TAG, "surfaceDestroyed $holder, ${surfaceMap[surfaceView]}")
                    surfaceMap[surfaceView]?.run {
                        surface = null
                        size = null
//                        position = -1
                    }
                    imagePlayer.releaseSurface(holder.surface)
                }
            })
            surfaceMap[surfaceView] = SurfaceReference()

            return ImageViewHolder(surfaceView)
        }

        @SuppressLint("CheckResult")
        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            Logger.d(TAG, "onBindViewHolder $position")
            val surfaceView = holder.itemView as SurfaceView
            surfaceMap[surfaceView]?.run { this.position = position }
            maybeSeek(surfaceView)
        }

        override fun getItemCount(): Int {
            return imageSources.size
        }

        private inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }

    private fun maybeSeek(surfaceView: SurfaceView) {
        val surfaceReference = surfaceMap[surfaceView] ?: return
        if (surfaceReference.run { surface == null || size == null || position == -1 }) return

        imagePlayer.seek(
            surfaceReference.position,
            surfaceReference.surface!!,
            surfaceReference.size!!
        )
    }

    private data class SurfaceReference(
        var surface: Surface? = null,
        var size: Size? = null,
        var position: Int = -1,
    )
}