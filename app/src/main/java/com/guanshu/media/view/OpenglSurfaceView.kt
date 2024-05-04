package com.guanshu.media.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.TextureRender
import com.guanshu.media.opengl.egl.EglManager
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OpenglSurfaceView"

class OpenglSurfaceView : SurfaceView, SurfaceHolder.Callback {

    private val egl = EglManager()
    private lateinit var glThread: HandlerThread
    private lateinit var glHandler: Handler

    @Volatile
    private var surfaceHolder: SurfaceHolder? = null

    private var textureId = -12345
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureData: TextureData? = null

    private val frameAvailable = AtomicBoolean(false)
    private val textureRender = TextureRender(4)

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
        holder.addCallback(this)
    }

    fun init() {
        Logger.d(TAG, "init")
        glThread = HandlerThread(TAG)
        glThread.start()
        glHandler = Handler(glThread.looper)
        glHandler.post {
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
        if (!this::glThread.isInitialized) {
            Logger.w(TAG, "glThread is not init")
            return
        }
        postOrRun {
            Logger.d(TAG, "release run")
            maybeReleaseEglSurface()
            egl.release()
        }
        glThread.quitSafely()
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

            // render dirty or not
            textureRender.drawFrame(
                listOf(textureData!!),
                viewResolution,
            )

            egl.swapBuffer()
        }
    }

    private fun maybeInitEglSurface() {
        Logger.d(TAG, "maybeInitEglSurface: ${surfaceHolder?.surface}")
        postOrRun {
            val surface = surfaceHolder?.surface ?: return@postOrRun
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")
        if (holder == surfaceHolder){
            return
        }

        surfaceHolder = holder
        maybeInitEglSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG, "surfaceChanged: $holder, $format, $width*$height")

        surfaceHolder = holder
        viewResolution = Size(width, height)
        surfaceTexture?.setDefaultBufferSize(width, height)
        postOrRun {
            GLES20.glViewport(0, 0, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")

        surfaceHolder = null
        maybeReleaseEglSurface()
    }

    private fun postOrRun(job: () -> Unit) {
        if (!this::glThread.isInitialized) {
            Logger.w(TAG, "postOrRun: glThread not init")
            return
        }
        if (Thread.currentThread() == glThread) {
            job.invoke()
        } else {
            glHandler.post(job)
        }
    }
}