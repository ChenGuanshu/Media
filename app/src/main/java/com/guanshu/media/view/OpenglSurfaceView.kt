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
import com.guanshu.media.opengl.Renderer
import com.guanshu.media.opengl.RendererFactory
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.egl.EglManager
import com.guanshu.media.opengl.egl.EglManagerInterface
import com.guanshu.media.opengl.egl.EglManagerNative
import com.guanshu.media.opengl.filters.DefaultRenderGraph
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OpenglSurfaceView"

class OpenglSurfaceView : SurfaceView, SurfaceHolder.Callback {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0,
    )

    private val egl: EglManagerInterface = EglManagerNative()
//    private val egl: EglManagerInterface = EglManager()

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    @Volatile
    private var surfaceHolder: SurfaceHolder? = null

    private lateinit var textureIds: IntArray
    private val surfaceTextures = arrayListOf<SurfaceTexture>()
    private val surfaces = arrayListOf<Surface>()
    private val textureDatas = arrayListOf<TextureData>()
    private val frameAvailables = hashMapOf<SurfaceTexture, AtomicBoolean>()
    private lateinit var textureRender: Renderer

    private var error = false

    var renderGraph = DefaultRenderGraph

    var renderingMode: RenderingMode = RenderingMode.RenderWhenDirty
        set(value) {
            maybeScheduleRender()
            Logger.d(TAG, "setRenderingMode:$value")
            field = value
        }

    fun setMediaResolution(index: Int, size: Size) {
        textureDatas[index].resolution = size
    }

    fun getMediaResolution(index: Int): Size? {
        return textureDatas.getOrNull(index)?.resolution
    }

    var onSurfaceCreate: ((List<Surface>) -> Unit)? = null
        set(value) {
            value?.invoke(surfaces)
            field = value
        }

    var viewResolution = DefaultSize
        set(value) {
            Logger.i(TAG, "set view resolution=$value")
            field = value
        }

    var onDisplaySurfaceCreate: (() -> Unit)? = null
        set(value) {
            if (surfaceHolder != null) {
                value?.invoke()
            }
            field = value
        }

    init {
        holder.addCallback(this)
    }

    fun init(oesTextureNum: Int = 1) {
        Logger.d(TAG, "init")
        val glThread = HandlerThread(TAG)
        glThread.start()
        this.glThread = glThread

        glHandler = Handler(glThread.looper)
        glHandler?.post {
            Logger.d(TAG, "init run ${Thread.currentThread()}")
            egl.init()
            maybeInitEglSurface()

            textureIds = IntArray(oesTextureNum)
            newTexture(textureIds)

            textureIds.forEach {
                val surfaceTexture = SurfaceTexture(it)
                frameAvailables[surfaceTexture] = AtomicBoolean(false)
                surfaceTexture.setOnFrameAvailableListener {
                    frameAvailables[surfaceTexture]?.set(true)
                    surfaceTexture.onFrameAvailable()
                }
                surfaceTextures.add(surfaceTexture)
                surfaces.add(Surface(surfaceTexture))
                textureDatas.add(TextureData(it, FloatArray(16), DefaultSize))
            }

            onSurfaceCreate?.invoke(surfaces)

            textureRender = RendererFactory.createRenderer(renderGraph)
            textureRender.init()

            maybeScheduleRender()
        }
    }

    fun release() {
        Logger.d(TAG, "release")
        if (glThread == null) {
            Logger.w(TAG, "glThread is not init")
            return
        }
        postOrRun {
            Logger.d(TAG, "release run")
            maybeReleaseEglSurface()
            egl.release()
        }
        glThread?.quitSafely()

        glThread = null
        glHandler = null
    }

    private fun SurfaceTexture.onFrameAvailable() {
        when (renderingMode) {
            RenderingMode.RenderWhenDirty -> requestRender()
            is RenderingMode.RenderFixedRate -> requestUpdate(this)
        }
    }

    private fun maybeScheduleRender() {
        val fps = (renderingMode as? RenderingMode.RenderFixedRate)?.fps ?: return
        val delayMs = 1000L / fps

        glHandler?.removeCallbacksAndMessages(null)
        glHandler?.postDelayed({ requestRender() }, delayMs)
    }

    private fun requestUpdate(surfaceTexture: SurfaceTexture) {
        postOrRun {
            val frameAvail = frameAvailables[surfaceTexture]!!
            val textureData = textureDatas[surfaceTextures.indexOf(surfaceTexture)]
            if (frameAvail.compareAndSet(true, false)) {
                surfaceTexture.updateTexImage()
                Matrix.setIdentityM(textureData.matrix, 0)
                surfaceTexture.getTransformMatrix(textureData.matrix)
            }
        }
    }

    private fun requestRender() {
        postOrRun {
            frameAvailables.keys.forEach { requestUpdate(it) }

            if (!textureRender.init) {
                Logger.w(TAG, "render: textureRenderer not init")
                return@postOrRun
            }

            // render dirty or not
            if (error) {
                return@postOrRun
            }

            try {
                textureRender.drawFrame(
                    textureDatas,
                    viewResolution,
                )
                egl.swapBuffer()
            } catch (e: Exception) {
                error = true
                Logger.e(TAG, "draw error", e)
            }

            maybeScheduleRender()
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
        glHandler?.removeCallbacksAndMessages(null)
        postOrRun {
            Logger.d(TAG, "maybeReleaseEglSurface run")
            egl.makeUnEglCurrent()
            egl.releaseEglSurface()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")
        if (holder == surfaceHolder) {
            maybeScheduleRender()
            return
        }

        surfaceHolder = holder
        onDisplaySurfaceCreate?.invoke()
        maybeInitEglSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG, "surfaceChanged: $holder, $format, $width*$height")

        surfaceHolder = holder
        viewResolution = Size(width, height)
        surfaceTextures.forEach { it.setDefaultBufferSize(width, height) }
        postOrRun { GLES20.glViewport(0, 0, width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")

        surfaceHolder = null
        maybeReleaseEglSurface()
    }

    private fun postOrRun(job: () -> Unit) {
        if (glThread == null) {
            Logger.w(TAG, "postOrRun: glThread not init")
            return
        }
        if (Thread.currentThread() == glThread) {
            job.invoke()
        } else {
            glHandler?.post(job)
        }
    }
}