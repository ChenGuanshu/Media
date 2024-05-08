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
import com.guanshu.media.opengl.filters.FilterConstants
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "SingleSourceGlSurfaceView"

class SingleSourceGlSurfaceView : GLSurfaceView {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var textureId = -12345
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureData: TextureData? = null

    private val frameAvailable = AtomicBoolean(false)
    private val textureRender = TextureRender().apply { addFilter(FilterConstants.FLATTEN_WITH_IMAGE) }

    var onSurfaceCreate: ((Surface) -> Unit)? = null
        set(value) {
            if (surface != null) {
                value?.invoke(surface!!)
            }
            field = value
        }
    var mediaResolution = DefaultSize
        set(value) {
            Logger.i(TAG, "set media resolution=$value")
//            surfaceTexture?.setDefaultBufferSize(value.width, value.height)
            textureData?.resolution = value
            field = value
        }
    var viewResolution = DefaultSize
        set(value) {
            Logger.i(TAG, "set view resolution=$value")
            field = value
        }

    private val renderer = object : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Logger.i(TAG, "onSurfaceCreated")

            val textures = IntArray(1)
            newTexture(textures)
            textureId = textures[0]
            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture?.setOnFrameAvailableListener {
                frameAvailable.set(true)
                requestRender()
            }
            surface = Surface(surfaceTexture)
            textureData = TextureData(textureId, FloatArray(16), mediaResolution)
            onSurfaceCreate?.invoke(surface!!)

            textureRender.init()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Logger.i(TAG, "onSurfaceChanged $width, $height")
            // 可以通过调整viewport的大小，来调整输出的视频大小
//            GLES20.glViewport(100, 100, width-200, height-200)
            GLES20.glViewport(0, 0, width, height)

            // 控制camera的输出buffer，不设置的话分辨率会比较低
            surfaceTexture?.setDefaultBufferSize(width, height)
            viewResolution = Size(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (frameAvailable.compareAndSet(true, false)) {
                surfaceTexture?.updateTexImage()
                Matrix.setIdentityM(textureData!!.matrix, 0)
                surfaceTexture?.getTransformMatrix(textureData!!.matrix)
                /**
                 * 0.0, -1.0, 0.0, 0.0,
                 * 1.0,  0.0, 0.0, 0.0,
                 * 0.0,  0.0, 1.0, 0.0,
                 * 0.0,  1.0, 0.0, 1.0
                 * 这是一个st matrix的例子，表示了顺时针调整了90度，用来将纹理的内容正向
                 */
            }

            textureRender.drawFrame(
                listOf(textureData!!),
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