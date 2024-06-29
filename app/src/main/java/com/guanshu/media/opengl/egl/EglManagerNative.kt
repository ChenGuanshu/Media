package com.guanshu.media.opengl.egl

import android.opengl.EGLContext
import com.guanshu.media.utils.Logger

private const val TAG = "EglManagerNative"

class EglManagerNative : EglManagerInterface {

    init {
        System.loadLibrary("native-media")
    }

    private var nativeEgl: Long = -1
    private var nativeEglSurface: Long = -1

    private fun checkEgl() {
        if (nativeEgl == -1L) throw IllegalArgumentException("egl not init")
    }

    private fun checkEglSurface() {
        if (nativeEglSurface == -1L) throw IllegalArgumentException("eglSurface not init")
    }

    override fun getEglContext(): EGLContext {
//        val nativeEglContext = nativeGetEglContext(nativeEgl)
        throw UnsupportedOperationException("getEglContext")
    }

    override fun init(sharedEglContext: EGLContext?) {
        nativeEgl = nativeInit(null)
        Logger.d(TAG, "init $nativeEgl")
    }

    override fun initEglSurface(surface: Any) {
        checkEgl()
        nativeEglSurface = nativeInitEglSurface(nativeEgl, surface)
        Logger.d(TAG, "initEglSurface $nativeEgl")
    }

    override fun makeEglCurrent() {
        checkEgl()
        checkEglSurface()
        nativeMakeEglCurrent(nativeEgl, nativeEglSurface)
        Logger.d(TAG, "makeEglCurrent $nativeEgl, $nativeEglSurface")
    }

    override fun makeUnEglCurrent() {
        checkEgl()
        nativeMakeUnEglCurrent(nativeEgl)
        Logger.d(TAG, "makeUnEglCurrent $nativeEgl")
    }

    override fun swapBuffer() {
        checkEgl()
        checkEglSurface()
        nativeSwapBuffer(nativeEgl, nativeEglSurface)
    }

    override fun releaseEglSurface() {
        if (nativeEgl == -1L) return
        if (nativeEglSurface == -1L) return

        Logger.d(TAG, "releaseEglSurface $nativeEgl, $nativeEglSurface")
        nativeReleaseEglSurface(nativeEgl, nativeEglSurface)
        nativeEglSurface = -1
    }

    override fun release() {
        if (nativeEgl == -1L) return

        Logger.d(TAG, "release $nativeEgl")
        nativeRelease(nativeEgl)
        nativeEgl = -1
    }

    private external fun nativeInit(sharedEglContext: EGLContext?): Long

    private external fun nativeInitEglSurface(nativeEgl: Long, surface: Any): Long

    private external fun nativeMakeEglCurrent(nativeEgl: Long, nativeEglSurface: Long)

    private external fun nativeMakeUnEglCurrent(nativeEgl: Long)

    private external fun nativeSwapBuffer(nativeEgl: Long, nativeEglSurface: Long)

    private external fun nativeReleaseEglSurface(nativeEgl: Long, nativeEglSurface: Long)

    private external fun nativeRelease(nativeEgl: Long)

    private external fun nativeGetEglContext(nativeEgl: Long): Long
}