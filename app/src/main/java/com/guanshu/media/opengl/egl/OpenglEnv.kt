package com.guanshu.media.opengl.egl

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.os.Handler
import android.os.HandlerThread
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.Logger


class OpenglEnv(name: String) {

    private val TAG = "OpenglEnv#$name"

    private val egl = EglManager()
    private lateinit var glThread: HandlerThread
    private lateinit var glHandler: Handler

    fun getEglContext() = egl.getEglContext()

    fun initThread() {
        Logger.d(TAG, "initThread")
        val glThread = HandlerThread(TAG)
        glThread.start()

        this.glHandler = Handler(glThread.looper)
        this.glThread = glThread
    }


    // blocking calling
    fun initContext(eglContext: EGLContext? = null, callback: () -> Unit) {
        Logger.d(TAG, "init: shared eglContext = $eglContext")
        postOrRun {
            egl.init(eglContext)
            callback()
            Logger.d(TAG, "init DONE")
        }
    }

    fun requestSurface(num: Int, callback: (List<Pair<Int, SurfaceTexture>>) -> Unit) {
        Logger.d(TAG, "requestSurface $num")
        postOrRun {
            val textures = IntArray(num)
            newTexture(textures)
            callback(textures.mapIndexed { i, t -> Pair(textures[i], SurfaceTexture(t)) })
        }
    }

    fun requestRender(onDraw: () -> Unit) {
        postOrRun(onDraw)
    }

    fun swapBuffer() = postOrRun { egl.swapBuffer() }

    fun initEglSurface(surface: Any) {
        postOrRun {
            egl.releaseEglSurface()

            egl.initEglSurface(surface)
            egl.makeEglCurrent()
        }
    }

    fun releaseEglSurface() {
        postOrRun {
            egl.makeUnEglCurrent()
            egl.releaseEglSurface()
        }
    }

    fun release() {
//        Logger.d(TAG, "release")
        if (!::glThread.isInitialized) {
            Logger.w(TAG, "release failed")
            return
        }

        glHandler.removeCallbacksAndMessages(null)
        postOrRun {
            Logger.d(TAG, "release run")
            egl.release()
        }
        glThread.quitSafely()
    }

    fun postOrRun(job: () -> Unit) {
        if (!::glThread.isInitialized) {
            Logger.e(TAG, "postOrRun thread not init")
            return
        }
        if (Thread.currentThread() == glThread) {
            job.invoke()
        } else if (!glHandler.post(job)) {
            Logger.e(TAG, "postOrRun failed")
        }
    }
}