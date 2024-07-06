package com.guanshu.media.opengl.egl

import android.opengl.EGL14
import android.opengl.EGL14.eglChooseConfig
import android.opengl.EGL15
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import com.guanshu.media.utils.Logger

private const val TAG = "EglManager"

class EglManager : EglManagerInterface {

    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private lateinit var eglConfig: EGLConfig

    private var currentEglSurface: EGLSurface? = null
    private val eglSurfaceMap = hashMapOf<EglSurfaceInterface, EGLSurface>()

    override fun getEglContext() = eglContext

    override fun init(sharedEglContext: EGLContext?) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw java.lang.RuntimeException("eglInitialize failed")
        }

        val configAttribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL15.EGL_OPENGL_ES3_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 8,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_SAMPLE_BUFFERS, 0,
            EGL14.EGL_NONE,
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        eglChooseConfig(eglDisplay, configAttribList, 0, configs, 0, configs.size, numConfigs, 0)
        if (numConfigs[0] == 0) {
            throw RuntimeException("No configs match configSpec")
        }
        eglConfig = configs[0]!!

        val contextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE,
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            sharedEglContext ?: EGL14.EGL_NO_CONTEXT,
            contextAttribList,
            0
        )

        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }

        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            eglContext,
        )

        Logger.i(TAG, "init done:$eglContext, $eglDisplay")
    }

    // Surface/SurfaceTexture
    override fun initEglSurface(surface: Any): EglSurfaceInterface {
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL surface")
        }

        Logger.d(TAG, "initEglSurface, $eglSurface")

        val eglSurfaceDelegate = EglSurfaceDelegate()
        eglSurfaceMap[eglSurfaceDelegate] = eglSurface
        currentEglSurface = eglSurface
        return eglSurfaceDelegate
    }

    override fun makeEglCurrent(eglSurfaceInterface: EglSurfaceInterface?) {
        val eglSurface = if (eglSurfaceInterface == null) {
            currentEglSurface
        } else {
            eglSurfaceMap[eglSurfaceInterface]
        }
        currentEglSurface = eglSurface
        if (eglSurface == null) {
            throw RuntimeException("Unable to find eglSurface from $eglSurfaceInterface")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Unable to make EGL context and surface current")
        }
        Logger.d(TAG, "makeEglCurrent")
    }

    override fun makeUnEglCurrent() {
        if (!EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
        ) {
            throw RuntimeException("Unable to make EGL context and surface current")
        }
        Logger.d(TAG, "makeUnEglCurrent")
    }

    override fun swapBuffer() {
        if (currentEglSurface == null) {
            Logger.e(TAG, "swapBuffer: eglSurface is null")
            return
        }

        if (!EGL14.eglSwapBuffers(eglDisplay, currentEglSurface)) {
            val error = EGL14.eglGetError()
            Logger.e(TAG, "swapBuffer failed, error =$error")
        }
    }

    override fun releaseEglSurface() {
        if (currentEglSurface == null) return

        Logger.d(TAG, "releaseEglSurface")
        makeUnEglCurrent()
        EGL14.eglDestroySurface(eglDisplay, currentEglSurface)
        currentEglSurface = null
    }

    override fun release() {
        Logger.d(TAG, "release $eglDisplay, $eglContext")
        eglSurfaceMap.values.forEach { eglSurface ->
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
        }
        eglSurfaceMap.clear()

        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}