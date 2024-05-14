package com.guanshu.media.opengl.egl

import android.opengl.EGL14
import android.opengl.EGL14.eglChooseConfig
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import com.guanshu.media.utils.Logger

private const val TAG = "EglManager"

class EglManager {

    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private lateinit var eglConfig: EGLConfig

    private var eglSurface: EGLSurface? = null

    fun getEglContext() = eglContext

    fun init(sharedEglContext: EGLContext? = null) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed");
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw java.lang.RuntimeException("eglInitialize failed")
        }

        val configAttribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_SAMPLE_BUFFERS, 0,
            EGL14.EGL_NONE
        )


        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        eglChooseConfig(eglDisplay, configAttribList, 0, configs, 0, configs.size, numConfigs, 0)
        if (numConfigs[0] == 0) {
            throw RuntimeException("No configs match configSpec")
        }
        eglConfig = configs[0]!!

        // 创建上下文
        val contextAttribList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            sharedEglContext ?: EGL14.EGL_NO_CONTEXT,
            contextAttribList,
            0
        )

        if (eglContext == null || eglContext === EGL14.EGL_NO_CONTEXT) {
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
    fun initEglSurface(surface: Any) {
        if (eglSurface != null) {
            Logger.w(TAG, "initEglSurface: already a surface")
            return
        }
        eglSurface = EGL14.eglCreateWindowSurface(
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
    }

    fun makeEglCurrent() {
        // 绑定EGL上下文和表面
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Unable to make EGL context and surface current")
        }
        Logger.d(TAG, "makeEglCurrent")
    }

    fun makeUnEglCurrent() {
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

    fun swapBuffer() {
        if (eglSurface == null) {
            Logger.e(TAG, "swapBuffer: eglSurface is null")
            return
        }

        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            val error = EGL14.eglGetError()
            Logger.e(TAG, "swapBuffer failed, error =$error")
        }
    }

    fun releaseEglSurface() {
        if (eglSurface != null) {
            makeUnEglCurrent()
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = null
            Logger.d(TAG, "releaseEglSurface")
        }
    }

    fun release() {
        releaseEglSurface()
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        Logger.d(TAG, "release")
    }
}