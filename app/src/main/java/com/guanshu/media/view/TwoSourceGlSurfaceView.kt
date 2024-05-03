//package com.guanshu.media.view
//
//import android.content.Context
//import android.graphics.SurfaceTexture
//import android.opengl.GLES20
//import android.opengl.GLSurfaceView
//import android.util.AttributeSet
//import android.util.Log
//import android.util.Size
//import android.view.Surface
//import com.google.android.exoplayer2.util.GlUtil
//import com.guanshu.media.opengl.TextureRender
//import com.guanshu.media.utils.DefaultSize
//import java.util.concurrent.atomic.AtomicBoolean
//import javax.microedition.khronos.egl.EGLConfig
//import javax.microedition.khronos.opengles.GL10
//
//private const val TAG = "MultipleSourceGlSurfaceView"
//
//class TwoSourceGlSurfaceView : GLSurfaceView {
//
//    constructor(context: Context) : super(context, null)
//    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
//
//    private val surfaceTextureList = arrayListOf<SurfaceTexture>()
//    private val surfaceList = arrayListOf<Surface>()
//
//    private val frameAvailable = AtomicBoolean(false)
//    private val textureRender = TextureRender()
//    var onSurfaceCreate: ((Surface) -> Unit)? = null
//        set(value) {
//            if (surface != null) {
//                value?.invoke(surface!!)
//            }
//            field = value
//        }
//    var mediaResolution = DefaultSize
//        set(value) {
//            Log.i(TAG, "set camera resolution=$value")
//            field = value
//        }
//    var viewResolution = DefaultSize
//        set(value) {
//            Log.i(TAG, "set view resolution=$value")
//            field = value
//        }
//
//    private val renderer = object : Renderer {
//        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//            Log.i(TAG, "onSurfaceCreated")
//            try {
//                textureRender.init()
//            } catch (e: GlUtil.GlException) {
//                Log.e(TAG, "create texture failed", e)
//            }
//
//            // TODO 动态申请 texture
//
//            surfaceTexture = SurfaceTexture(textureRender.textureId)
//            surfaceTexture?.setOnFrameAvailableListener {
//                frameAvailable.set(true)
//                requestRender()
//            }
//            surface = Surface(surfaceTexture)
//            onSurfaceCreate?.invoke(surface!!)
//        }
//
//        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
//            Log.i(TAG, "onSurfaceChanged $width, $height")
//            GLES20.glViewport(0, 0, width, height)
//            // 控制camera的输出buffer，不设置的话分辨率会比较低
//            surfaceTexture?.setDefaultBufferSize(width, height)
//            viewResolution = Size(width, height)
//        }
//
//        override fun onDrawFrame(gl: GL10?) {
//            if (frameAvailable.compareAndSet(true, false)) {
//                surfaceTexture?.updateTexImage()
//                textureRender.drawFrame(
//                    surfaceTexture!!,
//                    mediaResolution,
//                    viewResolution,
//                )
//            }
//        }
//    }
//
//    init {
//        setEGLContextClientVersion(2)
//        setEGLConfigChooser(
//            /* redSize= */8,
//            /* greenSize= */8,
//            /* blueSize= */ 8,
//            /* alphaSize= */8,
//            /* depthSize= */0,
//            /* stencilSize= */0
//        )
//        setRenderer(renderer)
//        renderMode = RENDERMODE_WHEN_DIRTY
//    }
//}