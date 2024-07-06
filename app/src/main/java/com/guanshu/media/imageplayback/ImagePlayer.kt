package com.guanshu.media.imageplayback

import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.Sampler2DTexture
import com.guanshu.media.opengl.egl.EglManagerInterface
import com.guanshu.media.opengl.egl.EglManagerNative
import com.guanshu.media.opengl.egl.EglSurfaceInterface
import com.guanshu.media.opengl.filters.SingleImageTextureFilter
import com.guanshu.media.utils.Logger

data class ImageSource(
    val filePath: String
)

private const val TAG = "ImagePlayer"

class ImagePlayer {

    private val glHandler: Handler
    private val eglManager: EglManagerInterface = EglManagerNative()
    private val textureRender = SingleImageTextureFilter()

    private var imageSources: List<ImageSource>? = null
    private var imageTextures: List<TextureData>? = null
    private val eglSurfaceMap = hashMapOf<Surface, EglSurfaceInterface>()

    init {
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        glHandler = Handler(handlerThread.looper)
    }

    fun init() {
        Logger.d(TAG, "init")
        glHandler.post {
            eglManager.init()
            textureRender.init()
        }
    }

    fun setDataSource(imageSources: List<ImageSource>) {
        Logger.d(TAG, "setDataSource $imageSources")
        glHandler.post {
            this.imageSources = imageSources
            this.imageTextures = imageSources
                .map { Sampler2DTexture.fromFilePath(it.filePath) }
                .map {
                    TextureData(
                        it.textureId,
                        it.matrix,
                        it.resolution,
                        it.textureType,
                    )
                }
        }
    }

    fun seek(index: Int, surface: Surface, resolution: Size) {
        Logger.i(TAG, "seek $index, $surface, $resolution")

        val start = System.currentTimeMillis()
        glHandler.post {
            val eglSurface = eglSurfaceMap.getOrPut(surface) {
                eglManager.initEglSurface(surface)
            }
            eglManager.makeEglCurrent(eglSurface)

            val texture = imageTextures?.get(index) ?: return@post
            GLES20.glViewport(0, 0, resolution.width, resolution.height)
            textureRender.render(listOf(texture), resolution)
            eglManager.swapBuffer()

            Logger.v(TAG, "seek cost ${System.currentTimeMillis() - start}")
        }
    }

    fun releaseSurface(surface: Surface?) {
        Logger.i(TAG, "releaseSurface $surface")
        if (surface == null) return
        glHandler.post {
            val surfaceInterface = eglSurfaceMap[surface] ?: return@post
            eglManager.releaseEglSurface(surfaceInterface)
            eglSurfaceMap.remove(surface)
        }
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