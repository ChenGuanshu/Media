package com.guanshu.media.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import com.google.android.exoplayer2.util.GlUtil
import com.guanshu.media.opengl.TextureRender
import com.guanshu.media.utils.DefaultSize
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "Camera2GlSurfaceView"

class Camera2GlSurfaceView : GLSurfaceView {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val frameAvailable = AtomicBoolean(false)
    private val textureRender = TextureRender()
    var onSurfaceCreate: ((Surface) -> Unit)? = null
        set(value) {
            if (surface != null) {
                value?.invoke(surface!!)
            }
            field = value
        }
    var cameraResolution = DefaultSize
    val viewResolution by lazy { Size(width, height) }

    private val renderer = object : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Log.i(TAG, "onSurfaceCreated")
            try {
                textureRender.init()
            } catch (e: GlUtil.GlException) {
                Log.e(TAG, "create texture failed", e)
            }

            surfaceTexture = SurfaceTexture(textureRender.textureId)
            surfaceTexture?.setOnFrameAvailableListener {
                frameAvailable.set(true)
                requestRender()
            }
            surface = Surface(surfaceTexture)
            onSurfaceCreate?.invoke(surface!!)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceChanged $width, $height")
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (frameAvailable.compareAndSet(true, false)) {
                surfaceTexture?.updateTexImage()
                textureRender.drawFrame(
                    surfaceTexture!!,
                    cameraResolution,
                    viewResolution,
                )
            }
        }
    }

    init {
        Log.i(TAG, "init")
        setEGLContextClientVersion(2)
        setEGLConfigChooser(
            /* redSize= */8,
            /* greenSize= */8,
            /* blueSize= */ 8,
            /* alphaSize= */8,
            /* depthSize= */0,
            /* stencilSize= */0
        )
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}