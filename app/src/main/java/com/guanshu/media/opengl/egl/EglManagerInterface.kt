package com.guanshu.media.opengl.egl

import android.opengl.EGLContext

private const val TAG = "EglManager"

interface EglManagerInterface {
    fun getEglContext(): EGLContext
    fun init(sharedEglContext: EGLContext? = null)
    fun initEglSurface(surface: Any): EglSurfaceInterface
    fun makeEglCurrent(eglSurfaceInterface: EglSurfaceInterface? = null)
    fun makeUnEglCurrent()
    fun swapBuffer()
    fun releaseEglSurface(surfaceInterface: EglSurfaceInterface? = null)
    fun release()
}

interface EglSurfaceInterface
class EglSurfaceDelegate : EglSurfaceInterface