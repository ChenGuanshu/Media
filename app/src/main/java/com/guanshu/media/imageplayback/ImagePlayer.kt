package com.guanshu.media.imageplayback

import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceView
import com.guanshu.media.opengl.abstraction.Sampler2DTexture
import com.guanshu.media.opengl.egl.EglManagerInterface
import com.guanshu.media.opengl.egl.EglManagerNative
import com.guanshu.media.utils.Logger

data class ImageSource(
    val filePath: String
)

private const val TAG = "ImagePlayer"

class ImagePlayer {

    private val glHandler: Handler
    private val eglManager: EglManagerInterface = EglManagerNative()

    private var imageSources: List<ImageSource>? = null
    private var imageTextures: List<Sampler2DTexture>? = null

    init {
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        glHandler = Handler(handlerThread.looper)
    }

    fun init() {
        Logger.d(TAG, "init")
        glHandler.post {
            eglManager.init()
        }
    }

    fun setDataSource(imageSources: List<ImageSource>) {
        Logger.d(TAG, "setDataSource $imageSources")
        glHandler.post {
            this.imageSources = imageSources
            this.imageTextures = imageSources.map {
                Sampler2DTexture.fromFilePath(it.filePath)
            }
        }
    }

    fun seek(index:Int, surface: SurfaceView){

    }

    fun setSurface(surface: Surface) {
        Logger.d(TAG, "setSurface $surface")
        glHandler.post {
            eglManager.initEglSurface(surface)
            eglManager.makeEglCurrent()
        }
    }

    fun playback(index: Int = 0) {
        Logger.d(TAG, "playback $index")
    }

    fun release() {
        Logger.d(TAG, "release")
        glHandler.post {
            eglManager.releaseEglSurface()
            eglManager.release()
            glHandler.removeCallbacksAndMessages(null)
            glHandler.looper.quitSafely()
        }
    }
}