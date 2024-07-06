package com.guanshu.media.opengl.egl

import android.opengl.EGLContext
import com.guanshu.media.utils.Logger

private const val TAG = "EglManagerNative"

class EglManagerNative : EglManagerInterface {

    init {
        System.loadLibrary("native-media")
    }

    private var nativeEgl: Long = -1
    private var currentNativeEglSurface: Long = -1
    private val eglSurfaceMap = hashMapOf<EglSurfaceInterface, Long>()

    private fun checkEgl() {
        if (nativeEgl == -1L) throw IllegalArgumentException("egl not init")
    }

    override fun getEglContext(): EGLContext {
//        val nativeEglContext = nativeGetEglContext(nativeEgl)
        throw UnsupportedOperationException("getEglContext")
    }

    override fun init(sharedEglContext: EGLContext?) {
        nativeEgl = nativeInit(null)
        Logger.d(TAG, "init $nativeEgl")
    }

    override fun initEglSurface(surface: Any): EglSurfaceInterface {
        checkEgl()
        val nativeEglSurface = nativeInitEglSurface(nativeEgl, surface)
        val delegate = EglSurfaceDelegate()
        Logger.d(TAG, "initEglSurface $nativeEgl")
        eglSurfaceMap[delegate] = nativeEglSurface
        currentNativeEglSurface = nativeEglSurface
        return delegate
    }

    override fun makeEglCurrent(eglSurfaceInterface: EglSurfaceInterface?) {
        checkEgl()
        val eglSurface = if (eglSurfaceInterface == null) {
            currentNativeEglSurface
        } else {
            eglSurfaceMap[eglSurfaceInterface] ?: -1L
        }
        if (eglSurface == -1L) {
            throw RuntimeException("Unable to find native eglSurface from $eglSurfaceInterface")
        }
        currentNativeEglSurface = eglSurface

        nativeMakeEglCurrent(nativeEgl, eglSurface)
        Logger.d(TAG, "makeEglCurrent $nativeEgl, $currentNativeEglSurface")
    }

    override fun makeUnEglCurrent() {
        checkEgl()
        nativeMakeUnEglCurrent(nativeEgl)
        Logger.d(TAG, "makeUnEglCurrent $nativeEgl")
    }

    override fun swapBuffer() {
        checkEgl()
        if (currentNativeEglSurface == -1L) {
            throw RuntimeException("currentNativeEglSurface not set")
        }
        nativeSwapBuffer(nativeEgl, currentNativeEglSurface)
    }

    override fun releaseEglSurface() {
        if (nativeEgl == -1L) return
        if (currentNativeEglSurface == -1L) return

        Logger.d(TAG, "releaseEglSurface $nativeEgl, $currentNativeEglSurface")
        makeUnEglCurrent()
        nativeReleaseEglSurface(nativeEgl, currentNativeEglSurface)
        val iterator = eglSurfaceMap.entries.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.value == currentNativeEglSurface) iterator.remove()
        }

        currentNativeEglSurface = -1L
    }

    override fun release() {
        if (nativeEgl == -1L) return
        eglSurfaceMap.values.forEach { nativeEglSurface ->
            if (nativeEglSurface != -1L) {
                nativeReleaseEglSurface(nativeEgl, nativeEglSurface)
            }
        }
        eglSurfaceMap.clear()

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