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
import com.guanshu.media.opengl.filters.SingleImageTextureFilter
import com.guanshu.media.opengl.filters.SmartTextureFilter
import com.guanshu.media.opengl.filters.TextureWithImageFilter
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newFbo
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.opengl.readToBitmap
import com.guanshu.media.opengl.unbindFbo
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger

private const val TAG = "OpenglSurfaceView"

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
//    private val mainFilter = SmartTextureFilter()

    // offscreen rendering from playback output to fbo
    private var secondOpenglEnv: OpenglEnv? = null
    private val secondFilter = TextureWithImageFilter()
    private var fbo: Int = -1
    private var fboTexture: Int = -1
    private var fboTextureData: TextureData? = null
    private var testBitmap: Bitmap? = null

    // For decoding output, built from second opengl ev
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureData: TextureData? = null

    // TODO lock between main/second opengl env

    // display surface
    @Volatile
    private var surfaceHolder: SurfaceHolder? = null

    private var error = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
        mainOpenglEnv = OpenglEnv()
        secondOpenglEnv = OpenglEnv()

        mainOpenglEnv!!.init {
            Logger.d(TAG, "init main context done")
            mainFilter.init()

            val context = mainOpenglEnv!!.getEglContext()
            secondOpenglEnv!!.init(context) {
                Logger.d(TAG, "init second context done")
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

        secondOpenglEnv?.requestSurface(1) {
            val textureId = it.first().first
            val st = it.first().second

            st.setOnFrameAvailableListener { surfaceText ->
                val textData = textureData ?: return@setOnFrameAvailableListener
                if (textData.resolution == DefaultSize) return@setOnFrameAvailableListener

                secondOpenglEnv?.requestRender {
                    textData.matrix.matrixReset()
                    surfaceText.updateTexImage()
                    surfaceText.getTransformMatrix(textData.matrix)

                    maybeBindFbo(textData.resolution)
                    checkGlError("before second filter render")
                    GLES20.glViewport(0, 0, textData.resolution.width, textData.resolution.height)
                    secondFilter.render(listOf(textData), textData.resolution)
                    checkGlError("after second filter render")
                    if (testBitmap == null && false) {
                        testBitmap = readToBitmap(textData.resolution)
                        Logger.d(TAG, "dump test bitmap")
                    }
                    unbindFbo()
                }
            }

            surfaceTexture = st
            surface = Surface(st)
            textureData = TextureData(textureId, newMatrix(), DefaultSize)

            Logger.d(TAG, "request surface done: $textureId, $st, $surface")
            callback(surface!!)
        }
    }

    private fun maybeBindFbo(resolution: Size) {
        if (fbo == -1) fbo = newFbo()
        if (fboTexture == -1) fboTexture = newTexture(
            GLES20.GL_TEXTURE_2D,
            resolution.width,
            resolution.height
        )
        if (fboTextureData == null) fboTextureData =
            TextureData(fboTexture, newMatrix(), resolution, GLES20.GL_TEXTURE_2D)

        bindFbo(fbo, fboTexture)
    }

    fun release() {
        Logger.d(TAG, "release")
        mainOpenglEnv?.release()
        mainOpenglEnv = null
        secondOpenglEnv?.release()
        secondOpenglEnv = null
    }

    private fun maybeScheduleRender() {
        val fps = 30
        val delayMs = 1000L / fps

        mainHandler.postDelayed({

            mainOpenglEnv?.requestRender {
                if (error) return@requestRender
                if (viewResolution == DefaultSize) return@requestRender
                val textData = fboTextureData ?: return@requestRender

                // TODO, the result is black
                try {
//                    val start = System.nanoTime()

                    checkGlError("before main filter render")
                    textData.matrix.matrixReset()
                    GLES20.glViewport(0, 0, viewResolution.width, viewResolution.height)
                    mainFilter.render(
                        listOf(textData),
                        viewResolution,
                    )
                    checkGlError("after main filter render")

//                    GLES20.glFinish()
//                    val cost = System.nanoTime() - start
//                    Logger.v(TAG,"main:cost=$cost, ${cost/1000_000}")

                    mainOpenglEnv?.swapBuffer()
                } catch (e: Exception) {
                    error = true
                    Logger.e(TAG, "draw error", e)
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
        mainOpenglEnv?.releaseEglSurface()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated: $holder")
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
        Logger.d(TAG, "surfaceCreated: $holder")

        surfaceHolder = null
        maybeReleaseEglSurface()
    }
}