package com.guanshu.media.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.TextureRender
import com.guanshu.media.opengl.egl.EglManager
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OpenglTextureView"

class OpenglTextureView : TextureView {

    private val egl = EglManager()
    private var glHandler: Handler? = null

    @Volatile
    private var displaySurface: SurfaceTexture? = null

    private var textureId = -12345
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureData: TextureData? = null

    private val frameAvailable = AtomicBoolean(false)
    private val textureRender = TextureRender(1)

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
            textureData?.resolution = value
            field = value
        }
    var viewResolution = DefaultSize
        set(value) {
            Logger.i(TAG, "set view resolution=$value")
            field = value
        }

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0,
    )

    init {
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Logger.d(TAG, "onSurfaceTextureAvailable: $surface, $width*$height")
                displaySurface = surface
                maybeInitEglSurface()
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // do Nothing
//                Logger.v(TAG,"onSurfaceTextureUpdated")
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Logger.d(TAG, "onSurfaceTextureSizeChanged: $surface, $width*$height")
                viewResolution = Size(width, height)
                displaySurface?.setDefaultBufferSize(width, height)

                postOrRun {
                    GLES20.glViewport(0, 0, width, height)
                }
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Logger.d(TAG, "onSurfaceTextureDestroyed: $surface")
                displaySurface = null

                maybeReleaseEglSurface()
                return true
            }
        }
    }

    fun init() {
        Logger.d(TAG, "init")
        val glThread = HandlerThread(TAG)
        glThread.start()
        glHandler = Handler(glThread.looper)
        postOrRun {
            Logger.d(TAG, "init run")
            egl.init()
            maybeInitEglSurface()

            val textures = IntArray(1)
            newTexture(textures)
            textureId = textures[0]
            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture?.setOnFrameAvailableListener {
                frameAvailable.set(true)
                render()
            }
            surface = Surface(surfaceTexture)
            textureData = TextureData(textureId, FloatArray(16), mediaResolution)
            onSurfaceCreate?.invoke(surface!!)

            textureRender.init()
        }
    }

    fun release() {
        Logger.d(TAG, "release")
        if (glHandler == null) {
            Logger.w(TAG, "glThread is not init")
            return
        }
        postOrRun {
            Logger.d(TAG, "release run")
            maybeReleaseEglSurface()
            egl.release()
        }
        glHandler?.looper?.quitSafely()
        glHandler = null
    }

    private fun render() {
        postOrRun {
            if (frameAvailable.compareAndSet(true, false)) {
                surfaceTexture?.updateTexImage()
                Matrix.setIdentityM(textureData!!.matrix, 0)
                surfaceTexture?.getTransformMatrix(textureData!!.matrix)
            }

            if (!textureRender.init) {
                return@postOrRun
            }

//            GLES20.glClearColor(1f, 0f, 0f, 0f)
//            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // TODO FIXFIXIIIXIXIXIXIXIFIFIX
            textureRender.drawFrame(
                listOf(textureData!!),
                viewResolution,
            )

            egl.swapBuffer()
        }
    }

    private fun maybeInitEglSurface() {
        Logger.d(TAG, "maybeInitEglSurface: $displaySurface")
        postOrRun {
            val surface = displaySurface ?: return@postOrRun
            Logger.d(TAG, "maybeInitEglSurface run")
            egl.releaseEglSurface()

            egl.initEglSurface(surface)
            egl.makeEglCurrent()
        }
    }

    private fun maybeReleaseEglSurface() {
        Logger.d(TAG, "maybeReleaseEglSurface")
        postOrRun {
            Logger.d(TAG, "maybeReleaseEglSurface run")
            egl.makeUnEglCurrent()
            egl.releaseEglSurface()
        }
    }

    private fun postOrRun(job: () -> Unit) {
        if (glHandler == null) {
            Logger.w(TAG, "postOrRun: glThread not init")
            return
        }
        if (Looper.myLooper() == glHandler?.looper) {
            job.invoke()
        } else {
            glHandler?.post(job)
        }
    }
}