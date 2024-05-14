package com.guanshu.media.opengl.egl

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.os.Handler
import android.os.HandlerThread
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.Logger

private const val TAG = "OpenglEnv"

class OpenglEnv {

    private val egl = EglManager()
    private lateinit var glThread: HandlerThread
    private lateinit var glHandler: Handler

    fun getEglContext() = egl.getEglContext()

    fun init(eglContext: EGLContext? = null, callback: () -> Unit) {
        Logger.d(TAG, "init: shared eglContext = $eglContext")
        val glThread = HandlerThread(TAG)
        glThread.start()
        this.glThread = glThread

        glHandler = Handler(glThread.looper)
        postOrRun {
            Logger.d(TAG, "init run")
            egl.init(eglContext)

            callback()
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
            return
        }
        if (Thread.currentThread() == glThread) {
            job.invoke()
        } else {
            glHandler.post(job)
        }
    }
}