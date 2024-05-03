package com.guanshu.media.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import com.google.android.exoplayer2.util.GlUtil
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.TextureRender
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.DefaultSize
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "SingleSourceGlSurfaceView"

class TwoSourceGlSurfaceView : GLSurfaceView {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val textureIds = IntArray(2)
    private val surfaceTextures = arrayListOf<SurfaceTexture>()
    private val surfaces = arrayListOf<Surface>()
    private val textureDatas = hashMapOf<SurfaceTexture, TextureData>()
    private val frameAvailables = hashMapOf<SurfaceTexture, AtomicBoolean>()

    private val textureRender = TextureRender()

    var onSurfaceCreate: ((List<Surface>) -> Unit)? = null
        set(value) {
            value?.invoke(surfaces)
            field = value
        }
    var mediaResolution = DefaultSize
        set(value) {
            Log.i(TAG, "set camera resolution=$value")
//            textureData?.resolution = value
            field = value
        }
    var viewResolution = DefaultSize
        set(value) {
            Log.i(TAG, "set view resolution=$value")
            field = value
        }

    private val renderer = object : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Log.i(TAG, "onSurfaceCreated")
            try {
                textureRender.init()
            } catch (e: GlUtil.GlException) {
                Log.e(TAG, "create texture failed", e)
            }
            // 设置 texture
            newTexture(textureIds)
            textureIds.forEach {
                val surfaceTexture = SurfaceTexture(it)
                frameAvailables[surfaceTexture] = AtomicBoolean(false)
                surfaceTexture.setOnFrameAvailableListener {
                    frameAvailables[surfaceTexture]?.set(true)
                    requestRender()
                }
                surfaceTextures.add(surfaceTexture)

                surfaces.add(Surface(surfaceTexture))
                textureDatas[surfaceTexture] = TextureData(it, FloatArray(16), mediaResolution)
            }

            onSurfaceCreate?.invoke(surfaces)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceChanged $width, $height")
            GLES20.glViewport(0, 0, width, height)

            // TODO
            surfaceTextures.forEach { it.setDefaultBufferSize(width, height) }

            viewResolution = Size(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            frameAvailables.entries.forEach {
                val st = it.key
                val aBoolean = it.value
                val textureData = textureDatas[st]!!

                if (aBoolean.compareAndSet(true, false)) {
                    st.updateTexImage()
                    Matrix.setIdentityM(textureData.matrix, 0)
                    st.getTransformMatrix(textureData.matrix)
                }
            }

            textureRender.drawFrame(
                textureDatas.values.toList(),
                viewResolution,
            )
        }
    }

    init {
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