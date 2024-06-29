package com.guanshu.media.opengl.egl

import android.opengl.EGLContext

private const val TAG = "EglManager"

interface EglManagerInterface {
    fun getEglContext(): EGLContext
    fun init(sharedEglContext: EGLContext? = null)
    fun initEglSurface(surface: Any)
    fun makeEglCurrent()
    fun makeUnEglCurrent()
    fun swapBuffer()
    fun releaseEglSurface()
    fun release()
}