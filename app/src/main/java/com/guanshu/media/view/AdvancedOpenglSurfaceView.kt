package com.guanshu.media.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.bindFbo
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.egl.OpenglEnv
import com.guanshu.media.opengl.fence
import com.guanshu.media.opengl.filters.OverlayFilter
import com.guanshu.media.opengl.filters.SingleImageTextureFilter
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newFbo
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.opengl.unbindFbo
import com.guanshu.media.opengl.waitFence
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.util.LinkedList

private const val TAG = "AdvancedOpenglSurfaceView"
private const val FPS = 30
private const val TEXTURE_CACHE_INIT_SIZE = 2

class AdvancedOpenglSurfaceView : SurfaceView, SurfaceHolder.Callback {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0,
    )

    // main open env, render the texture to the surface
    private var mainOpenglEnv: OpenglEnv? = null
    private val mainFilter = SingleImageTextureFilter()

    // offscreen rendering from playback output to fbo
    private var secondOpenglEnv: OpenglEnv? = null
    private val secondFilter = OverlayFilter()
    private var fbo: Int = -1

    // A front/back texture buffer
    // TODO implement it well
    private val fboTextureQueue = LinkedList<Pair<TextureData, Long>>()

    // For decoding output, built from second opengl ev
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureData: TextureData? = null

    // display surface
    @Volatile
    private var surfaceHolder: SurfaceHolder? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var playback = true

    fun setMediaResolution(size: Size) {
        textureData?.resolution = size
    }

    fun getMediaResolution(): Size? {
        return textureData?.resolution
    }

    var viewResolution = DefaultSize
        set(value) {
            Logger.i(TAG, "set view resolution=$value")
            field = value
        }

    init {
        holder.addCallback(this)
    }

    fun init() {
        Logger.d(TAG, "init")
        mainOpenglEnv = OpenglEnv("main")
        secondOpenglEnv = OpenglEnv("second")

        mainOpenglEnv?.initThread()
        secondOpenglEnv?.initThread()

        mainOpenglEnv?.initContext {
            Logger.d(TAG, "init main context done")
            mainFilter.init()
            secondOpenglEnv!!.initContext(mainOpenglEnv!!.getEglContext()) {
                Logger.d(TAG, "init second context done")
                fbo = newFbo()
                secondFilter.init()
            }
            maybeScheduleRender()
        }
    }

    fun requestSurface(callback: (Surface) -> Unit) {
        Logger.d(TAG, "request surface")
        if (surface != null) {
            callback(surface!!)
            return
        }

        secondOpenglEnv?.requestSurface(num = 1) {
            val textureId = it.first().first
            val st = it.first().second

            st.setOnFrameAvailableListener { surfaceText ->
                if (!playback) return@setOnFrameAvailableListener
                val textData = textureData ?: return@setOnFrameAvailableListener
                if (textData.resolution == DefaultSize) return@setOnFrameAvailableListener

                secondOpenglEnv?.requestRender {
                    // second渲染线程，负责把特效&source提前做离屏渲染
                    maybeSetupFboTextureQueue(textData.resolution)

                    textData.matrix.matrixReset()
                    surfaceText.updateTexImage()
                    surfaceText.getTransformMatrix(textData.matrix)
                    val pts = surfaceText.timestamp

                    checkGlError("before second filter render")
                    GLES20.glViewport(
                        0,
                        0,
                        textData.resolution.width,
                        textData.resolution.height
                    )

                    // TODO FIXME
                    val (fboTexture, _) = synchronized(fboTextureQueue) {
                        fboTextureQueue.poll() ?: return@requestRender
                    }

                    secondFilter.onBeforeDraw = {
                        checkGlError("before second filter maybeBindFbo")
                        bindFbo(fbo, fboTexture.textureId)
                        checkGlError("after second filter maybeBindFbo")
                    }
                    secondFilter.render(listOf(textData), textData.resolution)
                    checkGlError("after second filter render")
                    unbindFbo()

                    val glFence = fence()
                    GLES20.glFlush()
                    glFence.waitFence()
                    synchronized(fboTextureQueue) { fboTextureQueue.addLast(Pair(fboTexture, pts)) }
                }
            }

            surfaceTexture = st
            surface = Surface(st)
            textureData = TextureData(textureId, newMatrix(), DefaultSize)

            Logger.d(TAG, "request surface done: textureId=$textureId, $st, $surface")
            callback(surface!!)
        }
    }

    fun stop() {
        playback = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun maybeSetupFboTextureQueue(resolution: Size) {
        if (fboTextureQueue.isEmpty()) {
            synchronized(fboTextureQueue) {
                val textures = IntArray(TEXTURE_CACHE_INIT_SIZE)
                newTexture(
                    textures,
                    GLES20.GL_TEXTURE_2D,
                    resolution.width,
                    resolution.height
                )
                textures.forEach {
                    fboTextureQueue.add(
                        Pair(
                            TextureData(
                                it,
                                newMatrix(),
                                resolution,
                                GLES20.GL_TEXTURE_2D
                            ), -1
                        )
                    )
                }
            }
        }
    }

    fun release() {
        Logger.d(TAG, "release")
        mainOpenglEnv?.release()
        mainOpenglEnv = null
        secondOpenglEnv?.release()
        secondOpenglEnv = null
    }

    // avg: 30ms
    fun readBitmap(callback: (Bitmap?) -> Unit) {
        mainOpenglEnv?.postOrRun {
            Logger.d(TAG, "readBitmap: ${fboTextureQueue.size}")
            val width = viewResolution.width
            val height = viewResolution.height
            if (width <= 0 || height <= 0) {
                Logger.e(TAG, "invalid size:$width*$height")
                callback(null)
                return@postOrRun
            }
            val start = System.currentTimeMillis()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            nativeReadPixel(bitmap, width, height)
            Logger.i(TAG, "readBitmap cost:${System.currentTimeMillis() - start}")
            callback(bitmap)
        }
    }

    external fun nativeReadPixel(bitmap: Bitmap, width: Int, height: Int)

    private fun maybeScheduleRender() {
        val delayMs = 1000L / FPS
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            // 主渲染线程，负责上屏
            mainOpenglEnv?.requestRender {
                if (!playback) return@requestRender
                if (viewResolution == DefaultSize) return@requestRender
                if (surfaceHolder == null) return@requestRender

                val (fboTexture, pts) = synchronized(fboTextureQueue) {
                    fboTextureQueue.removeLastOrNull() ?: return@requestRender
                }
                if (pts == -1L) return@requestRender

                checkGlError("before main filter render")
                fboTexture.matrix.matrixReset()
                GLES20.glViewport(0, 0, viewResolution.width, viewResolution.height)
                mainFilter.render(
                    listOf(fboTexture),
                    viewResolution,
                )
                checkGlError("after main filter render")
                mainOpenglEnv?.swapBuffer()

                val glFence = fence()
                GLES20.glFlush()
                glFence.waitFence()

                // TODO should be elegant to insert into the correct position
                synchronized(fboTextureQueue) {
                    if (fboTextureQueue.isEmpty()) {
                        fboTextureQueue.addLast(Pair(fboTexture, pts))
                    } else {
                        val (a, b) = fboTextureQueue.first
                        if (pts > b)
                            fboTextureQueue.addLast(Pair(fboTexture, pts))
                        else
                            fboTextureQueue.addFirst(Pair(fboTexture, pts))
                    }
                }
            }
            maybeScheduleRender()

        }, delayMs)
    }

    private fun maybeInitEglSurface() {
        Logger.d(TAG, "maybeInitEglSurface: ${surfaceHolder?.surface}")
        val surface = surfaceHolder?.surface ?: return
        mainOpenglEnv?.initEglSurface(surface)
    }

    private fun maybeReleaseEglSurface() {
        Logger.d(TAG, "maybeReleaseEglSurface")
        mainHandler.removeCallbacksAndMessages(null)
        mainOpenglEnv?.releaseEglSurface()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")
        maybeScheduleRender()
        if (holder == surfaceHolder) {
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
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceDestroyed: $holder")

        surfaceHolder = null
        maybeReleaseEglSurface()
    }
}