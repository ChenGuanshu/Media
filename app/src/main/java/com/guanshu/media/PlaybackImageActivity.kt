package com.guanshu.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.guanshu.media.imageplayback.ImagePlayer
import com.guanshu.media.imageplayback.ImageSource
import com.guanshu.media.utils.Logger
import java.lang.ref.WeakReference

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

    private val bitmapCache = hashMapOf<String, WeakReference<Bitmap>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_image)
        viewPager = findViewById(R.id.image_playback_view_pager)
        adapter = ImageAdapter()
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

//        imagePlayer = ImagePlayer()
//        imagePlayer.init()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
//        imagePlayer.release()
    }

    private inner class ImageAdapter : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            Logger.v(TAG, "createViewHolder")
            val imageView = ImageView(parent.context)
            val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            imageView.layoutParams = layoutParams
//            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
//            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
//            parent.addView(imageView)
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            Logger.v(TAG, "onBindViewHolder $position")
            val path = imageSources[position].filePath
            var bitmap = bitmapCache[path]?.get()
            if (bitmap == null) {
                bitmap = BitmapFactory
                    .decodeStream(javaClass.getResourceAsStream(path))
                bitmapCache[path] = WeakReference(bitmap)
            }

            (holder.itemView as ImageView).setImageBitmap(bitmap)
        }

        override fun getItemCount(): Int {
            return imageSources.size
        }

        private inner class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView)
    }
}